package tech.illusion.space24.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.BasicSheet
import tech.illusion.space24.game.Puzzle

/**
 * Contract §2.4 format, `，` is a full-width comma:
 * `1 1 1 8 → 1 + 1 = 2，2 + 1 = 3，3 × 8 = 24`
 * A puzzle with no recorded steps (should not happen for a genuinely missed, solvable hand, but the
 * type allows it) just shows its cards with no arrow.
 */
fun missedHandLine(puzzle: Puzzle): String {
    val hand = puzzle.cards.joinToString(" ")
    if (puzzle.solutionSteps.isEmpty()) return hand
    return "$hand → " + puzzle.solutionSteps.joinToString("，") { it.display() }
}

/**
 * The `RESULT`-state overlay (contract §2.4), stacked over the still-resident (dimmed, read-only)
 * hud+board. Pure presentation: every value and callback comes from the caller, no internal state
 * and no game logic lives here.
 *
 * Element order follows the contract exactly: resultTitleText -> resultRecordBadge (only when
 * [isNewRecord]) -> 本局得分 label+value -> 历史最高（{难度}） label+value -> missed-hand list ->
 * 更换难度 + 再来一局 buttons. `resultRecordBadge` is only emitted (not merely hidden) when there is
 * no record — contract §2.4: "不是隐藏占位，是不存在".
 */
@Composable
fun ResultPanel(
    score: Int,
    bestScore: Int?,
    isNewRecord: Boolean,
    difficultyLabel: String,
    missedPuzzles: List<Puzzle>,
    onChangeDifficulty: () -> Unit,
    onRestart: () -> Unit,
) {
    // resultOverlayScrim (contract §2.4): the RESULT state is a genuine modal, so BasicSheet (the
    // low-level modal-dialog primitive — same shape as BasicAlertDialog, a real Android Dialog
    // window) now owns the dim scrim and the input blocking to the still-resident hud+board
    // underneath, replacing the hand-rolled fillMaxSize Box + pointer-consuming scrim this used to
    // be. There is no dismiss/cancel concept in this game (RESULT only exits via 更换难度 or
    // 再来一局), so outside-tap dismiss is a no-op rather than inventing a new callback.
    BasicSheet(
        onDismissRequest = {},
    ) {
        // resultPanelCard: fixed 560dp wide, 420dp minimum tall, radius 20, fillSecondary, 32dp padding.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(560.dp)
                .heightIn(min = 420.dp)
                .clip(RoundedCornerShape(20.dp))
                // Amber panel, set by the owner. The Material.Thick blend that used to sit here
                // is GONE on purpose: it washed the card's own fill toward silver (visible on
                // device), so the panel could not show the specified colour. The scheme's roles are
                // full-alpha, so the card occludes without it.
                .background(surfacePanel())
                .padding(32.dp),
        ) {
            Text(
                text = "时间到",
                style = PicoTheme.typography.titleLarge,
                color = onPanelPrimary(),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            if (isNewRecord) {
                // Contract §2.4: unlike every other pill in the doc, resultRecordBadge is typed
                // "药丸，描边" (stroked pill) with color role "passable（描边+文字）" and carries no
                // （背景）annotation — so it is a stroke, not a fill, unlike e.g. hudDifficultyPill.
                val badgeShape = RoundedCornerShape(999.dp)
                Text(
                    text = "新纪录",
                    style = PicoTheme.typography.bodySmall,
                    color = onPanelPrimary(),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(badgeShape)
                        .border(1.dp, onPanelPrimary(), badgeShape)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Text(
                text = "本局得分",
                style = PicoTheme.typography.bodySmall,
                color = onPanelSecondary(),
            )
            Text(
                text = score.toString(),
                style = PicoTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = onPanelPrimary(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = "历史最高（$difficultyLabel）",
                style = PicoTheme.typography.bodySmall,
                color = onPanelSecondary(),
            )
            Text(
                text = formatBestScore(bestScore),
                style = PicoTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = onPanelPrimary(),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            if (missedPuzzles.isNotEmpty()) {
                // resultMissedList: fixed 130dp visible height, scrolls beyond that, fillTertiary, radius 12.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceSunken())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (puzzle in missedPuzzles) {
                        Text(
                            text = missedHandLine(puzzle),
                            style = PicoTheme.typography.bodySmall,
                            color = onSurfacePrimary(),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            // NO Spacer(weight(1f)) here. A weighted child would make this Column try to expand
            // to fill its parent's height; the card should wrap its content (still >= 420dp via
            // heightIn) so the buttons sit directly under the list, not stretched to the sheet's
            // own bounds.
            // Contract §2.4: both result buttons are 999dp pills with 10,22 (vertical/horizontal)
            // content padding, matching every other Button call site in this codebase.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onChangeDifficulty,
                    colors = ghostButtonColors(),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 22.dp),
                ) {
                    Text(text = "更换难度", style = PicoTheme.typography.bodySmall)
                }
                Button(
                    onClick = onRestart,
                    colors = primaryButtonColors(),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 22.dp),
                ) {
                    Text(text = "再来一局", style = PicoTheme.typography.bodySmall)
                }
            }
        }
    }
}
