package tech.illusion.space24.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleBankTest {

    private companion object {
        // build() is the expensive part; share one instance across every assertion in this class.
        val bank: PuzzleBank by lazy { PuzzleBank.build() }
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    @Test
    fun `every tier has a non-empty bucket`() {
        for (difficulty in Difficulty.values()) {
            assertTrue("$difficulty bucket is empty", bank.puzzlesFor(difficulty).isNotEmpty())
        }
    }

    @Test
    fun `card values stay inside each tier's range`() {
        for (difficulty in Difficulty.values()) {
            for (puzzle in bank.puzzlesFor(difficulty)) {
                assertEquals(4, puzzle.cards.size)
                assertTrue(
                    "$difficulty puzzle ${puzzle.cards} out of range",
                    puzzle.cards.all { it in 1..difficulty.maxCardValue },
                )
            }
        }
    }

    @Test
    fun `every banked puzzle is solvable and carries a replayable solution`() {
        for (difficulty in Difficulty.values()) {
            for (puzzle in bank.puzzlesFor(difficulty)) {
                assertTrue("${puzzle.cards} banked with pathCount 0", puzzle.pathCount > 0)
                assertEquals(3, puzzle.solutionSteps.size)
                val pool = puzzle.cards.toMutableList()
                for (step in puzzle.solutionSteps) {
                    assertTrue(pool.remove(step.left))
                    assertTrue(pool.remove(step.right))
                    assertEquals(step.result, step.op.apply(step.left, step.right))
                    pool.add(step.result)
                }
                assertEquals(listOf(24), pool)
            }
        }
    }

    @Test
    fun `known fraction-only hands are excluded from every tier`() {
        val excluded = listOf(listOf(3, 3, 8, 8), listOf(1, 5, 5, 5))
        for (difficulty in Difficulty.values()) {
            val cards = bank.puzzlesFor(difficulty).map { it.cards }
            for (hand in excluded) assertTrue("$hand leaked into $difficulty", hand !in cards)
        }
    }

    @Test
    fun `easy hands are measurably easier to solve than hard hands`() {
        val easy = median(bank.puzzlesFor(Difficulty.EASY).map { it.pathCount })
        val normal = median(bank.puzzlesFor(Difficulty.NORMAL).map { it.pathCount })
        val hard = median(bank.puzzlesFor(Difficulty.HARD).map { it.pathCount })
        assertTrue("easy=$easy should exceed normal=$normal", easy > normal)
        assertTrue("normal=$normal should exceed hard=$hard", normal > hard)
    }

    @Test
    fun `percentile picks the value at the given fraction of the sorted distribution`() {
        val values = listOf(50, 10, 30, 20, 40)
        assertEquals(10, PuzzleBank.percentile(values, 0.0))
        assertEquals(30, PuzzleBank.percentile(values, 0.5))
        assertEquals(50, PuzzleBank.percentile(values, 1.0))
    }

    @Test
    fun `building the whole bank stays inside the startup budget`() {
        val startedAt = System.nanoTime()
        PuzzleBank.build()
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("bank build took ${elapsedMillis}ms, budget is 1500ms", elapsedMillis < 1500)
    }
}
