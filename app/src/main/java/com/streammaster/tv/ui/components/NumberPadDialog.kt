package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import androidx.compose.runtime.LaunchedEffect

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

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.5f)
                .background(Color(0xFF14100A), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = GoldWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
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

            // Big display of the number being typed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(12.dp))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.ifEmpty { "—" },
                    color = GoldWhite,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
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

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear", "0", "back")
            keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEachIndexed { colIndex, key ->
                        val requester = if (rowIndex == 0 && colIndex == 0) firstKeyFocus else null
                        var m = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (key == "clear" || key == "back") Color(0xFF2A2113) else CardSurface)
                            .tvFocus()
                            .clickable {
                                error = null
                                when (key) {
                                    "clear" -> value = ""
                                    "back" -> value = value.dropLast(1)
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
                        if (requester != null) m = m.focusRequester(requester)
                        Box(modifier = m, contentAlignment = Alignment.Center) {
                            Text(
                                text = when (key) {
                                    "clear" -> "✕"
                                    "back" -> "⌫"
                                    else -> key
                                },
                                color = GoldWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
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
