package tech.illusion.space24.content

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import tech.illusion.space24.game.GamePhase
import tech.illusion.space24.game.Op

private const val STAGE_TAG = "Space24Stage"

/**
 * 9 个面板承载全部 2D 内容：1 个 chrome（开始页/HUD/状态行/动作行/结算浮层）+ 4 张卡 + 4 个运算符。
 *
 * 布局的位置/可见性通过 `SpatialView` 的 `update` 回调驱动（见 [applyLayout]），而不是
 * `LaunchedEffect`。SDK 文档（`foundation-6.0.0` 的 `SpatialView.kt`）写着"这个函数里读到的
 * compose state 一旦变化，`update` 就会被 compose 运行时重新调用"，**这一点已经用设备日志实测
 * 验证过**：借 `DebugLaunch.kt` 的 `-e debug_start playing` 钩子把 `session.phase` 从
 * `NOT_STARTED` 打到 `PLAYING`，`applyLayout` 的日志显示它确实在阶段切换后立刻重新跑了一遍
 * （`phase=NOT_STARTED cardCount=0` -> `phase=PLAYING cardCount=4`），且截图能看到卡片/运算符
 * 面板随之出现——证明 `update` 对**阶段切换**是可靠的。**牌数递减（同一 PLAYING 阶段内的重新
 * 居中）仍未被这样验证过**：那需要真的完成一次合并，而模拟器的注入点击打不准空间窗口按钮，
 * 这一情形目前只靠同一条 SDK 文档保证与"phase 切换已证实生效"的同一机制类推，留作已知未验证项。
 * 若之后设备证据显示牌数变化没有触发重新布局，改用 `LaunchedEffect` 键上
 * `state.cards.size`/`state.phase` 的回落方案。
 */
@Composable
fun SpatialStage() {
    val density = LocalDensity.current.density
    fun px(dp: Float): Int = (dp * density).toInt()

    // 会话与其余应用状态仍由 HomeStage 持有；这里只取它暴露的快照供面板布局使用。
    val state = rememberStageState()

    // 字模是否已全部就位。Task 3 里恒为 false（还没有字模），Task 7 接上真实加载结果。
    // 它同时是降级开关：字模没挂上时卡面自动回落到 2D 文字，而不是空白。
    var glyphsReady by remember { mutableStateOf(false) }

    // 面板实体缓存：initial 只跑一次，update 每次重组都跑，两者通过它通信。
    val panels = remember { mutableMapOf<String, Entity>() }

    // 字模原型池，与"面板 id -> 当前挂载的字模实例"缓存。同样跨 initial/update 复用。
    val loader = remember { GlyphLoader() }
    val mounted = remember { mutableMapOf<String, MountedGlyphs>() }

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            AttachmentPanel(
                id = "chrome",
                size = IntSize(px(BoardGeometry.WINDOW_WIDTH_DP), px(BoardGeometry.WINDOW_HEIGHT_DP)),
            ) { ChromeContent(state) }

            for (i in 0 until BoardGeometry.MAX_CARDS) {
                AttachmentPanel(
                    id = "card_$i",
                    size = IntSize(px(BoardGeometry.CARD_WIDTH_DP), px(BoardGeometry.CARD_HEIGHT_DP)),
                ) {
                    val card = state.cards.getOrNull(i)
                    if (card != null) {
                        CardFace(
                            card = card,
                            selected = card.id == state.selectedCardId,
                            hinted = card.id in state.hintedCardIds,
                            showValue = !glyphsReady, // Task 7 引入 glyphsReady；本任务里它恒为 false
                            onClick = { state.onCardTap(card.id) },
                        )
                    }
                }
            }

            for (i in 0 until BoardGeometry.OP_COUNT) {
                val op = Op.values()[i]
                AttachmentPanel(
                    id = "op_$i",
                    size = IntSize(px(BoardGeometry.OP_SIZE_DP), px(BoardGeometry.OP_SIZE_DP)),
                ) {
                    OperatorFace(
                        op = op,
                        state = when {
                            op == state.selectedOp -> OperatorState.SELECTED
                            state.selectedCardId != null -> OperatorState.ENABLED
                            else -> OperatorState.DIMMED
                        },
                        showSymbol = !glyphsReady, // 同上
                        onClick = { state.onOpTap(op) },
                    )
                }
            }
        },
        initial = { content, attachments ->
            // 面板必须先挂进渲染树、先跑一次布局——字模 preload 是后来加的降级开关，不该让
            // 连 chrome（开始页）都要等 14 个 USD 资产全部加载完才显示（I4）。
            var attached = 0
            for (id in panelIds()) {
                val entity = attachments.entity(id)
                if (entity == null) {
                    Log.w(STAGE_TAG, "attachment MISSING id=$id")
                    continue
                }
                // attachments.entity(id) 本身不会把面板加进渲染树（SpaceCube 记录的 2 号坑）。
                content.addEntity(entity)
                panels[id] = entity
                attached++
            }
            Log.w(STAGE_TAG, "attachments attached=$attached expected=${panelIds().size}")
            applyLayout(content, panels, state, density, loader, mounted)

            loader.preload()
            // 写 Compose state 会触发重组，让 CardFace/OperatorFace 的 showValue/showSymbol
            // 跟着翻——字模全就位则关掉 2D 文字，任一缺失则保持 2D 文字可见。
            glyphsReady = loader.isComplete()
            // 不完全依赖上面这次 state 写入触发的重组去再跑一遍 update：显式再调一次
            // applyLayout，保证 preload 完成后字模立刻挂上，不依赖“phase 切换已证实、
            // 但同一 phase 内的 state 写入是否总触发 update”这条尚未在设备上专门验证过的假设。
            applyLayout(content, panels, state, density, loader, mounted)
        },
        update = { content, _ ->
            applyLayout(content, panels, state, density, loader, mounted)
        },
    )
}

