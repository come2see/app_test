package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.illusion.space24.game.Difficulty

class FormattingTest {

    @Test
    fun `a fresh round reads as one thirty`() {
        assertEquals("1:30", formatRemainingMillis(90_000L))
    }

    @Test
    fun `sub-second remainders round up so the clock only shows zero when it is spent`() {
        assertEquals("0:01", formatRemainingMillis(1L))
        assertEquals("0:01", formatRemainingMillis(1_000L))
        assertEquals("0:02", formatRemainingMillis(1_001L))
        assertEquals("0:00", formatRemainingMillis(0L))
    }

    @Test
    fun `seconds are zero padded and minutes are not`() {
        assertEquals("1:01", formatRemainingMillis(61_000L))
        assertEquals("0:09", formatRemainingMillis(9_000L))
    }

    @Test
    fun `a negative clock is clamped instead of printing a negative time`() {
        assertEquals("0:00", formatRemainingMillis(-5_000L))
    }

    @Test
    fun `difficulty labels are the three Chinese tiers`() {
        assertEquals("简单", Difficulty.EASY.label())
        assertEquals("普通", Difficulty.NORMAL.label())
        assertEquals("困难", Difficulty.HARD.label())
    }

    @Test
    fun `a tier with no record shows the empty-state dashes, not a zero`() {
        assertEquals("--", formatBestScore(null))
    }

    @Test
    fun `an existing record shows its number`() {
        assertEquals("0", formatBestScore(0))
        assertEquals("9", formatBestScore(9))
    }
}
