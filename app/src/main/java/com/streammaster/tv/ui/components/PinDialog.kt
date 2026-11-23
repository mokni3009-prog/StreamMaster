package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
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
 * K07 (prototype `.pin-dot` / `.pin-key` / `.pin-err`): dots are 62dp
 * circles with a 2.5px border; keys are 72dp high with an 18dp radius
 * and 28sp text (20sp for function keys) in a 3-column grid of 110dp
 * keys; the error line is always reserved (26dp high) so the pad does
 * not jump when an error appears.
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

    // .dlg-card shell (prototype): 720dp, padding 32×46, radius 30,
    // gold gradient, 2.5px border. Vertical padding is 32dp, not the
    // prototype's 40dp: at 40dp the dialog is ~727dp tall and the bottom
    // of "Annuler" is cut ~7dp on a 720p canvas (720dp high); at 32dp
    // it is ~711dp and fits (an invisible change on the reference).
    val cardShape = RoundedCornerShape(30.dp)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 720.dp).fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        0.0f to Color(0xFA221807),
                        1.0f to Color(0xFC0C0903)
                    ),
                    cardShape
                )
                .border(2.5.dp, BorderStrong, cardShape)
                .padding(horizontal = 46.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = title,
                color = GoldWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = GoldSoft,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Dots UI: 4 dots of 62dp, filled gold as digits are entered.
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(4) { i ->
                    val filled = i < entry.length
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .then(
                                if (filled) Modifier.background(GoldGradient)
                                else Modifier.background(Color(0x99000000))
                            )
                            .border(
                                2.5.dp,
                                if (filled) Color.White else BorderStrong,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (filled) {
                            Text(text = "•", color = GoldDeep, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // .pin-err: reserved 26dp line, shown only on error.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFB9A8),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Pad: 1–9, then clear / 0 / backspace (prototype order),
            // 3 columns of 110dp keys.
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear", "0", "back")
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
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
                            modifier = Modifier.width(110.dp)
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
    val shape = RoundedCornerShape(18.dp)
    var m = modifier
        .height(72.dp)
        .clip(shape)
        .background(Color(0x21E5C158))
        .border(2.dp, Color(0x59FFE89E), shape)
        // The focusRequester must come BEFORE tvFocus (which owns the
        // focusable): after it, the request lands on a phantom target
        // and the key shows no halo.
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .tvFocus()
        .clickable { onClick() }
    Box(modifier = m, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = GoldWhite,
            fontSize = if (isFunction) 20.sp else 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}
