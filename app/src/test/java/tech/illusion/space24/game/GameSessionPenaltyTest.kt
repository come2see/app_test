package tech.illusion.space24.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionPenaltyTest {

    private fun bankOf(cards: List<Int>): PuzzleBank {
        val solved = Solver.solve(cards)
        val puzzle = Puzzle(cards = cards, pathCount = solved.pathCount, solutionSteps = solved.steps)
        return PuzzleBank(Difficulty.values().associateWith { listOf(puzzle) })
    }

    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    private fun GameSession.cardWithValue(value: Int): CardOnBoard = cards.first { it.value == value }

    private fun GameSession.merge(left: Int, op: Op, right: Int) {
        onCardTap(cardWithValue(left).id)
        onOpTap(op)
        onCardTap(cards.first { it.value == right && it.id != selectedCardId }.id)
    }

    @Test
    fun `skip costs ten seconds, books the hand as missed and deals again`() {
        val session = GameSession(bankOf(listOf(2, 3, 7, 8)), FakeClock(), Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.onSkip()
        assertEquals(GameSession.ROUND_MILLIS - GameSession.PENALTY_MILLIS, session.remainingMillis)
        assertEquals(listOf(listOf(2, 3, 7, 8)), session.missedPuzzles.map { it.cards })
        assertEquals(4, session.cards.size)
        assertEquals(GamePhase.PLAYING, session.phase)
        assertEquals(0, session.score)
    }

    @Test
    fun `a skip that would spend the clock ends the round and does not deal`() {
        val clock = FakeClock()
        val session = GameSession(bankOf(listOf(2, 3, 7, 8)), clock, Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.merge(7, Op.MINUS, 3)                                   // board is now 3 cards
        clock.now = GameSession.ROUND_MILLIS - GameSession.PENALTY_MILLIS  // exactly 10s left
        val boardBefore = session.cards
        session.onSkip()
        assertEquals(GamePhase.RESULT, session.phase)
        assertEquals(0L, session.remainingMillis)
        assertEquals(boardBefore, session.cards)                        // the skip effect did not happen
        assertEquals(1, session.missedPuzzles.size)                     // booked exactly once
    }

    @Test
    fun `hint costs ten seconds and highlights the two cards of the next merge`() {
        val session = GameSession(bankOf(listOf(1, 1, 1, 8)), FakeClock(), Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.onHint()
        assertEquals(GameSession.ROUND_MILLIS - GameSession.PENALTY_MILLIS, session.remainingMillis)
        assertEquals(2, session.hintedCardIds.size)
        assertTrue(session.hintedCardIds.all { id -> session.cards.any { it.id == id } })
        assertTrue(session.hintUsed)
    }

    @Test
    fun `hint is limited to once per hand and is not refunded by a reset`() {
        val session = GameSession(bankOf(listOf(1, 1, 1, 8)), FakeClock(), Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.onHint()
        val afterFirstHint = session.remainingMillis
        session.onHint()
        assertEquals(afterFirstHint, session.remainingMillis)
        session.onResetPuzzle()
        assertTrue("reset must not refund the hint", session.hintUsed)
        session.onHint()
        assertEquals(afterFirstHint, session.remainingMillis)
    }

    @Test
    fun `a new hand refunds the hint`() {
        val session = GameSession(bankOf(listOf(1, 1, 1, 8)), FakeClock(), Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.onHint()
        session.onSkip()
        assertFalse(session.hintUsed)
    }

    @Test
    fun `hint tracks the current board rather than the dealt hand`() {
        val session = GameSession(bankOf(listOf(2, 3, 7, 8)), FakeClock(), Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.merge(7, Op.MINUS, 3)      // board: 4, 2, 8 — solvable as 4×(8−2)
        session.onHint()
        assertEquals(2, session.hintedCardIds.size)
        assertTrue(session.hintedCardIds.all { id -> session.cards.any { it.id == id } })
    }

    @Test
    fun `a dead-end board reports itself and charges nothing`() {
        val session = GameSession(bankOf(listOf(2, 3, 7, 8)), FakeClock(), Random(5))
        session.startNewGame(Difficulty.NORMAL)
        session.merge(7, Op.PLUS, 8)       // board: 15, 2, 3 — no way to 24
        session.merge(15, Op.PLUS, 2)      // board: 17, 3 — definitely dead
        val before = session.remainingMillis
        session.onHint()
        assertEquals(FeedbackKind.HINT_DEAD_END, session.feedback)
        assertEquals(before, session.remainingMillis)
        assertFalse("a dead end must not burn the hint", session.hintUsed)
        assertTrue(session.hintedCardIds.isEmpty())
    }

    @Test
    fun `a hint that would spend the clock ends the round and highlights nothing`() {
        val clock = FakeClock()
        val session = GameSession(bankOf(listOf(1, 1, 1, 8)), clock, Random(5))
        session.startNewGame(Difficulty.NORMAL)
        clock.now = GameSession.ROUND_MILLIS - GameSession.PENALTY_MILLIS + 1  // just under 10s left
        session.onHint()
        assertEquals(GamePhase.RESULT, session.phase)
        assertEquals(0L, session.remainingMillis)
        assertTrue(session.hintedCardIds.isEmpty())
        assertFalse(session.hintUsed)
    }

    @Test
    fun `hint and skip are inert once the round is over`() {
        val clock = FakeClock()
        val session = GameSession(bankOf(listOf(1, 1, 1, 8)), clock, Random(5))
        session.startNewGame(Difficulty.NORMAL)
        clock.now = GameSession.ROUND_MILLIS
        session.onTick()
        val missed = session.missedPuzzles.size
        session.onHint()
        session.onSkip()
        assertEquals(missed, session.missedPuzzles.size)
        assertTrue(session.hintedCardIds.isEmpty())
    }
}
