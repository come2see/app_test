package tech.illusion.space24.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    /** A one-hand bank per tier: deterministic boards, no full PuzzleBank.build() cost. */
    private fun singleHandBank(cards: List<Int> = listOf(2, 3, 7, 8)): PuzzleBank {
        val solved = Solver.solve(cards)
        val puzzle = Puzzle(cards = cards, pathCount = solved.pathCount, solutionSteps = solved.steps)
        return PuzzleBank(Difficulty.values().associateWith { listOf(puzzle) })
    }

    private fun session(cards: List<Int> = listOf(2, 3, 7, 8), now: () -> Long = { 0L }) =
        GameSession(bank = singleHandBank(cards), nowMillis = now, random = Random(3))

    private fun GameSession.cardWithValue(value: Int): CardOnBoard =
        cards.first { it.value == value }

    /** Merges two board cards by value: card A, then the operator, then card B. */
    private fun GameSession.merge(left: Int, op: Op, right: Int) {
        onCardTap(cardWithValue(left).id)
        onOpTap(op)
        onCardTap(cards.first { it.value == right && it.id != selectedCardId }.id)
    }

    @Test
    fun `starting a game deals four cards and a full clock`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        assertEquals(GamePhase.PLAYING, session.phase)
        assertEquals(Difficulty.NORMAL, session.currentDifficulty)
        assertEquals(listOf(2, 3, 7, 8), session.cards.map { it.value })
        assertEquals(GameSession.ROUND_MILLIS, session.remainingMillis)
        assertEquals(0, session.score)
        assertEquals(4, session.cards.map { it.id }.distinct().size)
    }

    @Test
    fun `tapping a card selects it and tapping it again clears the selection`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        val id = session.cardWithValue(7).id
        session.onCardTap(id)
        assertEquals(id, session.selectedCardId)
        session.onCardTap(id)
        assertNull(session.selectedCardId)
    }

    @Test
    fun `tapping another card before choosing an operator moves the selection`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        session.onCardTap(session.cardWithValue(7).id)
        val other = session.cardWithValue(3).id
        session.onCardTap(other)
        assertEquals(other, session.selectedCardId)
        assertNull(session.selectedOp)
    }

    @Test
    fun `an operator cannot be chosen before a card`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        session.onOpTap(Op.TIMES)
        assertNull(session.selectedOp)
    }

    @Test
    fun `a legal merge replaces two cards with one result card`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        session.merge(7, Op.MINUS, 3)
        assertEquals(3, session.cards.size)
        assertTrue(session.cards.any { it.value == 4 })
        assertNull(session.selectedCardId)
        assertNull(session.selectedOp)
        assertEquals(FeedbackKind.NONE, session.feedback)
    }

    @Test
    fun `an inexact division produces no card and keeps the selection and operator`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        val firstId = session.cardWithValue(7).id
        session.onCardTap(firstId)
        session.onOpTap(Op.DIV)
        session.onCardTap(session.cardWithValue(3).id)
        assertEquals(FeedbackKind.NOT_DIVISIBLE, session.feedback)
        assertEquals(4, session.cards.size)
        assertEquals(firstId, session.selectedCardId)
        assertEquals(Op.DIV, session.selectedOp)
    }

    @Test
    fun `merging down to a non-24 single card reports it without ending the round`() {
        val session = session(cards = listOf(1, 1, 1, 8))
        session.startNewGame(Difficulty.NORMAL)
        session.merge(1, Op.PLUS, 1)   // -> 2, 1, 8
        session.merge(2, Op.PLUS, 1)   // -> 3, 8
        session.merge(3, Op.PLUS, 8)   // -> 11
        assertEquals(1, session.cards.size)
        assertEquals(FeedbackKind.NOT_TWENTY_FOUR, session.feedback)
        assertEquals(GamePhase.PLAYING, session.phase)
        assertEquals(0, session.score)
    }

    @Test
    fun `reaching 24 scores a point and deals a fresh hand`() {
        val session = session(cards = listOf(1, 1, 1, 8))
        session.startNewGame(Difficulty.NORMAL)
        session.merge(1, Op.PLUS, 1)
        session.merge(2, Op.PLUS, 1)
        session.merge(3, Op.TIMES, 8)
        assertEquals(1, session.score)
        assertEquals(FeedbackKind.CORRECT, session.feedback)
        assertEquals(4, session.cards.size)
        assertEquals(listOf(1, 1, 1, 8), session.cards.map { it.value })
        assertTrue(session.missedPuzzles.isEmpty())
    }

    @Test
    fun `undo steps back one merge and clears the selection`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        session.merge(7, Op.MINUS, 3)
        session.onUndo()
        assertEquals(listOf(2, 3, 7, 8), session.cards.map { it.value }.sorted())
        assertNull(session.selectedCardId)
        assertEquals(FeedbackKind.NONE, session.feedback)
    }

    @Test
    fun `undo on an untouched hand does nothing`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        session.onUndo()
        assertEquals(4, session.cards.size)
        assertEquals(GamePhase.PLAYING, session.phase)
    }

    @Test
    fun `reset restores the dealt hand and drops the undo history`() {
        val session = session()
        session.startNewGame(Difficulty.NORMAL)
        session.merge(7, Op.MINUS, 3)
        session.merge(4, Op.TIMES, 2)
        session.onResetPuzzle()
        assertEquals(listOf(2, 3, 7, 8), session.cards.map { it.value }.sorted())
        session.onUndo()
        assertEquals(listOf(2, 3, 7, 8), session.cards.map { it.value }.sorted())
    }

    @Test
    fun `time reaching zero ends the round and books the unfinished hand as missed`() {
        var now = 0L
        val session = session(now = { now })
        session.startNewGame(Difficulty.NORMAL)
        now = GameSession.ROUND_MILLIS
        session.onTick()
        assertEquals(GamePhase.RESULT, session.phase)
        assertEquals(0L, session.remainingMillis)
        assertEquals(listOf(listOf(2, 3, 7, 8)), session.missedPuzzles.map { it.cards })
    }

    @Test
    fun `the board is inert once the round is over`() {
        var now = 0L
        val session = session(now = { now })
        session.startNewGame(Difficulty.NORMAL)
        now = GameSession.ROUND_MILLIS
        session.onTick()
        val before = session.cards
        session.onCardTap(before.first().id)
        session.onOpTap(Op.PLUS)
        session.onUndo()
        session.onResetPuzzle()
        assertEquals(before, session.cards)
        assertNull(session.selectedCardId)
        assertEquals(1, session.missedPuzzles.size)
    }

    @Test
    fun `a correct answer landing exactly at zero is not credited`() {
        var now = 0L
        val session = session(cards = listOf(1, 1, 1, 8), now = { now })
        session.startNewGame(Difficulty.NORMAL)
        session.merge(1, Op.PLUS, 1)
        session.merge(2, Op.PLUS, 1)
        now = GameSession.ROUND_MILLIS      // clock expires before the final merge is judged
        session.merge(3, Op.TIMES, 8)
        assertEquals(0, session.score)
        assertEquals(GamePhase.RESULT, session.phase)
    }

    @Test
    fun `returning to the start page leaves no residue from the round`() {
        val session = session()
        session.startNewGame(Difficulty.HARD)
        session.merge(7, Op.MINUS, 3)
        session.returnToStart()
        assertEquals(GamePhase.NOT_STARTED, session.phase)
        assertTrue(session.cards.isEmpty())
        assertTrue(session.missedPuzzles.isEmpty())
        assertEquals(0, session.score)
        assertNull(session.selectedCardId)
        assertFalse(session.hintUsed)
        assertEquals(GameSession.ROUND_MILLIS, session.remainingMillis)
    }

    /** Returns [now] for a set number of reads, then [expired] forever after. */
    private class ArmedClock : () -> Long {
        var now = 0L
        var expired = GameSession.ROUND_MILLIS
        private var liveReadsLeft = Int.MAX_VALUE

        /** The next [count] reads return [now]; every read after that returns [expired]. */
        fun expireAfterReads(count: Int) {
            liveReadsLeft = count
        }

        override fun invoke(): Long = if (liveReadsLeft-- > 0) now else expired
    }

    @Test
    fun `boundary rule 3 alone blocks the point when the clock dies inside the winning tap`() {
        val clock = ArmedClock()
        val session = GameSession(singleHandBank(listOf(1, 1, 1, 8)), clock, Random(3))
        session.startNewGame(Difficulty.NORMAL)
        session.merge(1, Op.PLUS, 1)
        session.merge(2, Op.PLUS, 1)
        val three = session.cards.first { it.value == 3 }
        val eight = session.cards.first { it.value == 8 }
        session.onCardTap(three.id)
        session.onOpTap(Op.TIMES)
        // Exactly one live read left: onCardTap's entry guard passes and the merge to 24 completes.
        // The clock is dead by the time onPuzzleSolved checks it, so only rule 3 can block the point.
        clock.expireAfterReads(1)
        session.onCardTap(eight.id)
        assertEquals(0, session.score)
        assertEquals(GamePhase.RESULT, session.phase)
        assertEquals(1, session.missedPuzzles.size)
    }
}
