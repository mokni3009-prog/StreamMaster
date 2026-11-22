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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * CONTRACT §6 focus behaviour (SPEC §9): focused element scales to 1.03,
 * gets a 2.dp GoldWhite border and a gold glow.
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
                    .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Gold, spotColor = Gold)
                    .border(2.dp, GoldWhite, RoundedCornerShape(20.dp))
            } else {
                Modifier.border(2.dp, BorderGold, RoundedCornerShape(20.dp))
            }
        )
}

/** Focus variant without the default gold border (for rows / list items that draw their own). */
@Composable
fun Modifier.tvFocusBorderless(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .focusable()
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = if (focused) 1.03f else 1f; scaleY = if (focused) 1.03f else 1f }
        .then(
            if (focused) Modifier.border(2.dp, GoldWhite, RoundedCornerShape(20.dp))
            else Modifier
        )
}

val CardRadius = 20.dp
val CardPadding = 16.dp