/** 9 个面板的 id，顺序固定：chrome、4 张卡、4 个运算符。 */
private fun panelIds(): List<String> =
    listOf("chrome") +
        (0 until BoardGeometry.MAX_CARDS).map { "card_$it" } +
        (0 until BoardGeometry.OP_COUNT).map { "op_$it" }

/**
 * 把面板摆到契约 dp 位置对应的米坐标上，并按游戏状态设 `enabled`。
 *
 * 每次重组都会跑一遍——牌数递减时整行要重新居中，所以位置不能只在 initial 算一次。
 *
 * `boardVisible` 只在 `PLAYING` 为真，因此 `NOT_STARTED`（开始页）与 `RESULT`（结算浮层）两态下
 * 卡片与运算符面板都关闭。**`RESULT` 态的关闭是必须的，不是优化**：结算浮层画在 z=0 的 chrome
 * 面板上，卡片面板同在 z=0，Task 7 挂上字模后字模的挂载 z（`depthMeters / 2`，恒为正）会穿过
 * 浮层浮在最前，连 scrim 一起盖掉。
 *
 * 每个面板的 `TransformComponent` 都显式判空并分支打印——不能让"位置已设"的假象在组件缺失时
 * 也照样成立，那是 Task 2 探针立下的规矩（一条能断言假事实的日志比没有日志更糟）。
 */
