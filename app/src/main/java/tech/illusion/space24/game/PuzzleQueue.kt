package tech.illusion.space24.game

import kotlin.random.Random

/**
 * Deals puzzles from one difficulty bucket. The bucket is shuffled and drained before reshuffling,
 * which is what structurally guarantees the spec's "no identical hand twice in a round" — bucket
 * sizes are far larger than the number of hands a 90 s round can get through.
 */
class PuzzleQueue(
    private val puzzles: List<Puzzle>,
    private val random: Random = Random.Default,
) {
    init {
        require(puzzles.isNotEmpty()) { "PuzzleQueue needs at least one puzzle" }
    }

    private var remaining: MutableList<Puzzle> = ArrayList()

    fun next(): Puzzle {
        if (remaining.isEmpty()) remaining = puzzles.shuffled(random).toMutableList()
        return remaining.removeAt(remaining.size - 1)
    }
}
