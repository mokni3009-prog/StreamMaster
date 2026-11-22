package com.streammaster.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.streammaster.tv.util.DeviceCaps

/** True when the device is low-RAM; agent 2/3/4 use it to disable animations and shrink caches. */
val LocalLowRam = compositionLocalOf { false }

private val StreamMasterColors = darkColorScheme(
    primary = Gold,
    onPrimary = GoldDeep,
    secondary = GoldSoft,
    onSecondary = GoldDeep,
    tertiary = GoldBright,
    background = BgBottom,
    onBackground = GoldWhite,
    surface = CardSurface,
    onSurface = GoldWhite,
    surfaceVariant = CardSurfaceMuted,
    onSurfaceVariant = TextMuted,
    outline = BorderGold,
    error = Danger
)

private val StreamMasterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 48.sp, letterSpacing = LogoLetterSpacing, color = GoldWhite
    ),
    headlineLarge = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 36.sp, letterSpacing = LogoLetterSpacing, color = GoldWhite
    ),
    headlineMedium = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 28.sp, color = GoldWhite
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 22.sp, color = GoldWhite
    ),
    titleMedium = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 18.sp, color = GoldWhite
    ),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontSize = 18.sp, color = GoldWhite),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontSize = 16.sp, color = GoldWhite),
    bodySmall = TextStyle(fontFamily = BodyFontFamily, fontSize = 14.sp, color = TextMuted),
    labelLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GoldWhite)
)

/**
 * Dark theme: vertical gradient BgTop -> BgBottom plus two radial gold glows
 * (top-left and bottom-right corners), matching the 1920x1080 prototype stage.
 */
@Composable
fun StreamMasterTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lowRam = DeviceCaps.isLowRam(context)
    CompositionLocalProvider(LocalLowRam provides lowRam) {
        MaterialTheme(colorScheme = StreamMasterColors, typography = StreamMasterTypography) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
                    .drawBehind {
                        if (!lowRam) {
                            drawRadialGlow(Offset(size.width * 0.08f, size.height * 0.02f), size.minDimension * 0.55f)
                            drawRadialGlow(Offset(size.width * 0.95f, size.height * 0.98f), size.minDimension * 0.5f)
                        }
                    }
            ) { content() }
        }
    }
}

private fun DrawScope.drawRadialGlow(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x3DE5C158), Color(0x00E5C158)),
            center = center, radius = radius
        ),
        center = center, radius = radius
    )
}

/** Convenience scope for full-screen themed content. */
@Composable
fun ThemedScreen(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), content = content)
}
