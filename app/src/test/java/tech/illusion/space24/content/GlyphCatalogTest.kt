package tech.illusion.space24.content

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.illusion.space24.game.Op

class GlyphCatalogTest {

    @Test
    fun `positive value decomposes digit by digit`() {
        assertEquals(listOf(Glyph.D7), GlyphCatalog.decompose("7"))
        assertEquals(listOf(Glyph.D2, Glyph.D4), GlyphCatalog.decompose("24"))
    }

    /**
     * 负号复用运算符的减号字模，不单独出资产。牌面理论极值 −28561 是 6 字符。
     * Int.toString() 产生的是 ASCII 连字符 '-'，不是 U+2212，两者都要认。
     */
    @Test
    fun `negative value uses the operator minus glyph`() {
        val expected = listOf(Glyph.MINUS, Glyph.D2, Glyph.D8, Glyph.D5, Glyph.D6, Glyph.D1)
        assertEquals(expected, GlyphCatalog.decompose("-28561"))
        assertEquals(expected, GlyphCatalog.decompose("−28561"))
    }

    @Test
    fun `every operator maps to a glyph`() {
        for (op in Op.values()) {
            // 不抛异常即可；具体映射由下一个测试锁定
            GlyphCatalog.glyphFor(op)
        }
        assertEquals(Glyph.MINUS, GlyphCatalog.glyphFor(Op.values().first { it.symbol == "−" }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unmappable character is rejected loudly`() {
        GlyphCatalog.decompose("7a")
    }

    /**
     * 逐位锁定字符→字模映射表，覆盖全部 10 个数字。手写映射表里一次错位（比如 '3' 误接
     * 到 D9）不会抛异常、也不会让上面几个测试变红，只有逐位比对才能抓出来。
     */
    @Test
    fun `every digit character maps to its own glyph`() {
        ('0'..'9').forEachIndexed { i, c ->
            assertEquals(Glyph.values()[i], GlyphCatalog.decompose(c.toString()).single())
        }
    }

    /** 字高归一化：按目标字高求缩放，不按字宽——按宽会把窄的 1 拉胖。 */
    @Test
    fun `scale is derived from cap height not width`() {
        val layout = GlyphCatalog.layout(
            glyphWidths = listOf(20f, 60f),
            capHeight = 100f,
            targetHeight = 50f,
            maxWidth = 1000f,
            tracking = 4f,
        )
        assertEquals(0.5f, layout.scale, 1e-4f)
    }

    /** 横向按各字实际宽度依次排布，整串关于原点居中。 */
    @Test
    fun `glyphs are laid out proportionally and centred on the origin`() {
        val layout = GlyphCatalog.layout(
            glyphWidths = listOf(20f, 60f),
            capHeight = 100f,
            targetHeight = 100f,   // scale = 1
            maxWidth = 1000f,
            tracking = 0f,
        )
        // 总宽 80，整串居中跨 [-40,+40]：
        //   宽 20 的占 [-40,-20]，中心 -30
        //   宽 60 的占 [-20,+40]，中心 +10
        // 两个中心不对称，正是比例字距的体现——等宽排布才会对称。
        assertEquals(-30f, layout.offsetsX[0], 1e-4f)
        assertEquals(10f, layout.offsetsX[1], 1e-4f)
    }

    /** 整串超过卡宽时等比缩小，这替代了旧的"按字符数切三档字号"规则。 */
    @Test
    fun `overlong string is scaled down to fit`() {
        val widths = List(6) { 30f }   // scale=1 时总宽 180
        val layout = GlyphCatalog.layout(
            glyphWidths = widths,
            capHeight = 100f,
            targetHeight = 100f,
            maxWidth = 90f,          // 只有一半放得下
            tracking = 0f,
        )
        assertEquals(0.5f, layout.scale, 1e-4f)
        // 缩放后总宽 90，首字中心 -37.5
        assertEquals(-37.5f, layout.offsetsX[0], 1e-4f)
    }

    @Test
    fun `single glyph sits on the origin`() {
        val layout = GlyphCatalog.layout(
            glyphWidths = listOf(40f),
            capHeight = 100f,
            targetHeight = 100f,
            maxWidth = 1000f,
            tracking = 6f,
        )
        assertEquals(0f, layout.offsetsX[0], 1e-4f)
    }

    /**
     * 超宽 + 非零字距同时出现（`overlong string is scaled down to fit` 只测了 tracking=0）。
     * `fitScale` 必须同时缩小字宽与字距，总宽（含字距）才能精确落在 `maxWidth` 上，而不是
     * 只缩了字宽、字距原样保留导致整串仍然溢出。
     */
    @Test
    fun `overflow with nonzero tracking still lands total width on maxWidth`() {
        val widths = List(3) { 40f } // scale=1、tracking=0 时总宽 120，另加 2×10 字距共 140
        val layout = GlyphCatalog.layout(
            glyphWidths = widths,
            capHeight = 100f,
            targetHeight = 100f,
            maxWidth = 100f,
            tracking = 10f,
        )
        val expectedFitScale = 100f / 140f
        assertEquals(expectedFitScale, layout.scale, 1e-4f)

        val scaledWidths = widths.map { it * layout.scale }
        val totalWidth = (layout.offsetsX.last() + scaledWidths.last() / 2f) -
            (layout.offsetsX.first() - scaledWidths.first() / 2f)
        assertEquals(100f, totalWidth, 1e-3f)
    }
}
