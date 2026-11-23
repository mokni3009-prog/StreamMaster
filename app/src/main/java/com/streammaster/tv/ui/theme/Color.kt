package com.streammaster.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Gold identity — exact values from SPEC §9 / CONTRACT §6. */
val Gold = Color(0xFFE5C158)
val GoldBright = Color(0xFFFFE89E)
val GoldWhite = Color(0xFFFFFDF4)
val GoldSoft = Color(0xFFF3D98B)
val GoldDark = Color(0xFFB38412)
val GoldDeep = Color(0xFF231804)
val BgTop = Color(0xFF120D05)
val BgBottom = Color(0xFF080603)
val BorderGold = Color(0x5CFFE89E) // rgba(255,232,158,.36) — alpha 0x5C
val BorderStrong = Color(0xC7FFF4C8) // rgba(255,244,200,.78) — alpha 0xC7
val CardSurface = Color(0xFF1A1408)
val CardSurfaceMuted = Color(0xFF14100A)
val TextMuted = Color(0xFFF3D98B) // G12: soft gold for secondary text (was grey-beige #C9BFA6)
val TextDim = Color(0xFF8A7F63)
val Danger = Color(0xFFFF6A4D) // G13: prototype danger (was Material #E57373)
val Success = Color(0xFF81C784)

/**
 * SPEC §9 gold gradient (prototype --gold-grad):
 * linear-gradient(135deg, #fff6cc, #ecc75c 38%, #b8860b 75%, #f7dc88).
 * Used for primary buttons and the active sidebar item.
 */
val GoldGradient: Brush = Brush.linearGradient(
    0.0f to Color(0xFFFFF6CC),
    0.38f to Color(0xFFECC75C),
    0.75f to Color(0xFFB8860B),
    1.0f to Color(0xFFF7DC88)
)