private fun applyLayout(
    content: SpatialViewContent,
    panels: Map<String, Entity>,
    state: StageState,
    density: Float,
    loader: GlyphLoader,
    mounted: MutableMap<String, MountedGlyphs>,
) {
    val cardCount = state.cards.size
    val boardVisible = state.phase == GamePhase.PLAYING

    // 验证 update 回调是否随 phase/cardCount 变化重触发的唯一手段：这条日志每次 applyLayout
    // 被调用都打一遍，设备上比对时间戳/次数即可判断 update 是否真的重新跑了。
    Log.w(STAGE_TAG, "applyLayout phase=${state.phase} cardCount=$cardCount")

    fun moveTo(id: String, entity: Entity, xDp: Float, yDp: Float) {
        val transform = entity.components[TransformComponent::class.java]
        // 只打缺组件的告警，不打逐帧成功日志——位置多半每次都在变，成功日志会把 logcat
        // 刷成噪声；上面那条 applyLayout 级别的日志已经足够证明这一轮跑过了。
        if (transform == null) {
            Log.w(STAGE_TAG, "panel id=$id has NO TransformComponent, position NOT set")
            return
        }
        transform.setPosition(content.anchorOf(xDp, yDp, density, 0f))
    }

    panels["chrome"]?.let { chrome ->
        chrome.enabled = true
        moveTo("chrome", chrome, BoardGeometry.WINDOW_WIDTH_DP / 2f, BoardGeometry.WINDOW_HEIGHT_DP / 2f)
    }

    for (i in 0 until BoardGeometry.MAX_CARDS) {
        val id = "card_$i"
        val entity = panels[id] ?: continue
        val visible = boardVisible && i < cardCount
        entity.enabled = visible
        if (!visible) continue
        moveTo(id, entity, BoardGeometry.cardCenterXDp(i, cardCount), BoardGeometry.CARD_ROW_CENTER_Y_DP)
    }

    for (i in 0 until BoardGeometry.OP_COUNT) {
        val id = "op_$i"
        val entity = panels[id] ?: continue
        entity.enabled = boardVisible
        if (!boardVisible) continue
        moveTo(id, entity, BoardGeometry.opCenterXDp(i), BoardGeometry.OP_ROW_CENTER_Y_DP)
    }

    // dp -> 米。字模是场景实体的子节点，局部坐标是米，所以这三个尺寸必须换算，
    // 不能把契约的 dp 数值直接当成场景单位用。
    fun metersOf(dp: Float): Float =
        content.convertSize(
            Vector3(0f, dp * density, 0f),
            ViewCoordinateSpace.Local,
            content.localSpatialCoordinateSpace,
        ).y

    val trackingM = metersOf(GLYPH_TRACKING_DP)

    // 字模是否可用于挂载。`loader.isComplete()` 是全有或全无的降级开关（见 GlyphLoader 文档）：
    // preload 只跑一次，这个值在整个进程生命周期内是常量，但仍在每次 applyLayout 里读取一次
    // 而不是缓存成外部变量——避免有人以后误以为它会随时间变化而去做增量判断。
    //
    // 这道 gate 是必须的：mountGlyphs 本身只按"这个字模有没有加载成功"逐字判断，并不知道
    // isComplete() 的全局结果——如果不在这里拦一道，任一字模缺失时就会出现"大部分字挂上 3D、
    // 缺的那个字悄悄消失"的混排局面，而不是文档承诺的整体回落到 2D 文字。
    val glyphsAvailable = loader.isComplete()

    // 卡面字模
    val cardTargetHeightM = metersOf(BoardGeometry.CARD_HEIGHT_DP * CARD_GLYPH_HEIGHT_RATIO)
    val cardMaxWidthM = metersOf(BoardGeometry.CARD_WIDTH_DP - CARD_GLYPH_PADDING_DP)
    for (i in 0 until BoardGeometry.MAX_CARDS) {
        val id = "card_$i"
        val panel = panels[id] ?: continue
        val card = state.cards.getOrNull(i)
        val visible = boardVisible && card != null && glyphsAvailable
        val key = if (visible) card!!.value.toString() else ""
        val glyphs = if (visible) GlyphCatalog.decompose(key) else emptyList()
        val result = mountGlyphs(
            panel = panel,
            previous = mounted[id],
            key = key,
            glyphs = glyphs,
            loader = loader,
            targetHeight = cardTargetHeightM,
            maxWidth = cardMaxWidthM,
            tracking = trackingM,
        )
        mounted[id] = result
        result.entities.forEach { it.enabled = visible }
    }

    // 运算符字模
    val opTargetHeightM = metersOf(BoardGeometry.OP_SIZE_DP * OP_GLYPH_HEIGHT_RATIO)
    val opMaxWidthM = metersOf(BoardGeometry.OP_SIZE_DP)
    for (i in 0 until BoardGeometry.OP_COUNT) {
        val id = "op_$i"
        val panel = panels[id] ?: continue
        val op = Op.values()[i]
        val opVisible = boardVisible && glyphsAvailable
        val key = if (opVisible) op.symbol else ""
        val glyphs = if (opVisible) listOf(GlyphCatalog.glyphFor(op)) else emptyList()
        val result = mountGlyphs(
            panel = panel,
            previous = mounted[id],
            key = key,
            glyphs = glyphs,
            loader = loader,
            targetHeight = opTargetHeightM,
            maxWidth = opMaxWidthM,
            tracking = trackingM,
        )
        mounted[id] = result
        result.entities.forEach { it.enabled = opVisible }
    }
}

