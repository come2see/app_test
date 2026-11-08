package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.illusion.space24.game.FeedbackKind

class GameBoardTest {

    @Test
    fun `one and two digit card faces use the largest text size`() {
        assertEquals(CardTextSize.LARGE, cardTextSizeFor("7"))
        assertEquals(CardTextSize.LARGE, cardTextSizeFor("13"))
        assertEquals(CardTextSize.LARGE, cardTextSizeFor("-5"))
    }

    @Test
    fun `three character faces step down one size`() {
        assertEquals(CardTextSize.MEDIUM, cardTextSizeFor("169"))
        assertEquals(CardTextSize.MEDIUM, cardTextSizeFor("-12"))
    }

    @Test
    fun `long intermediates fall back to the smallest size`() {
        assertEquals(CardTextSize.SMALL, cardTextSizeFor("2197"))
        assertEquals(CardTextSize.SMALL, cardTextSizeFor("28561"))
        assertEquals(CardTextSize.SMALL, cardTextSizeFor("-28561"))
    }

    @Test
    fun `the status line is empty when there is nothing to say`() {
        assertNull(statusTextFor(FeedbackKind.NONE))
    }

    @Test
    fun `every other feedback kind has copy`() {
        assertEquals("不是 24，可撤销或重置", statusTextFor(FeedbackKind.NOT_TWENTY_FOUR))
        assertEquals("除不尽，换个运算或换张牌", statusTextFor(FeedbackKind.NOT_DIVISIBLE))
        assertEquals("答对！+1", statusTextFor(FeedbackKind.CORRECT))
        assertEquals("当前牌面已无解，请撤销或重置", statusTextFor(FeedbackKind.HINT_DEAD_END))
    }
}
