package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus

/**
 * Channel-number assignment dialog (CONTRACT §8, SPEC §4.2).
 *
 * Mirrors the prototype's `openNumDialog()` / `numKeyPress()` /
 * `confirmNum()`: digits 0–9 + clear + confirm, at most 3 digits, value
 * must be 1–100, empty confirms as "remove the number" ([onConfirm] gets
 * null). Shows the current number and the hint text. An out-of-range
 * value is rejected inline (the dialog stays open with an error message,
 * like the prototype's toast) instead of confirming.
 *
 * K08 (prototype `dlg-num` / `.num-key`): the number display is 44sp;
 * the pad keys have 16dp vertical padding and 24sp text; the 12th key
 * is a gold OK key that confirms.
 *
 * The caller (Manage screen) owns uniqueness: if the number is already
 * assigned to another channel it must refuse it and say which channel
 * holds it — that check needs repository data the dialog does not have.
 */
@Composable
fun NumberPadDialog(
    title: String = "Attribuer un numéro",
    currentNumber: Int? = null,
    hint: String = "Numéro de 1 à 100. Laisser vide pour retirer le numéro.",
    onConfirm: (Int?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var value by remember { mutableStateOf(currentNumber?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val firstKeyFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstKeyFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun confirm() {
        if (value.isEmpty()) {
            onConfirm(null) // empty = remove the number
            return
        }
        val n = value.toIntOrNull()
        if (n == null || n < 1 || n > 100) {
            error = "Numéro invalide : entre 1 et 100."
            return
        }
        onConfirm(n)
    }

    // .dlg-card shell (prototype): 720dp, padding 40×46, radius 30,
    // gold gradient, 2.5px border.
    val cardShape = RoundedCornerShape(30.dp)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .width(720.dp)
                .background(
                    Brush.linearGradient(
                        0.0f to Color(0xFA221807),
                        1.0f to Color(0xFC0C0903)
                    ),
                    cardShape
                )
                .border(2.5.dp, BorderStrong, cardShape)
                .padding(horizontal = 46.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = GoldWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Numéro actuel : ${currentNumber?.toString() ?: "Aucun"}",
                color = Gold,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = hint,
                color = TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            // Big display of the number being typed (44sp, like .num-display).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.ifEmpty { "—" },
                    color = GoldWhite,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = Danger,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            // .num-pad: 3 columns, 10dp gaps, 16dp key padding, 24sp text.
            // Prototype keys: 1–9, clear ("C"), 0, gold OK.
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear", "0", "ok")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                keys.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEachIndexed { colIndex, key ->
                            val requester = if (rowIndex == 0 && colIndex == 0) firstKeyFocus else null
                            val isOk = key == "ok"
                            val keyShape = RoundedCornerShape(14.dp)
                            var m = Modifier
                                .weight(1f)
                                .padding(vertical = 16.dp)
                                .clip(keyShape)
                                .then(
                                    if (isOk) Modifier.background(GoldGradient)
                                    else Modifier.background(Color(0x73000000))
                                )
                                .border(
                                    2.dp,
                                    if (isOk) Color(0xFFFFF8D6) else Color(0x59FFE89E),
                                    keyShape
                                )
                                // The focusRequester must come BEFORE
                                // tvFocus (which owns the focusable).
                                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                                .tvFocus()
                                .clickable {
                                    error = null
                                    when (key) {
                                        "clear" -> value = ""
                                        "ok" -> confirm()
                                        else -> {
                                            if (value.length >= 3) return@clickable
                                            val candidate = value + key
                                            // Reject > 100 immediately (prototype behaviour).
                                            if ((candidate.toIntOrNull() ?: 0) > 100) {
                                                error = "Numéro invalide : entre 1 et 100."
                                                return@clickable
                                            }
                                            value = candidate
                                        }
                                    }
                                }
                            Box(modifier = m, contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (key) {
                                        "clear" -> "C"
                                        "ok" -> "OK"
                                        else -> key
                                    },
                                    color = if (isOk) GoldDeep else GoldWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoldButton(
                    text = "Valider",
                    onClick = ::confirm,
                    modifier = Modifier.weight(1f)
                )
                GoldButton(
                    text = "Annuler",
                    onClick = onCancel,
                    outlined = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
