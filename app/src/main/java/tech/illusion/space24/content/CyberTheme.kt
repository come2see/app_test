package tech.illusion.space24.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pico.spatial.ui.design.ColorScheme
import com.pico.spatial.ui.design.defaultColorScheme

/**
 * "霓虹街区" — Space24's cyber colour scheme, approved by the owner on 2026-08-18 from the three
 * directions on the theme canvas. Near-black blue base, cyan primary accent, neon semantics.
 *
 * This is the SANCTIONED way to theme SpatialUI: `PicoTheme(colorScheme = …)` takes a full
 * [ColorScheme], so every `PicoTheme.colorScheme.*` read across the app resolves to these values
 * with no call-site changes. Two roles are deliberately redefined away from their stock meaning,
 * because the stock scheme assumes a LIGHT window:
 *  - `labelPrimary` ships as opaque BLACK; here it is the bright text, since every surface is dark.
 *  - `fillPrimary` is both the window base and the "on accent" colour — dark text on cyan.
 */
@Composable
fun cyberColorScheme(): ColorScheme = defaultColorScheme(
    // Surfaces, darkest to lightest.
    fillTertiary = Color(0xFF070911),   // sunken wells: the missed-hand list
    fillPrimary = Color(0xFF0B0E17),    // the window base
    fillLight = Color(0xFF161B2B),      // elevated: cards, result panel, pills, segment track
    fillSecondary = Color(0xFF1F2740),  // mid surface, one step above elevated
    // Text, brightest to dimmest.
    labelPrimary = Color(0xFFE8F4FF),
    labelPrimaryLight = Color(0xFFE8F4FF),
    labelSecondary = Color(0xFF8FA3C4),
    labelTertiary = Color(0xFF5D6E8C),
    labelQuaternary = Color(0xFF3E4B63),
    // Accent and semantics.
    interaction = Color(0xFF00E5FF),    // primary buttons, selected card, selected operator
    passable = Color(0xFF39FF88),       // 答对！+1, 新纪录
    error = Color(0xFFFF3B6B),          // 不是 24 / 除不尽 / 已无解
    alert = Color(0xFFFFB020),          // unused by this app today
    dividerLine = Color(0xFF24304A),
    lightenHover = Color(0x14FFFFFF),
    lightenPressed = Color(0x24FFFFFF),
)

/**
 * The result panel's own surface — amber, set by the owner on 2026-08-18.
 *
 * Not a `ColorScheme` role: the scheme has a fixed set of 16 and none of them means "the modal
 * panel's fill". It lives here so this file stays the only place in the app carrying a colour
 * literal. Note it is BRIGHT, so everything drawn on it uses the dark on-panel text below.
 */
val CyberPanelSurface = Color(0xFFFFB020)