/** 卡面字高占卡高的比例。初值凭观感给，真机看过再调。 */
private const val CARD_GLYPH_HEIGHT_RATIO = 0.42f

/** 卡面左右各留 8dp 内边距，整串超出剩余宽度时等比缩小。 */
private const val CARD_GLYPH_PADDING_DP = 16f

/** 运算符字高占按钮边长的比例。 */
private const val OP_GLYPH_HEIGHT_RATIO = 0.55f

/** 字距（dp），调用方换算成米后再传给 `mountGlyphs`。 */
private const val GLYPH_TRACKING_DP = 2f

/**
 * 每个字模挂载时绕局部 X 轴（pitch）额外转的角度，修正真机上"字模躺平、扁平薄片带斜向
 * 折痕"的朝向问题——见 `mountGlyphs` 里 `setEulerAngles` 调用处的详细说明。**待真机验证定
 * 型**：90 只是第一个尝试值，如果真机上字形已经立起来但左右镜像/上下颠倒，先试
 * `-GLYPH_MOUNT_PITCH_DEG`；如果朝向轴根本不对（比如变成绕错了轴），再回头重新诊断，不要
 * 无根据地继续加大或缩小这个角度。
 */
private const val GLYPH_MOUNT_PITCH_DEG = 90f

/**
 * 一个面板上当前挂着的字模实例。用它判断"牌面没变就别重建"——
 * 每次重组都重挂会在合并动画（Phase 3）到来前就先把帧率吃光。
 *
 * **已知、已接受的限制（不在本任务修）**：换批时旧的 [entities] 只会在 [mountGlyphs] 里被
 * `enabled = false`，从不 `removeChild`/`destroy`——真实对局里每次牌数/牌面变化都会攒一批
 * 永久挂在面板下、只是禁用的旧字模实例，而不是仅在一次布局里发生。没有当场修的原因：
 * 清理的正确形态取决于还没设计出来的 Phase 3 合并动画——合并动画大概率需要旧字模先播完
 * 动画再销毁，而不是重新布局时立即销毁，现在提前修很可能等 Phase 3 落地后又要重做一遍。
 * 细节见 `.spatialsdk/ui-verification-log.md` 第 2 轮"结论"部分。
 */
private class MountedGlyphs(val key: String, val entities: List<Entity>)

/**
 * 把 [glyphs] 挂到 [panel] 上，先清掉上一批。
 *
 * 字模是面板实体的**子实体**，局部坐标 `(offsetX, 0, depthMeters / 2)`——局部原点就是面板
 * 中心，面板本身在 `z = 0`（见本文件顶部关于结算浮层/卡片面板同在 z=0 的说明），所以横向
 * 居中由构造保证，不经任何 dp→米 换算，只有 x 偏移和缩放需要算。
 *
 * Z 偏移取 `depthMeters / 2` 而不是 `0`：字模授作 pivot 在几何中心（含 Z 轴），若把中心直接
 * 钉在面板表面 `z = 0`，背面会陷进面板半个厚度、正面只凸出半个厚度，不满足规格 §4.4"背面
 * 与卡面齐平"的要求。把中心沿 Z 轴正方向（面板外侧）推 `depthMeters / 2`
 * （`depthMeters = loader.depthOf(glyph) * layout.scale`，字模自身厚度换算成米之后的值）：
 *   背面 z = depthMeters/2 - depthMeters/2 = 0            （与面板表面 z=0 齐平）
 *   正面 z = depthMeters/2 + depthMeters/2 = depthMeters  （凸出字模自身厚度）
 * 这两条对任意 scale/depthMeters 取值恒成立，不是凑出来的特例——凸出量因此不是一个单独
 * 选定的常量，而是每个字模自己的实际厚度（卡面数字约 1.3cm，接近规格"约 1.5cm"的预期，
 * 但由资产几何决定，不是可调常量）。此前（I1 修复前后）曾用固定常量 `GLYPH_Z_M` 作为额外
 * 偏移叠加在这个公式上，会把背面錨定在 `z = GLYPH_Z_M` 而不是 `z = 0`，与面板表面不齐平，
 * 已被移除——见函数体内对应位置的注释。
 *
 * **单位**：[targetHeight] 与 [maxWidth] 必须是**米**——字模是场景实体的子节点，局部坐标
 * 就是米。调用方用 `content.convertSize` 把契约的 dp 换成米，不要直接把 dp 数值传进来。
 * `glyphWidths` / `capHeight` 则是 `getVisualBounds` 量出的模型原始单位；`layout` 只用它们
 * 求比值，所以两组单位不同不矛盾，返回的 `scale` 正是"模型单位 → 米"的缩放，`depthOf` 同样
 * 是模型原始单位，乘同一个 `scale` 才能换算成米。
 */
