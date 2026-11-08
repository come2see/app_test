package tech.illusion.space24.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleQueueTest {

    private fun puzzle(vararg cards: Int) = Puzzle(cards = cards.toList(), pathCount = 1, solutionSteps = emptyList())

    @Test
    fun `draws every puzzle once before repeating any`() {
        val puzzles = listOf(puzzle(1, 1, 1, 8), puzzle(6, 6, 6, 6), puzzle(4, 4, 10, 10))
        val queue = PuzzleQueue(puzzles, Random(7))
        val drawn = List(puzzles.size) { queue.next() }
        assertEquals(puzzles.size, drawn.distinct().size)
        assertEquals(puzzles.toSet(), drawn.toSet())
    }

    @Test
    fun `reshuffles and keeps dealing once the bucket is exhausted`() {
        val queue = PuzzleQueue(listOf(puzzle(1, 1, 1, 8), puzzle(6, 6, 6, 6)), Random(7))
        val drawn = List(5) { queue.next() }
        assertEquals(5, drawn.size)
        assertTrue(drawn.all { it.cards.size == 4 })
    }

    @Test
    fun `the same seed produces the same deal order`() {
        val puzzles = listOf(puzzle(1, 1, 1, 8), puzzle(6, 6, 6, 6), puzzle(4, 4, 10, 10))
        val first = List(3) { PuzzleQueue(puzzles, Random(11)).next() }
        assertEquals(1, first.distinct().size)
    }

    @Test
    fun `an empty bucket is rejected loudly instead of dealing nothing`() {
        try {
            PuzzleQueue(emptyList(), Random(1))
            throw AssertionError("expected an IllegalArgumentException for an empty bucket")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("puzzle"))
        }
    }
}
