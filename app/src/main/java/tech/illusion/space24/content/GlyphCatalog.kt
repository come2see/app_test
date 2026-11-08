package tech.illusion.space24.content

import tech.illusion.space24.game.Op

/**
 * 一个立体字模资产。[assetName] 是 `app/src/main/assets/glyphs/` 下的文件名（不含扩展名）。
 *
 * 负数的减号复用 [MINUS]，不单独出资产——字形一致，多一个资产只是多一份体积。
 */
enum class Glyph(val assetName: String) {
    D0("digit_0"), D1("digit_1"), D2("digit_2"), D3("digit_3"), D4("digit_4"),
    D5("digit_5"), D6("digit_6"), D7("digit_7"), D8("digit_8"), D9("digit_9"),
    PLUS("op_plus"), MINUS("op_minus"), TIMES("op_times"), DIVIDE("op_divide"),
}

/**
 * 一串字模的排布结果：统一缩放系数，以及各字相对串中心的横向偏移。
 *
 * 偏移与入参**同单位**——这个函数是纯比例运算，不关心单位是 dp 还是米。调用方传米就得米。
 */
data class GlyphLayout(val scale: Float, val offsetsX: List<Float>)

/**
 * 牌面/运算符文本与立体字模之间的映射与排布。纯函数，不碰 Android，可在 JVM 上直接测。
 */
object GlyphCatalog {

    /**
     * 把一段牌面文本拆成字模序列。
     *
     * ASCII 连字符与 U+2212 减号都映射到 [Glyph.MINUS]：`Int.toString()` 给的是前者，
     * 契约文案里用的是后者。认不出的字符直接抛——静默跳过会让卡面少一位数字而毫无提示。
     */
    fun decompose(text: String): List<Glyph> = text.map { ch ->
        when (ch) {
            '0' -> Glyph.D0; '1' -> Glyph.D1; '2' -> Glyph.D2; '3' -> Glyph.D3; '4' -> Glyph.D4
            '5' -> Glyph.D5; '6' -> Glyph.D6; '7' -> Glyph.D7; '8' -> Glyph.D8; '9' -> Glyph.D9
            '-', '−' -> Glyph.MINUS
            else -> throw IllegalArgumentException("无法映射为字模的字符: '$ch' (U+%04X)".format(ch.code))
        }
    }

    /** 运算符到字模。按 [Op.symbol] 匹配，避免依赖枚举声明顺序。 */
    fun glyphFor(op: Op): Glyph = when (op.symbol) {
        "+" -> Glyph.PLUS
        "−", "-" -> Glyph.MINUS
        "×", "*" -> Glyph.TIMES
        "÷", "/" -> Glyph.DIVIDE
        else -> throw IllegalArgumentException("无法映射为字模的运算符: ${op.symbol}")
    }

    /**
     * 算出一串字模的统一缩放与各字横向偏移。
     *
     * @param glyphWidths 各字模在**模型原始单位**下的宽度，由运行时 `getVisualBounds` 量得
     * @param capHeight 字模在模型原始单位下的字高（各字统一，取自授作规范）
     * @param targetHeight 期望的显示字高，**单位由调用方决定**（挂进 3D 场景时是米）
     * @param maxWidth 可用宽度，与 [targetHeight] 同单位；整串超出时在字高缩放之上再等比缩小
     * @param tracking 字距，与 [targetHeight] 同单位
     *
     * 缩放按**字高**而非字宽求：按宽会让窄的 `1` 被拉到和 `8` 一样宽。
     */
    fun layout(
        glyphWidths: List<Float>,
        capHeight: Float,
        targetHeight: Float,
        maxWidth: Float,
        tracking: Float,
    ): GlyphLayout {
        require(glyphWidths.isNotEmpty()) { "glyphWidths 不能为空" }
        require(capHeight > 0f) { "capHeight 必须为正，实际 $capHeight" }

        val heightScale = targetHeight / capHeight
        val scaledWidths = glyphWidths.map { it * heightScale }
        val totalTracking = tracking * (glyphWidths.size - 1)
        val naturalWidth = scaledWidths.sum() + totalTracking

        // 超宽时整串再等比缩小。这替代了契约旧版"按字符数切三档字号"的规则。
        val fitScale = if (naturalWidth > maxWidth) maxWidth / naturalWidth else 1f
        val scale = heightScale * fitScale

        val finalWidths = glyphWidths.map { it * scale }
        val finalTracking = tracking * fitScale
        val totalWidth = finalWidths.sum() + finalTracking * (finalWidths.size - 1)

        var cursor = -totalWidth / 2f
        val offsets = finalWidths.map { w ->
            val centre = cursor + w / 2f
            cursor += w + finalTracking
            centre
        }
        return GlyphLayout(scale = scale, offsetsX = offsets)
    }
}
