package tech.illusion.space24.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.graphics.SpatialHoverStyle
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import tech.illusion.space24.game.CardOnBoard
import tech.illusion.space24.game.FeedbackKind
import tech.illusion.space24.game.Op

enum class CardTextSize { LARGE, MEDIUM, SMALL }

/**
 * Card faces run from one character ("7") to six ("-28561"), so the face steps its text size down by
 * character count instead of letting a long intermediate wrap or clip inside the fixed tile.
 * Contract §2.3: ≤2 chars -> titleLarge, =3 -> titleSmall, >=4 -> bodySmall.
 */
fun cardTextSizeFor(text: String): CardTextSize = when {
    text.length <= 2 -> CardTextSize.LARGE
    text.length == 3 -> CardTextSize.MEDIUM
    else -> CardTextSize.SMALL
}

/**
 * Null means "show nothing" — the status line never becomes a modal interruption. Copy is verbatim
 * from contract §4 — the only four feedback strings this app is allowed to show; do not reword them.
 */
fun statusTextFor(feedback: FeedbackKind): String? = when (feedback) {
    FeedbackKind.NONE -> null
    FeedbackKind.NOT_TWENTY_FOUR -> "不是 24，可撤销或重置"
    FeedbackKind.NOT_DIVISIBLE -> "除不尽，换个运算或换张牌"
    FeedbackKind.CORRECT -> "答对！+1"
    FeedbackKind.HINT_DEAD_END -> "当前牌面已无解，请撤销或重置"
}

/** Contract §3: negative and dead-end feedback is `error`, a correct answer is `passable`. */
@Composable
fun statusColorFor(feedback: FeedbackKind): Color = when (feedback) {
    FeedbackKind.NONE -> onSurfaceSecondary()
    FeedbackKind.CORRECT -> PicoTheme.colorScheme.passable
    else -> PicoTheme.colorScheme.error
}

/**
 * 一张卡的面板内容。尺寸由承载它的 AttachmentPanel 决定，所以这里 `fillMaxSize` 而不是
 * 自带 `size` —— 两处都写尺寸会在面板尺寸调整时静默错位。
 *
 * 契约 §3：`selected` 换填色并加 2dp 描边；`hinted` 保持默认填色、加 3dp `interaction` 描边，
 * 两态因此视觉可分。契约还要求 hinted 边框带轻微辉光，未找到可核实的 SpatialUI API，未实现。
 *
 * [showValue] 为 false 时不画数值文字——3D 字模接管之后由字模显示，留着 2D 文字会和字模重影。
 */
@Composable
fun CardFace(
    card: CardOnBoard,
    selected: Boolean,
    hinted: Boolean,
    showValue: Boolean,
    onClick: () -> Unit,
) {
    val face = card.value.toString()
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (selected) surfaceAccent() else surfaceElevated())
            .then(
                when {
                    selected -> Modifier.border(2.dp, onSurfacePrimary(), shape)
                    hinted -> Modifier.border(3.dp, PicoTheme.colorScheme.interaction, shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            // 手写 clickable：不像 SpatialUI 的 Button 那样自带 hover 和触感，两者都要显式加。
            .spatialHoverEffect(SpatialHoverStyle.Highlight)
            .controllerHapticFeedback(),
        contentAlignment = Alignment.Center,
    ) {
        if (showValue) {
            Text(
                text = face,
                style = when (cardTextSizeFor(face)) {
                    CardTextSize.LARGE -> PicoTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
                    CardTextSize.MEDIUM -> PicoTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
                    CardTextSize.SMALL -> PicoTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                },
                color = if (selected) onAccent() else onSurfacePrimary(),
            )
        }
    }
}

/** 运算符按钮的三态。契约 §2.3：选中 / 可用 / 未选牌时的禁用。 */
enum class OperatorState { SELECTED, ENABLED, DIMMED }

/**
 * 一个运算符的面板内容。尺寸同样由 AttachmentPanel 决定。
 *
 * [showSymbol] 为 false 时不画符号文字，理由同 [CardFace] 的 `showValue`。
 */
@Composable
fun OperatorFace(op: Op, state: OperatorState, showSymbol: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        enabled = state != OperatorState.DIMMED,
        shape = RoundedCornerShape(16.dp),
        colors = when (state) {
            OperatorState.SELECTED -> primaryButtonColors()
            OperatorState.ENABLED -> secondaryButtonColors()
            OperatorState.DIMMED -> dimmedButtonColors()
        },
    ) {
        if (showSymbol) {
            Text(text = op.symbol, style = PicoTheme.typography.titleSmall)
        }
    }
}
