package com.streammaster.tv.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * CONTRACT §6 focus behaviour (SPEC §9) — G01/G02 (strict comparison):
 * focused element scales to 1.03 and gets the prototype halo
 * (`[data-f]:focus`: 4px #fff7d1 ring + 38px rgba(255,232,158,.85) gold
 * glow + 18/40px rgba(0,0,0,.95) drop shadow).
 * Unfocused elements get a TRANSPARENT 2.dp border (same width, so the
 * layout size stays stable) — no permanent gold border (G02).
 * Uses androidx.tv.foundation focus handling via the standard focusable +
 * onFocusChanged chain (tv-foundation builds on the same focus system).
 * Card radius is 20.dp everywhere; poster cards carry a 2.dp gold border.
 *
 * This is a @Composable Modifier extension so each call site owns its
 * remembered focus flag.
 */
@Composable
fun Modifier.tvFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .focusable()
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = if (focused) 1.03f else 1f; scaleY = if (focused) 1.03f else 1f }
        .then(
            if (focused) {
                Modifier
                    .shadow(
                        38.dp, RoundedCornerShape(20.dp),
                        ambientColor = Color(0xD9FFE89E), // rgba(255,232,158,.85) gold glow
                        spotColor = Color(0xF2000000) // rgba(0,0,0,.95) drop shadow
                    )
                    .border(4.dp, Color(0xFFFFF7D1), RoundedCornerShape(20.dp))
            } else {
                // G02: transparent border — keeps the size identical, no gold ring.
                Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(20.dp))
            }
        )
}

/** Focus variant without the default gold border (for rows / list items that draw their own).
 * R-B3: on focus it now carries the same halo as [tvFocus] — 4dp #FFF7D1
 * ring + 38dp gold glow + black drop shadow — keeping the 1.03 scale;
 * unfocused it stays borderless (transparent). */
@Composable
fun Modifier.tvFocusBorderless(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .focusable()
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = if (focused) 1.03f else 1f; scaleY = if (focused) 1.03f else 1f }
        .then(
            if (focused) {
                Modifier
                    .shadow(
                        38.dp, RoundedCornerShape(20.dp),
                        ambientColor = Color(0xD9FFE89E), // rgba(255,232,158,.85) gold glow
                        spotColor = Color(0xF2000000) // rgba(0,0,0,.95) drop shadow
                    )
                    .border(4.dp, Color(0xFFFFF7D1), RoundedCornerShape(20.dp))
            } else {
                Modifier
            }
        )
}

val CardRadius = 20.dp
val CardPadding = 16.dp
