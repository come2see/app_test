package tech.illusion.space24.content

import android.util.Log
import com.pico.spatial.core.ecs.Entity

private const val GLYPH_TAG = "Space24Glyph"

/**
 * 14 个字模的原型实体池。
 *
 * 每个字模只 `loadSuspend` 一次建立原型（`enabled = false`、不入渲染树），需要显示时
 * `clone()` 出实例——同一字模在同一副牌里可能出现多次，重复解析同一个 `.usdc` 是白付的开销。
 *
 * 原型的包围盒在加载时量一次并缓存：`getVisualBounds` 对 3D 模型有效（对 2D 附件面板才恒返回 0）。
 */
class GlyphLoader {

    private val prototypes = mutableMapOf<Glyph, Entity>()
    private val widths = mutableMapOf<Glyph, Float>()
    private val depths = mutableMapOf<Glyph, Float>()
    private var capHeightValue = 0f

    /** 加载全部字模原型。任一字模失败只记日志并跳过，不让整个板面崩掉。 */
    suspend fun preload() {
        for (glyph in Glyph.values()) {
            val uri = "asset://glyphs/${glyph.assetName}.usdc"
            try {
                val entity = Entity.loadSuspend(uriString = uri)
                entity.setName("GlyphProto_${glyph.assetName}")
                entity.enabled = false
                val bounds = entity.getVisualBounds(relativeTo = null, recursive = true)
                prototypes[glyph] = entity
                widths[glyph] = bounds.size.x
                depths[glyph] = bounds.size.z
                // 各字模字高统一（授作规范），取最大值以防个别字模有微小溢出。
                if (bounds.size.y > capHeightValue) capHeightValue = bounds.size.y
                Log.w(GLYPH_TAG, "loaded ${glyph.assetName} w=${bounds.size.x} h=${bounds.size.y} d=${bounds.size.z}")
            } catch (t: Throwable) {
                // 只 catch 具体异常会被别的异常打崩进程——这是 SpaceVinyl 吃过的亏。
                Log.w(GLYPH_TAG, "load FAILED $uri: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        Log.w(GLYPH_TAG, "preload done loaded=${prototypes.size} expected=${Glyph.values().size}")
    }

    /** 字模在原始单位下的宽度；未加载成功的返回 0。 */
    fun widthOf(glyph: Glyph): Float = widths[glyph] ?: 0f

    /**
     * 字模在原始单位下的厚度（Z 轴，挤出深度）；未加载成功的返回 0。
     *
     * 授作时 pivot 用 `ORIGIN_GEOMETRY`/`center='BOUNDS'`，三轴都居中——包括 Z——所以这个厚度
     * 是**整个挤出体**的深度，实体自身局部原点正好在厚度中点。挂载时若想让某一面（而不是
     * 中点）贴合到某个 Z 值，调用方必须自己把中心再偏移 `depthMeters / 2`；见
     * `SpatialStage.kt` 的 `mountGlyphs` 如何用它算出背面贴合面板的挂载 Z。
     */
    fun depthOf(glyph: Glyph): Float = depths[glyph] ?: 0f

    /** 各字统一的字高（原始单位）。preload 之前为 0。 */
    fun capHeight(): Float = capHeightValue

    /**
     * 14 个原型是否全部加载成功。
     *
     * 这是**降级开关**：任一字模缺失就让卡面与运算符回落到 2D 文字，而不是显示空白。
     * 缺一个字模就整体回落而非逐字混排，是因为混排出来的数字（一半立体一半平面）
     * 比统一的 2D 文字更难读，也更像是坏了。
     */
    fun isComplete(): Boolean = prototypes.size == Glyph.values().size

    /**
     * 克隆一个可显示的实例；原型缺失时返回 null。
     *
     * 必须显式传 `recursive = true`——默认的 `CloneOptions` 是非递归的，字模的挤出网格挂在
     * 根实体的子节点下，非递归克隆会拿到一个没有几何体的空壳（SpaceFlightChess /
     * SpaceMinesweeper 两个姊妹项目对模型克隆都踩过这个坑，统一显式传 true）。
     */
    fun instantiate(glyph: Glyph): Entity? {
        val proto = prototypes[glyph] ?: return null
        return proto.clone(Entity.CloneOptions(recursive = true))?.apply { enabled = true }
    }
}
