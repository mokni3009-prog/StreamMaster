package com.streammaster.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Global UI scaling (2026-11-23): every screen was designed against a
 * 1920×1080dp reference canvas, so on a smaller / lower-density TV the
 * fixed dp sizes overflow the screen. [ScaledContent] overrides
 * [LocalDensity] with a uniformly scaled density, so ALL measurements —
 * dp layout sizes, sp text (sp scales with density × fontScale), focus
 * hit areas, Dialog windows (they inherit CompositionLocals) and the
 * video surface — shrink together, with no per-screen math.
 *
 * Scale = min(screenW / 1920, screenH / 1080), clamped to [MIN_SCALE, 1].
 * The reference is 1920×1080dp, NOT 960×540dp (see EpgScreen: the grid
 * geometry comment assumes 240 + 10 + 12×100 + 11×10 = 1560dp). On a
 * standard 1080p xhdpi TV (960×540dp) the scale is 0.5, which restores
 * the intended pixel sizes (a 232dp poster at density 2.0 is drawn at
 * 464px; at scale 0.5 it is back to 232px), and on a 1080p mdpi TV the
 * scale is 1.0 so the UI is pixel-identical to the unscaled design.
 * MIN_SCALE = 0.5f: below that, text would become unreadable
 * (16sp → 5.3sp at 0.333), so tiny canvases (e.g. 640×360dp) are scaled
 * by 0.5 and the per-screen flexible layouts below absorb the rest.
 *
 * Deliberately NOT graphicsLayer scale: that changes neither measurement
 * nor placement (focus/scroll keep unscaled coordinates, Dialogs are
 * separate windows, AndroidView ignores it). Deliberately NOT
 * WindowMetricsCalculator (needs API 30; minSdk is 23) — LocalConfiguration
 * is enough for a full-screen TV app.
 *
 * Caveats: LocalConfiguration reports the physical screen, not the
 * window, so in system PiP (a small window) the scale stays that of the
 * full screen — harmless because PiP shows the video surface only.
 * The density override also scales the Theme glows (they are fractions
 * of the canvas) consistently.
 */
private const val DESIGN_WIDTH_DP = 1920f
private const val DESIGN_HEIGHT_DP = 1080f
private const val MIN_SCALE = 0.5f

@Composable
fun ScaledContent(content: @Composable () -> Unit) {
    val scale = uiScale()
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = base.density * scale,
            fontScale = base.fontScale
        )
    ) { content() }
}

/**
 * Current UI scale (same computation as [ScaledContent]), exposed for
 * screens that need size-aware behavior — e.g. the Login lift cap, which
 * must shrink on small canvases so the logo stays on-screen.
 */
@Composable
fun uiScale(): Float {
    val config = LocalConfiguration.current
    return minOf(
        config.screenWidthDp / DESIGN_WIDTH_DP,
        config.screenHeightDp / DESIGN_HEIGHT_DP
    ).coerceIn(MIN_SCALE, 1f)
}
