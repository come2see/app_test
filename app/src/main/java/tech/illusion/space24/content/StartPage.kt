package tech.illusion.space24.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.graphics.SpatialHoverStyle
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import tech.illusion.space24.game.Difficulty

/**
 * [bankReady] is false while the puzzle bank is still being pre-generated off the main thread; the
 * start button stays visibly unavailable and its tap is a no-op until the bank exists.
 *
 * Contract §2.1 element order: appTitleText -> startDifficultySegment -> startBestScoreLabel ->
 * startBestScoreValue -> startBeginButton. That centered column is unchanged by this file's other
 * contents. `startHowToPlayButton` (§2.1's appended row) floats independently in the top-right
 * corner and does not join it; it toggles [showHowToPlay], a pure Compose-level presentation flag
 * (contract §2.5 — not `GamePhase`, not persisted in `GameSession`) that reveals the
 * `howto_play_overlay` (§2.5) stacked above everything else in this page.
 */
@Composable
fun StartPage(
    selectedDifficulty: Difficulty,
    onSelectDifficulty: (Difficulty) -> Unit,
    bestScore: Int?,
    bankReady: Boolean,
    onStart: () -> Unit,
) {
    var showHowToPlay by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Space24",
                style = PicoTheme.typography.titleLarge,
                color = onSurfacePrimary(),
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Row(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(surfaceElevated())
                    .padding(4.dp),
            ) {
                for (difficulty in Difficulty.values()) {
                    val selected = difficulty == selectedDifficulty
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) surfaceAccent() else Color.Transparent)
                            .clickable { onSelectDifficulty(difficulty) }
                            // Hand-rolled clickable — Button's built-in hover/haptics do not apply.
                            .spatialHoverEffect(SpatialHoverStyle.Highlight)
                            .controllerHapticFeedback()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = difficulty.label(),
                            style = PicoTheme.typography.bodySmall,
                            color = if (selected) onAccent() else onSurfaceSecondary(),
                        )
                    }
                }
            }
            Text(
                text = "历史最高（${selectedDifficulty.label()}）",
                style = PicoTheme.typography.bodySmall,
                color = onSurfaceSecondary(),
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = formatBestScore(bestScore),
                style = PicoTheme.typography.titleLarge,
                color = onSurfacePrimary(),
                modifier = Modifier.padding(bottom = 32.dp),
            )
            // Contract §2.1: startBeginButton is a pill (999dp) with 14/32 (vertical/horizontal)
            // padding and titleSmall text; disabled state swaps to dimmedButtonColors()
            // (fillTertiary/labelSecondary). Position/style/logic unchanged by this increment; the
            // only addition is closing the howto overlay before a round actually starts, as a
            // defensive second layer under the overlay's own scrim already blocking this tap.
            Button(
                onClick = {
                    if (bankReady) {
                        showHowToPlay = false
                        onStart()
                    }
                },
                enabled = bankReady,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 32.dp),
                colors = if (bankReady) primaryButtonColors() else dimmedButtonColors(),
            ) {
                Text(
                    text = if (bankReady) "开始游戏" else "题库准备中",
                    style = PicoTheme.typography.titleSmall,
                )
            }
        }

        // startHowToPlayButton (contract §2.1 appended row, final visual spec confirmed at gate A):
        // container reuses startBeginButton's own enabled-state role (surfaceAccent(), i.e.
        // PicoTheme.colorScheme.interaction — the same role primaryButtonColors() puts on
        // startBeginButton) at low alpha for a soft tinted fill; content stays near-opaque so the
        // badge/text read clearly against it. No border — the tint alone carries the "present but
        // secondary" affordance. Uses the Small preset size (not a hand-built ButtonSize, per this
        // codebase's own "custom ButtonSize silently drops the corner radius" pitfall) and leaves
        // shape/contentPadding/gap at their size-derived defaults. The "?" badge rides in Button's
        // documented leadingIcon slot (Button.kt: "a leading icon before the content" — any
        // composable, not only an Icon) rather than a hand-rolled Row-in-content, so this needs no
        // new "icon + text share one Text" or "RowScope-in-content" pattern at all.
        Button(
            onClick = { showHowToPlay = true },
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = surfaceAccent().copy(alpha = 0.3f),
                contentColor = surfaceAccent(),
            ),
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(surfaceAccent().copy(alpha = 0.68f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "?",
                        style = PicoTheme.typography.bodySmall,
                        color = onAccent(),
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp),
        ) {
            Text(text = "玩法", style = PicoTheme.typography.bodySmall)
        }

        if (showHowToPlay) {
            HowToPlayOverlay(onDismiss = { showHowToPlay = false })
        }
    }
}

