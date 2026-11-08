package tech.illusion.space24.content

import androidx.compose.runtime.Composable
import com.pico.spatial.ui.design.ButtonColors
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme

/**
 * The three non-primary button looks the design contract defines. Roles are taken verbatim from
 * `.spatialsdk/design-contract.md` §2.3/§2.4/§3 — do not substitute other roles:
 *  - [secondaryButtonColors]: operator row (enabled) and the four action buttons — fillSecondary / labelPrimary
 *  - [ghostButtonColors]:     result panel's 更换难度 — fillTertiary / labelPrimary
 *  - [dimmedButtonColors]:    operator row (disabled) and 题库准备中 — fillTertiary / labelSecondary
 * The primary look (开始游戏 / 再来一局 / selected operator) stays [ButtonDefaults.buttonColors].
 */
@Composable
fun secondaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = surfaceElevated(),
    contentColor = onSurfacePrimary(),
)

@Composable
fun ghostButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = surfaceElevated(),
    contentColor = onSurfacePrimary(),
)

@Composable
fun dimmedButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = surfaceElevated(),
    contentColor = onSurfaceDisabled(),
)

/**
 * Primary actions (开始游戏 / 再来一局 / the selected operator).
 *
 * NOT [ButtonDefaults.buttonColors]: that resolves to `fillPrimary` + `labelPrimaryLight`, and
 * `fillPrimary` is exactly the opaque colour this app now paints its base with — a default-coloured
 * button would be invisible against it. The accent is the one opaque role that separates from both
 * the base and the elevated surfaces.
 */
@Composable
fun primaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = surfaceAccent(),
    contentColor = onAccent(),
)
