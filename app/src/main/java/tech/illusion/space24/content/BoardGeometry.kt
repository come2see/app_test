package tech.illusion.space24.content

/**
 * 板面元素在**窗口相对 dp** 坐标系里的中心位置，坐标原点是窗口左上角。
 *
 * 数值全部取自设计契约 §2.3，不在此处新造。这些是纯函数，不碰 Android 也不碰 Compose，
 * 因此 Phase 0 的探针和正式实现可以共用同一份——两处各算一遍是错位的常见来源。
 *
 * 米空间的换算不在这里做：那需要运行时的 `SpatialViewContent`，见 `SpatialCoords.kt`。
 */
object BoardGeometry {

    const val WINDOW_WIDTH_DP: Float = 900f
    const val WINDOW_HEIGHT_DP: Float = 640f

    const val CARD_WIDTH_DP: Float = 104f
    const val CARD_HEIGHT_DP: Float = 136f
    const val CARD_GAP_DP: Float = 16f

    /** 契约 §2.3：卡片行上沿在窗口下方 96dp 处，卡高 136 → 中心 96 + 68。 */
    const val CARD_ROW_CENTER_Y_DP: Float = 164f

    const val OP_SIZE_DP: Float = 56f
    const val OP_GAP_DP: Float = 16f

    /** 契约 §2.3：运算符行上沿 256dp，边长 56 → 中心 256 + 28。 */
    const val OP_ROW_CENTER_Y_DP: Float = 284f

    /** 契约 §2.3：运算符行下沿到状态行的间隔（16dp）。状态行本身没有独立的行高常量。 */
    const val OP_TO_STATUS_GAP_DP: Float = 16f

    const val MAX_CARDS: Int = 4
    const val OP_COUNT: Int = 4

    /**
     * 卡片行上沿（窗口相对 dp）。由 [CARD_ROW_CENTER_Y_DP] 与卡高反推，不是独立写死的数字——
     * 这样任何一处只改中心常量，上沿会跟着动，不会出现两处数字各说各话的情况（I2）。
     */
    val CARD_ROW_TOP_DP: Float get() = CARD_ROW_CENTER_Y_DP - CARD_HEIGHT_DP / 2f

    /** 卡片行下沿（窗口相对 dp），同样由中心常量反推。 */
    val CARD_ROW_BOTTOM_DP: Float get() = CARD_ROW_TOP_DP + CARD_HEIGHT_DP

    /** 运算符行上沿（窗口相对 dp），由中心常量反推。 */
    val OP_ROW_TOP_DP: Float get() = OP_ROW_CENTER_Y_DP - OP_SIZE_DP / 2f

    /** 运算符行下沿（窗口相对 dp），由中心常量反推。 */
    val OP_ROW_BOTTOM_DP: Float get() = OP_ROW_TOP_DP + OP_SIZE_DP

    /**
     * 卡片行下沿到运算符行上沿的间隔（契约 §2.3：24dp）。**由两行各自的位置常量反推**，
     * 不是单独写死的 24——这样运算符面板（读 [OP_ROW_CENTER_Y_DP]）与 chrome 占位
     * （读这个属性）永远不可能各算出一套不一致的间隔（I2 的根因正是两处各写了一份）。
     */
    val CARD_TO_OP_GAP_DP: Float get() = OP_ROW_TOP_DP - CARD_ROW_BOTTOM_DP

    /**
     * 状态行上沿（窗口相对 dp）。契约 §2.3 的 328dp，由运算符行下沿 + 契约间隔反推得到，
     * 不在调用方（`HomeStage.kt`）里重复写这个数字。
     */
    val STATUS_LINE_TOP_DP: Float get() = OP_ROW_BOTTOM_DP + OP_TO_STATUS_GAP_DP

    /** 一行 [count] 个宽 [itemWidth]、间距 [gap] 的元素里，第 [index] 个的中心 x（整行居中）。 */
    private fun centeredRowX(index: Int, count: Int, itemWidth: Float, gap: Float): Float {
        val totalWidth = count * itemWidth + (count - 1) * gap
        val left = (WINDOW_WIDTH_DP - totalWidth) / 2f
        return left + itemWidth / 2f + index * (itemWidth + gap)
    }

    /**
     * 第 [index] 张卡的中心 x，当前共 [cardCount] 张。
     *
     * 牌数随合并递减时整行**重新居中**——留着原位的空洞会让板面看起来是坏的。
     */
    fun cardCenterXDp(index: Int, cardCount: Int): Float {
        require(cardCount in 1..MAX_CARDS) { "cardCount=$cardCount 超出 1..$MAX_CARDS" }
        require(index in 0 until cardCount) { "index=$index 超出 0..<$cardCount" }
        return centeredRowX(index, cardCount, CARD_WIDTH_DP, CARD_GAP_DP)
    }

    /** 第 [index] 个运算符的中心 x。运算符恒为 4 个，不随牌数变化。 */
    fun opCenterXDp(index: Int): Float {
        require(index in 0 until OP_COUNT) { "index=$index 超出 0..<$OP_COUNT" }
        return centeredRowX(index, OP_COUNT, OP_SIZE_DP, OP_GAP_DP)
    }
}