/**
 * Contract §2.5 `howto_play_overlay`: `startHowToPlayScrim` + `startHowToPlayCard`. Declared last in
 * `StartPage`'s Box so it paints above the centered column and the corner button, naturally blocking
 * pointer events to `startBeginButton` etc. underneath it without any per-element "disabled" flag.
 *
 * Hand-rolled Box+pointerInput (not `BasicSheet`, the real `Dialog`-backed primitive `ResultPanel`
 * uses for its own modal): the task's own spec calls out the scrim/card pointer-consumption
 * mechanics explicitly, and this overlay's card must stay inside the same 900x640 start_page frame
 * rather than opening a second platform window. `BasicSheet` remains a documented option for a
 * future pass (see `.spatialsdk/design-contract.md` §2.5 risk notes) if that trade-off is revisited.
 *
 * The card reuses `ResultPanel`'s actually-verified panel treatment — opaque `surfacePanel()` +
 * `onPanelPrimary()`/`onPanelSecondary()` text — rather than the contract's originally-sketched
 * `backgroundMaterial(true, Material.Regular)` glass, which has zero precedent anywhere in this repo
 * and, per `ResultPanel.kt`'s own comment, washed this app's dark palette toward silver the one time
 * a `Material` blend (`Thick`) was tried on this exact card surface. Device-screenshot re-validation
 * of `Material.Regular` specifically remains open future work, not something this build-only pass can
 * confirm; opaque `surfacePanel()` is the conservative, already-working choice for now.
 */
@Composable
private fun HowToPlayOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // fillTertiary at added alpha, same role contract §2.4's resultOverlayScrim specifies
            // for its own scrim; 0.72 is this increment's own pick (no prior numeric precedent in
            // this repo to match), dark enough to read as "background pressed back" without hiding
            // the corner button's cyan tint entirely as a spatial-orientation cue.
            .background(PicoTheme.colorScheme.fillTertiary.copy(alpha = 0.72f))
            // startHowToPlayScrim must consume the tap itself (not just look dim) so a tap on the
            // empty area outside the card closes the overlay exactly like the close button does,
            // and so the tap never reaches startBeginButton/startDifficultySegment underneath.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(surfacePanel())
                // startHowToPlayCard consumes its own tap so a press anywhere on the card — not
                // just on the close button — never falls through to the scrim behind it and closes
                // the overlay by accident.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp),
        ) {
            // Title row: startHowToPlayTitleText (left) + startHowToPlayCloseButton (right), one
            // line. Box+align, not Row+weight — this codebase has only ever proven
            // ColumnScope.weight (GameBoard.kt's Spacer(Modifier.weight(1f))); RowScope.weight has
            // no precedent here, so this follows the contract's own documented conservative fallback
            // instead of being the first place in the app to try it.
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "玩法说明",
                    style = PicoTheme.typography.titleSmall,
                    color = onPanelPrimary(),
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Button(
                    onClick = onDismiss,
                    colors = secondaryButtonColors(),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 14.dp),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text(text = "×", style = PicoTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            // Scrollable body only — the title row above stays put. ColumnScope.weight(1f) is the
            // same API GameBoard.kt already proves; it caps this inner Column to whatever height
            // remains under the card's 480dp ceiling, so verticalScroll has a bound to scroll within
            // instead of the card just growing past its intended max height.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "简介",
                    style = PicoTheme.typography.titleSmall,
                    color = onPanelPrimary(),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "在90秒倒计时结束前,把每道题发到的4张数字牌,通过加减乘除一步步合并,尽可能多地拼出24点——解出的题目数就是你这一局的分数。",
                    style = PicoTheme.typography.bodySmall,
                    color = onPanelSecondary(),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Text(
                    text = "操作",
                    style = PicoTheme.typography.titleSmall,
                    color = onPanelPrimary(),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "- 点数字牌：点一张牌选中它作为操作数,再点另一张牌准备把两张牌合并。",
                    style = PicoTheme.typography.bodySmall,
                    color = onPanelSecondary(),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "- 点运算符：选中一张牌后,点 + − × ÷ 中的一个,决定接下来怎么合并这两张牌。",
                    style = PicoTheme.typography.bodySmall,
                    color = onPanelSecondary(),
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Text(
                    text = "规则",
                    style = PicoTheme.typography.titleSmall,
                    color = onPanelPrimary(),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "1. 每局限时90秒, 每道题会发给你4张数字牌(简单、普通难度都是1–9点;困难难度会混入10–13点,一律用阿拉伯数字标出,不是花牌),目标是用加减乘除把这4张牌一步步合并成一张24点的牌。",
                    style = PicoTheme.typography.bodySmall,
                    color = onPanelSecondary(),
                )
            }
        }
    }
}
