package tech.illusion.space24.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {

    /** Replays a step list to prove it consumes exactly the dealt cards and lands on 24. */
    private fun assertStepsReplayTo24(cards: List<Int>, steps: List<MergeStep>) {
        val pool = cards.toMutableList()
        for (step in steps) {
            assertTrue("step operand ${step.left} not on the board $pool", pool.remove(step.left))
            assertTrue("step operand ${step.right} not on the board $pool", pool.remove(step.right))
            assertEquals(step.result, step.op.apply(step.left, step.right))
            pool.add(step.result)
        }
        assertEquals(listOf(24), pool)
    }

    @Test
    fun `commutative solutions are deduped to a single path`() {
        // 4×6 and 6×4 are the same expression; canonical dedup must count them once.
        assertEquals(1, Solver.solve(listOf(4, 6)).pathCount)
    }

    @Test
    fun `a single card equal to the target is a trivial solution with no steps`() {
        val result = Solver.solve(listOf(24))
        assertEquals(1, result.pathCount)
        assertTrue(result.steps.isEmpty())
    }

    @Test
    fun `unsolvable two card hands report no solution`() {
        assertFalse(Solver.solve(listOf(5, 5)).hasSolution)
    }

    @Test
    fun `hands whose only solution needs fractions are unsolvable under the integer rule`() {
        // 8÷(3−8÷3) and 5×(5−1÷5) both require a fractional intermediate.
        assertFalse(Solver.solve(listOf(3, 3, 8, 8)).hasSolution)
        assertFalse(Solver.solve(listOf(1, 5, 5, 5)).hasSolution)
        assertFalse(Solver.solve(listOf(1, 1, 1, 1)).hasSolution)
    }

    @Test
    fun `hands solvable with integer intermediates report a replayable solution`() {
        for (cards in listOf(listOf(4, 4, 10, 10), listOf(1, 1, 1, 8), listOf(6, 6, 6, 6), listOf(7, 3, 8, 2))) {
            val result = Solver.solve(cards)
            assertTrue("$cards should be solvable", result.hasSolution)
            assertEquals(cards.size - 1, result.steps.size)
            assertStepsReplayTo24(cards, result.steps)
        }
    }

    @Test
    fun `card order does not change the verdict or the path count`() {
        val sorted = Solver.solve(listOf(2, 3, 7, 8))
        val shuffled = Solver.solve(listOf(8, 2, 7, 3))
        assertEquals(sorted.pathCount, shuffled.pathCount)
    }

    @Test
    fun `merge steps display as readable equations`() {
        assertEquals("3 + 5 = 8", MergeStep(3, Op.PLUS, 5, 8).display())
        assertEquals("8 × 3 = 24", MergeStep(8, Op.TIMES, 3, 24).display())
        assertEquals("3 − 8 = -5", MergeStep(3, Op.MINUS, 8, -5).display())
    }
}
