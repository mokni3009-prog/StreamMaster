package com.streammaster.tv.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R

/**
 * Typography — CONTRACT §6 / SPEC §9.
 * Body: Carlito (OFL, metrically Calibri-compatible — the closest free match
 * to Segoe UI). Titles/logo: Gelasio (OFL, metric-compatible with Georgia).
 * Both are bundled in res/font/; Segoe UI / Georgia themselves are
 * proprietary and are never copied.
 * NOTE: neither family ships a Black (900) static cut — the Bold (700) cut
 * is the closest available, so FontWeight.Black resolves to Bold.
 */
val BodyFontFamily: FontFamily = FontFamily(
    Font(R.font.carlito_regular, FontWeight.Normal),
    Font(R.font.carlito_bold, FontWeight.Bold)
)
val TitleFontFamily: FontFamily = FontFamily(
    Font(R.font.gelasio_regular, FontWeight.Normal),
    Font(R.font.gelasio_bold, FontWeight.Bold)
)

/**
 * G03 — crisp text shadow from the prototype (--text-crisp):
 * `0 2px 6px rgba(0,0,0,.98), 0 0 2px #000`. Compose's Shadow carries a
 * single layer, so the dominant `0 2px 6px` layer is used (the `0 0 2px`
 * halo is subsumed by the 6px blur).
 */
val TextShadowCrisp = Shadow(
    color = Color(0xFA000000), // rgba(0,0,0,.98)
    offset = Offset(0f, 2f),
    blurRadius = 6f
)

val LogoLetterSpacing = 1.2.sp
val LogoFontWeight: FontWeight = FontWeight.Black
val TitleFontWeight: FontWeight = FontWeight.Black
