package tech.illusion.space24.game

/** One dealt hand plus its pre-computed solvability metadata. */
data class Puzzle(val cards: List<Int>, val pathCount: Int, val solutionSteps: List<MergeStep>)

/**
 * The whole puzzle bank, built once (off the main thread) before a round can start — the game never
 * solves a hand on demand mid-round. Every 4-card multiset in 1..13 is solved exactly once and the
 * 1..9 subset is derived by filtering, so `build()` does ~1820 searches, not 1820 + 495.
 */
class PuzzleBank(private val byDifficulty: Map<Difficulty, List<Puzzle>>) {

    fun puzzlesFor(difficulty: Difficulty): List<Puzzle> = byDifficulty[difficulty] ?: emptyList()

    companion object {

        /** Fraction of the distribution above which a hand counts as "easy to pull off". */
        private const val EASY_FRACTION = 0.67

        /** Fraction below which a hand counts as "few ways in", i.e. hard. */
        private const val HARD_FRACTION = 0.33

        fun build(): PuzzleBank {
            val solvable = ArrayList<Puzzle>()
            forEachMultiset(maxValue = Difficulty.HARD.maxCardValue) { cards ->
                val result = Solver.solve(cards)
                if (result.hasSolution) {
                    solvable.add(Puzzle(cards = cards, pathCount = result.pathCount, solutionSteps = result.steps))
                }
            }
            val lowRange = solvable.filter { puzzle ->
                puzzle.cards.all { it <= Difficulty.NORMAL.maxCardValue }
            }
            val easyThreshold = percentile(lowRange.map { it.pathCount }, EASY_FRACTION)
            val hardThreshold = percentile(solvable.map { it.pathCount }, HARD_FRACTION)
            return PuzzleBank(
                mapOf(
                    Difficulty.EASY to lowRange.filter { it.pathCount >= easyThreshold },
                    Difficulty.NORMAL to lowRange,
                    Difficulty.HARD to solvable.filter { it.pathCount <= hardThreshold },
                )
            )
        }

        /** Value at [fraction] of the sorted distribution. Ties keep both buckets non-empty. */
        internal fun percentile(values: List<Int>, fraction: Double): Int {
            require(values.isNotEmpty()) { "percentile of an empty distribution" }
            val sorted = values.sorted()
            return sorted[((sorted.size - 1) * fraction).toInt()]
        }

        /** Non-decreasing 4-tuples = multisets, so `{7,3,8,2}` and `{2,3,7,8}` are enumerated once. */
        private inline fun forEachMultiset(maxValue: Int, action: (List<Int>) -> Unit) {
            for (a in 1..maxValue) {
                for (b in a..maxValue) {
                    for (c in b..maxValue) {
                        for (d in c..maxValue) {
                            action(listOf(a, b, c, d))
                        }
                    }
                }
            }
        }
    }
}
