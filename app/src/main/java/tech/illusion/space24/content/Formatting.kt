package tech.illusion.space24.content

import tech.illusion.space24.game.Difficulty

/** Countdown formatting: seconds round up, so 90_000ms reads "1:30" and only a spent clock reads "0:00". */
fun formatRemainingMillis(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> "简单"
    Difficulty.NORMAL -> "普通"
    Difficulty.HARD -> "困难"
}

/** Contract §4: a tier with no record yet reads "--", never "0". */
fun formatBestScore(bestScore: Int?): String = bestScore?.toString() ?: "--"