private fun mountGlyphs(
    panel: Entity,
    previous: MountedGlyphs?,
    key: String,
    glyphs: List<Glyph>,
    loader: GlyphLoader,
    targetHeight: Float,
    maxWidth: Float,
    tracking: Float,
): MountedGlyphs {
    if (previous != null && previous.key == key) return previous
    // 已知限制，暂不修：这里只禁用旧实例，不 destroy/removeChild，见 MountedGlyphs 的文档注释。
    previous?.entities?.forEach { it.enabled = false }

    if (glyphs.isEmpty() || loader.capHeight() <= 0f) return MountedGlyphs(key, emptyList())

    val layout = GlyphCatalog.layout(
        glyphWidths = glyphs.map { loader.widthOf(it) },
        capHeight = loader.capHeight(),
        targetHeight = targetHeight,
        maxWidth = maxWidth,
        tracking = tracking,
    )

    val mounted = glyphs.mapIndexedNotNull { i, glyph ->
        val entity = loader.instantiate(glyph) ?: run {
            Log.w(STAGE_TAG, "instantiate failed for ${glyph.assetName}")
            return@mapIndexedNotNull null
        }
        entity.components[TransformComponent::class.java]?.apply {
            setScaleVector(Vector3(layout.scale, layout.scale, layout.scale))
            // 字模授作 pivot 在几何中心（含 Z 轴）。面板表面在 z = 0，把中心放在 depthMeters/2
            // 处（而不是叠加任何额外的固定偏移常量）：
            //   背面 z = depthMeters/2 - depthMeters/2 = 0            （与面板表面 z=0 齐平）
            //   正面 z = depthMeters/2 + depthMeters/2 = depthMeters  （凸出字模自身厚度）
            // 这两条对任意 scale/depthMeters 取值恒成立，不是凑出来的特例。
            val depthMeters = loader.depthOf(glyph) * layout.scale
            setPosition(Vector3(layout.offsetsX[i], 0f, depthMeters / 2f))
            // 真机实测：字模在头显里躺平，呈现为扁平薄片带一道斜向折痕，不是正对镜头的立
            // 字——不是位置错，是朝向错。授作时 Blender 原生把字模摆在自己的 XY 平面里（字
            // 高沿 Y、厚度沿 Z），但运行时渲染出来的效果说明 SDK 加载 .usdc 时按另一套轴向
            // 假设摆放了几何体。没有确认过的 Blender 导出侧修法（试过 usd_export 的
            // convert_orientation，逐字节比对确认它只改 USD 的 upAxis 声明字符串，不碰任何
            // 顶点数据——如果 SDK 真的读了这个声明去做换算，改声明或许有用；如果 SDK 忽略
            // 声明只认原始坐标，改声明就是空的，两种情况从资产侧都测不出来）。改在这里而不
            // 是回 Blender 重新导出：这是全部 14 个字模唯一的摆放入口，效果一致、可逆，不用
            // 每次改都重新走一遍 Blender→14 资产→重新构建那一整套，试错成本是改一个数再编译。
            // GLYPH_MOUNT_PITCH_DEG 是待真机验证定型的值，正负号和角度都可能要调——改这一个
            // 常量，不要改 Blender 导出参数或字模资产本身。
            setEulerAngles(EulerAngles(pitch = GLYPH_MOUNT_PITCH_DEG, yaw = 0f, roll = 0f))
        }
        panel.addChild(entity)
        entity
    }
    return MountedGlyphs(key, mounted)
}
