package tech.illusion.space24.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class GamePhase { NOT_STARTED, PLAYING, RESULT }

/** A card on the board. [id] is stable and unique per card instance, so two 6s never get confused. */
data class CardOnBoard(val id: Int, val value: Int)

enum class FeedbackKind { NONE, NOT_TWENTY_FOUR, NOT_DIVISIBLE, CORRECT, HINT_DEAD_END }

/**
 * Owns every rule of a round. Properties are backed by Compose snapshot state (not plain vars) so
 * Composables reading them register a snapshot dependency and actually recompose — with plain vars
 * the JVM tests still pass while the UI silently freezes.
 *
 * Time is derived, never accumulated: [remainingMillis] is always recomputed from [nowMillis] minus
 * the accrued penalties, so a missed tick can never drift the clock.
 */
@Stable
class GameSession(
    private val bank: PuzzleBank,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {

    companion object {
        const val ROUND_MILLIS = 90_000L
        const val PENALTY_MILLIS = 10_000L
    }

    var phase: GamePhase by mutableStateOf(GamePhase.NOT_STARTED)
        private set
    var currentDifficulty: Difficulty by mutableStateOf(Difficulty.NORMAL)
        private set
    var cards: List<CardOnBoard> by mutableStateOf(emptyList())
        private set
    var selectedCardId: Int? by mutableStateOf(null)
        private set
    var selectedOp: Op? by mutableStateOf(null)
        private set
    var hintedCardIds: Set<Int> by mutableStateOf(emptySet())
        private set
    var hintUsed: Boolean by mutableStateOf(false)
        private set
    var score: Int by mutableStateOf(0)
        private set
    var feedback: FeedbackKind by mutableStateOf(FeedbackKind.NONE)
        private set
    var remainingMillis: Long by mutableStateOf(ROUND_MILLIS)
        private set
    var missedPuzzles: List<Puzzle> by mutableStateOf(emptyList())
        private set

    private var queue: PuzzleQueue? = null
    private var currentPuzzle: Puzzle? = null
    private var roundStartMillis = 0L
    private var penaltyMillis = 0L
    private var nextCardId = 0
    private val undoStack = ArrayDeque<List<CardOnBoard>>()

    fun startNewGame(difficulty: Difficulty) {
        currentDifficulty = difficulty
        queue = PuzzleQueue(bank.puzzlesFor(difficulty), random)
        score = 0
        missedPuzzles = emptyList()
        penaltyMillis = 0L
        roundStartMillis = nowMillis()
        remainingMillis = ROUND_MILLIS
        phase = GamePhase.PLAYING
        loadNextPuzzle()
    }

    fun returnToStart() {
        phase = GamePhase.NOT_STARTED
        queue = null
        currentPuzzle = null
        cards = emptyList()
        missedPuzzles = emptyList()
        score = 0
        penaltyMillis = 0L
        remainingMillis = ROUND_MILLIS
        undoStack.clear()
        hintUsed = false
        hintedCardIds = emptySet()
        feedback = FeedbackKind.NONE
        clearSelection()
    }

    /** Called by the UI ticker. Refreshes the displayed clock and ends the round at zero. */
    fun onTick() {
        if (phase != GamePhase.PLAYING) return
        remainingMillis = computeRemaining()
        if (remainingMillis <= 0L) endRound()
    }

    fun onCardTap(id: Int) {
        if (!timeStillAvailable()) return
        val tapped = cards.firstOrNull { it.id == id } ?: return
        val firstId = selectedCardId
        val op = selectedOp

        if (firstId == null) {
            selectedCardId = id
            feedback = FeedbackKind.NONE
            return
        }
        if (op == null) {
            selectedCardId = if (firstId == id) null else id
            feedback = FeedbackKind.NONE
            return
        }
        if (firstId == id) {
            clearSelection()
            feedback = FeedbackKind.NONE
            return
        }
        val first = cards.firstOrNull { it.id == firstId }
        if (first == null) {
            clearSelection()
            return
        }
        val result = op.apply(first.value, tapped.value)
        if (result == null) {
            // Illegal division: no result card, and the selection + operator survive so the player
            // can just pick a different second card.
            feedback = FeedbackKind.NOT_DIVISIBLE
            return
        }
        undoStack.addLast(cards)
        val merged = CardOnBoard(id = nextCardId++, value = result)
        cards = cards.map { if (it.id == firstId) merged else it }.filter { it.id != tapped.id }
        clearSelection()
        hintedCardIds = emptySet()
        if (cards.size == 1) {
            if (cards[0].value == Solver.TARGET) onPuzzleSolved() else feedback = FeedbackKind.NOT_TWENTY_FOUR
        } else {
            feedback = FeedbackKind.NONE
        }
    }

    fun onOpTap(op: Op) {
        if (!timeStillAvailable()) return
        if (selectedCardId == null) return
        selectedOp = op
        feedback = FeedbackKind.NONE
    }

    fun onUndo() {
        if (!timeStillAvailable()) return
        val previous = undoStack.removeLastOrNull() ?: return
        cards = previous
        clearSelection()
        hintedCardIds = emptySet()
        feedback = FeedbackKind.NONE
    }

    fun onResetPuzzle() {
        if (!timeStillAvailable()) return
        val puzzle = currentPuzzle ?: return
        dealCards(puzzle)
        // hintUsed is intentionally left alone: resetting the hand does not refund the hint.
        feedback = FeedbackKind.NONE
    }

    /**
     * −10 s, books the current hand as unanswered and deals the next one. If the penalty would spend
     * the clock, the penalty still lands but the deal does not: the round ends on the hand in front
     * of the player (boundary rule 2).
     */
    fun onSkip() {
        if (!timeStillAvailable()) return
        val after = remainingMillis - PENALTY_MILLIS
        penaltyMillis += PENALTY_MILLIS
        if (after <= 0L) {
            endRound()
            return
        }
        remainingMillis = after
        currentPuzzle?.let { missedPuzzles = missedPuzzles + it }
        loadNextPuzzle()
    }

    /**
     * −10 s, highlights the two cards the next merge should use — computed from the *current* board,
     * not the dealt hand, so it still helps after a few merges. Once per hand; a reset does not
     * refund it. A board with no solution left charges nothing at all and says so instead.
     */
    fun onHint() {
        if (!timeStillAvailable()) return
        if (hintUsed) return
        val ids = nextMergeCardIds()
        if (ids == null) {
            feedback = FeedbackKind.HINT_DEAD_END
            return
        }
        val after = remainingMillis - PENALTY_MILLIS
        penaltyMillis += PENALTY_MILLIS
        if (after <= 0L) {
            endRound()
            return
        }
        remainingMillis = after
        hintUsed = true
        hintedCardIds = ids
        feedback = FeedbackKind.NONE
    }

    /** Null when the current board can no longer reach 24. */
    private fun nextMergeCardIds(): Set<Int>? {
        if (cards.size < 2) return null
        val step = Solver.solve(cards.map { it.value }).steps.firstOrNull() ?: return null
        val left = cards.firstOrNull { it.value == step.left } ?: return null
        val right = cards.firstOrNull { it.id != left.id && it.value == step.right } ?: return null
        return setOf(left.id, right.id)
    }

    private fun onPuzzleSolved() {
        remainingMillis = computeRemaining()
        if (remainingMillis <= 0L) {
            endRound()
            return
        }
        score += 1
        loadNextPuzzle()
        feedback = FeedbackKind.CORRECT
    }

    private fun loadNextPuzzle() {
        val puzzle = queue?.next() ?: return
        currentPuzzle = puzzle
        dealCards(puzzle)
        hintUsed = false
        feedback = FeedbackKind.NONE
    }

    private fun dealCards(puzzle: Puzzle) {
        cards = puzzle.cards.map { CardOnBoard(id = nextCardId++, value = it) }
        undoStack.clear()
        clearSelection()
        hintedCardIds = emptySet()
    }

    private fun clearSelection() {
        selectedCardId = null
        selectedOp = null
    }

    private fun computeRemaining(): Long =
        (ROUND_MILLIS - (nowMillis() - roundStartMillis) - penaltyMillis).coerceAtLeast(0L)

    /** Boundary rule 1: every action checks the clock first; a spent clock ends the round instead. */
    private fun timeStillAvailable(): Boolean {
        if (phase != GamePhase.PLAYING) return false
        remainingMillis = computeRemaining()
        if (remainingMillis <= 0L) {
            endRound()
            return false
        }
        return true
    }

    /** Idempotent: repeated ticks after the round ends must not book the hand as missed twice. */
    private fun endRound() {
        if (phase == GamePhase.RESULT) return
        currentPuzzle?.let { missedPuzzles = missedPuzzles + it }
        remainingMillis = 0L
        clearSelection()
        hintedCardIds = emptySet()
        feedback = FeedbackKind.NONE
        phase = GamePhase.RESULT
    }
}
