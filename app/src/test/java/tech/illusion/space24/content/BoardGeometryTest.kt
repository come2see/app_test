package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardGeometryTest {

    /** 4 张卡：总宽 4*104 + 3*16 = 464，左边距 (900-464)/2 = 218，首张中心 218+52 = 270，步长 120。 */
    @Test
    fun `four cards are centred with 16dp gaps`() {
        assertEquals(270f, BoardGeometry.cardCenterXDp(0, 4), 0.01f)
        assertEquals(390f, BoardGeometry.cardCenterXDp(1, 4), 0.01f)
        assertEquals(510f, BoardGeometry.cardCenterXDp(2, 4), 0.01f)
        assertEquals(630f, BoardGeometry.cardCenterXDp(3, 4), 0.01f)
    }

    /** 牌数递减时整行重新居中，不是留着原位的空洞。 */
    @Test
    fun `row recentres as cards are consumed`() {
        // 1 张：正中
        assertEquals(450f, BoardGeometry.cardCenterXDp(0, 1), 0.01f)
        // 2 张：总宽 2*104+16 = 224，左边距 338，中心 390 与 510
        assertEquals(390f, BoardGeometry.cardCenterXDp(0, 2), 0.01f)
        assertEquals(510f, BoardGeometry.cardCenterXDp(1, 2), 0.01f)
        // 3 张：总宽 3*104+2*16 = 344，左边距 278，中心 330 / 450 / 570
        assertEquals(330f, BoardGeometry.cardCenterXDp(0, 3), 0.01f)
        assertEquals(450f, BoardGeometry.cardCenterXDp(1, 3), 0.01f)
        assertEquals(570f, BoardGeometry.cardCenterXDp(2, 3), 0.01f)
    }

    /** 每一种牌数下整行都关于窗口中线对称。 */
    @Test
    fun `row is symmetric about the window centre for every card count`() {
        for (count in 1..BoardGeometry.MAX_CARDS) {
            val first = BoardGeometry.cardCenterXDp(0, count)
            val last = BoardGeometry.cardCenterXDp(count - 1, count)
            assertEquals(BoardGeometry.WINDOW_WIDTH_DP, first + last, 0.01f)
        }
    }

    /** 4 个运算符：总宽 4*56 + 3*16 = 272，左边距 314，首个中心 342，步长 72。 */
    @Test
    fun `operators are centred with 16dp gaps`() {
        assertEquals(342f, BoardGeometry.opCenterXDp(0), 0.01f)
        assertEquals(414f, BoardGeometry.opCenterXDp(1), 0.01f)
        assertEquals(486f, BoardGeometry.opCenterXDp(2), 0.01f)
        assertEquals(558f, BoardGeometry.opCenterXDp(3), 0.01f)
    }

    /** 越界索引直接抛，不静默返回一个看似合理的坐标。 */
    @Test(expected = IllegalArgumentException::class)
    fun `card index beyond count is rejected`() {
        BoardGeometry.cardCenterXDp(2, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `card count beyond max is rejected`() {
        BoardGeometry.cardCenterXDp(0, 5)
    }
}
