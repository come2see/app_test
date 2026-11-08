package tech.illusion.space24.content

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pico.spatial.ui.design.PicoTheme

/**
 * Semantic aliases over [cyberColorScheme]. Each one is now a straight role read — the alpha
 * juggling this file used to carry is gone, because the app supplies its own [ColorScheme] instead
 * of bending the stock light-theme roles to a dark UI.
 */

/** The opaque window base, and the "on accent" colour (dark text on cyan). */
@Composable
fun surfaceBase(): Color = PicoTheme.colorScheme.fillPrimary

/** Elevated surfaces sitting on [surfaceBase]: cards, panels, pills, segment tracks. */
@Composable
fun surfaceElevated(): Color = PicoTheme.colorScheme.fillLight

/** Sunken wells cut into a surface: the missed-hand list. */
@Composable
fun surfaceSunken(): Color = PicoTheme.colorScheme.fillTertiary

/** Selected / primary-action surface. */
@Composable
fun surfaceAccent(): Color = PicoTheme.colorScheme.interaction

/** Text on [surfaceAccent] — the accent is bright, so its content is the dark base. */
@Composable
fun onAccent(): Color = PicoTheme.colorScheme.fillPrimary

/** Primary text on the base and elevated surfaces. */
@Composable
fun onSurfacePrimary(): Color = PicoTheme.colorScheme.labelPrimary

/** Secondary text: labels and captions. */
@Composable
fun onSurfaceSecondary(): Color = PicoTheme.colorScheme.labelSecondary

/** Disabled text. */
@Composable
fun onSurfaceDisabled(): Color = PicoTheme.colorScheme.labelQuaternary

/** Hairline dividers. */
@Composable
fun dividerOnSurface(): Color = PicoTheme.colorScheme.dividerLine

/** The result panel's surface. Bright amber — pair only with the on-panel colours below. */
@Composable
fun surfacePanel(): Color = CyberPanelSurface

/** Primary text on [surfacePanel]. */
@Composable
fun onPanelPrimary(): Color = PicoTheme.colorScheme.fillPrimary

/** Secondary text on [surfacePanel]: labels subordinate to the value they describe. */
@Composable
fun onPanelSecondary(): Color = PicoTheme.colorScheme.fillPrimary.copy(alpha = 0.65f)
