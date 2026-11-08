package tech.illusion.space24.data

import android.content.Context
import tech.illusion.space24.game.Difficulty

/**
 * Three independent best-score records, one per [Difficulty]. A null read means "no record yet",
 * which the UI shows as 0; a scoreless round therefore never claims a record.
 */
object BestScoreStore {

    private const val PREFS_NAME = "space24_progress"

    private fun keyFor(difficulty: Difficulty): String = "best_score_${difficulty.name}"

    fun readBestScore(context: Context, difficulty: Difficulty): Int? {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(keyFor(difficulty), -1)
        return if (stored < 0) null else stored
    }

    /** Returns true when the record was replaced, which is also the "新纪录" signal for the UI. */
    fun writeBestScoreIfBetter(context: Context, difficulty: Difficulty, candidate: Int): Boolean {
        if (!isNewBest(readBestScore(context, difficulty), candidate)) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(keyFor(difficulty), candidate)
            .apply()
        return true
    }

    fun isNewBest(current: Int?, candidate: Int): Boolean = candidate > (current ?: 0)
}
