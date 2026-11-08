package tech.illusion.space24.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

/**
 * Contract §2.2: the `hud` panel itself is 64dp tall and spans the window's width, anchored to the
 * top of `main`; the three groups (remaining / difficulty / score) sit inside that fixed strip with
 * a 24dp inset from the left/right edges, and a 1dp very-light bottom border marks the panel edge
 * (contract leaves the exact API unspecified — implemented here as a low-alpha `fillSecondary` line).
 */

/**
 * Contract §2 preamble + §2.2: `hudRemainingValue`/`hudScoreValue` must use tabular (monospace)
 * figures so the countdown's digit widths don't jitter every second. Verified via
 * `com.pico.spatial.ui:ui-text-android:1.0.5` sources: [com.pico.spatial.ui.design.Text]'s `style`
 * parameter is a real `androidx.compose.ui.text.TextStyle`, whose `fontFeatureSettings` survives
 * `Text`'s internal `style.merge(...)` call untouched (that overload never passes
 * `fontFeatureSettings`, so `TextStyle.fastMerge` keeps the base style's value), and Android's
 * paragraph layer wires it straight into `TextPaint.fontFeatureSettings`
 * (`FontFeatureSpan.android.kt`) — a real, functioning OpenType feature request, not inert metadata.
 */
private const val TABULAR_FIGURES = "tnum"

@Composable
fun Hud(
    remainingMillis: Long,
    score: Int,
    difficultyLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "剩余",
                    style = PicoTheme.typography.bodySmall,
                    color = onSurfaceSecondary(),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    text = formatRemainingMillis(remainingMillis),
                    style = PicoTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = onSurfacePrimary(),
                )
            }
            // Contract §2.2: the difficulty is a pill, not plain text.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(surfaceElevated())
                    .padding(horizontal = 16.dp, vertical = 5.dp),
            ) {
                Text(
                    text = difficultyLabel,
                    style = PicoTheme.typography.bodySmall,
                    color = onSurfacePrimary(),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "得分",
                    style = PicoTheme.typography.bodySmall,
                    color = onSurfaceSecondary(),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                Text(
                    text = score.toString(),
                    style = PicoTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = onSurfacePrimary(),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerOnSurface()),
        )
    }
}
