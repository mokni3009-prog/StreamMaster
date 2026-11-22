package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import kotlinx.coroutines.delay

/**
 * 4-digit parental-control PIN pad dialog (CONTRACT §8, SPEC §4.7).
 *
 * Mirrors the prototype's `renderPinPad()` / `pinDigit()`:
 * digits 1–9, then clear (✕), 0, backspace (⌫); dots UI showing the
 * entered digits; [onComplete] fires automatically once 4 digits are
 * entered (after a short 180 ms beat, like the prototype).
 *
 * The [title] distinguishes the two uses — setup ("Définir le code PIN")
 * vs verify ("Entrez le code PIN"). The two-step setup flow (enter, then
 * confirm) and the wrong-PIN error are the caller's job (Settings screen):
 * pass [errorMessage] (e.g. "Code incorrect") to show it, and on mismatch
 * the caller simply re-opens the dialog. [onCancel] closes without a PIN.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String = "",
    errorMessage: String? = null,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entry by remember { mutableStateOf("") }
    val firstKeyFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstKeyFocus.requestFocus()
        } catch (_: Exception) {
        }
    }
    // Auto-complete at 4 digits (prototype: setTimeout(handlePinComplete, 180)).
    LaunchedEffect(entry.length) {
        if (entry.length == 4) {
            delay(180)
            onComplete(entry)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.55f)
                .background(Color(0xFF14100A), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                color = GoldWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Dots UI: 4 dots, filled gold as digits are entered.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (i < entry.length) Gold else Color(0xFF3A2F16)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i < entry.length) {
                            Text(text = "•", color = GoldDeep, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Danger,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // Pad: 1–9, then clear / 0 / backspace (prototype order).
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear", "0", "back")
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEachIndexed { colIndex, key ->
                        val requester = if (rowIndex == 0 && colIndex == 0) firstKeyFocus else null
                        PinKey(
                            label = when (key) {
                                "clear" -> "✕"
                                "back" -> "⌫"
                                else -> key
                            },
                            isFunction = key == "clear" || key == "back",
                            focusRequester = requester,
                            onClick = {
                                when (key) {
                                    "clear" -> entry = ""
                                    "back" -> entry = entry.dropLast(1)
                                    else -> if (entry.length < 4) entry += key
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            GoldButton(
                text = "Annuler",
                onClick = onCancel,
                outlined = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    isFunction: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var m = modifier
        .height(60.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (isFunction) Color(0xFF2A2113) else CardSurface)
        .tvFocus()
        .clickable { onClick() }
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    Box(modifier = m, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = GoldWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
