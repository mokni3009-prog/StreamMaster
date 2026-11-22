package com.streammaster.tv.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography — CONTRACT §6 / SPEC §9.
 * Body: SansSerif (Segoe UI is unavailable on Android; Default sans is the
 * closest system equivalent). Titles/logo: Serif, Black 900, letterSpacing 1.2sp.
 */
val BodyFontFamily: FontFamily = FontFamily.SansSerif
val TitleFontFamily: FontFamily = FontFamily.Serif

val LogoLetterSpacing = 1.2.sp
val LogoFontWeight: FontWeight = FontWeight.Black
val TitleFontWeight: FontWeight = FontWeight.Black
