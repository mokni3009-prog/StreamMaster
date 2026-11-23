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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
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
        fontSize = 48.sp, letterSpacing = LogoLetterSpacing, color = GoldWhite,
        shadow = TextShadowCrisp
    ),
    headlineLarge = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 36.sp, letterSpacing = LogoLetterSpacing, color = GoldWhite,
        shadow = TextShadowCrisp
    ),
    headlineMedium = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 28.sp, color = GoldWhite, shadow = TextShadowCrisp
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 22.sp, color = GoldWhite, shadow = TextShadowCrisp
    ),
    titleMedium = TextStyle(
        fontFamily = TitleFontFamily, fontWeight = FontWeight.Black,
        fontSize = 18.sp, color = GoldWhite, shadow = TextShadowCrisp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily, fontSize = 18.sp, color = GoldWhite,
        shadow = TextShadowCrisp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily, fontSize = 16.sp, color = GoldWhite,
        shadow = TextShadowCrisp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily, fontSize = 14.sp, color = TextMuted,
        shadow = TextShadowCrisp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        color = GoldWhite, shadow = TextShadowCrisp
    )
)

/**
 * Dark theme: vertical gradient BgTop -> BgBottom plus the two radial gold
 * glows from the prototype `#stage` (G07), always drawn — including on
 * low-RAM devices (the prototype shows them unconditionally):
 *  - 1300x750 at 80%/-10%, rgba(229,193,88,.22)
 *  - 1000x680 at 15%/110%, rgba(184,134,11,.20)
 * Sizes are fractions of the 1920x1080 stage (1300/1920, 750/1080, ...).
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
                        drawEllipticalGlow(
                            center = Offset(size.width * 0.80f, size.height * -0.10f),
                            glowSize = Size(size.width * 0.677f, size.height * 0.694f),
                            color = Color(0x38E5C158) // rgba(229,193,88,.22)
                        )
                        drawEllipticalGlow(
                            center = Offset(size.width * 0.15f, size.height * 1.10f),
                            glowSize = Size(size.width * 0.521f, size.height * 0.630f),
                            color = Color(0x33B8860B) // rgba(184,134,11,.20)
                        )
                    }
            ) { content() }
        }
    }
}

/** Radial gradient glow stretched into an ellipse of [glowSize] around [center]. */
private fun DrawScope.drawEllipticalGlow(center: Offset, glowSize: Size, color: Color) {
    val radius = maxOf(glowSize.width, glowSize.height) / 2f
    withTransform({
        scale(
            scaleX = glowSize.width / (2f * radius),
            scaleY = glowSize.height / (2f * radius),
            pivot = center
        )
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center, radius = radius
            ),
            center = center, radius = radius
        )
    }
}

/** Convenience scope for full-screen themed content. */
@Composable
fun ThemedScreen(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), content = content)
}
