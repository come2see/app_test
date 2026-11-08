package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.illusion.space24.game.MergeStep
import tech.illusion.space24.game.Op
import tech.illusion.space24.game.Puzzle

class ResultPanelTest {

    @Test
    fun `a missed hand lists its cards then its steps as equations`() {
        val puzzle = Puzzle(
            cards = listOf(1, 1, 1, 8),
            pathCount = 3,
            solutionSteps = listOf(
                MergeStep(1, Op.PLUS, 1, 2),
                MergeStep(2, Op.PLUS, 1, 3),
                MergeStep(3, Op.TIMES, 8, 24),
            ),
        )
        assertEquals("1 1 1 8 → 1 + 1 = 2，2 + 1 = 3，3 × 8 = 24", missedHandLine(puzzle))
    }

    @Test
    fun `a hand with no recorded steps still shows its cards`() {
        val puzzle = Puzzle(cards = listOf(7, 3, 8, 2), pathCount = 1, solutionSteps = emptyList())
        assertEquals("7 3 8 2", missedHandLine(puzzle))
    }
}
