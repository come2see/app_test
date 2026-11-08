package tech.illusion.space24.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpTest {

    @Test
    fun `plus minus times are plain integer arithmetic including negatives`() {
        assertEquals(11, Op.PLUS.apply(3, 8))
        assertEquals(-5, Op.MINUS.apply(3, 8))
        assertEquals(24, Op.TIMES.apply(3, 8))
    }

    @Test
    fun `division only succeeds when it divides exactly`() {
        assertEquals(4, Op.DIV.apply(8, 2))
        assertNull(Op.DIV.apply(8, 3))
        assertNull(Op.DIV.apply(1, 5))
    }

    @Test
    fun `division by zero is illegal`() {
        assertNull(Op.DIV.apply(24, 0))
        assertNull(Op.DIV.apply(0, 0))
    }

    @Test
    fun `negative dividends use remainder equals zero not floating point`() {
        assertEquals(-4, Op.DIV.apply(-8, 2))
        assertEquals(-4, Op.DIV.apply(8, -2))
        assertNull(Op.DIV.apply(-8, 3))
    }

    @Test
    fun `commutativity flags match the operators`() {
        assertEquals(true, Op.PLUS.commutative)
        assertEquals(true, Op.TIMES.commutative)
        assertEquals(false, Op.MINUS.commutative)
        assertEquals(false, Op.DIV.commutative)
    }

    @Test
    fun `symbols are the display glyphs used in solution steps`() {
        assertEquals(listOf("+", "−", "×", "÷"), Op.values().map { it.symbol })
    }
}
