package com.streammaster.tv.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R
import com.streammaster.tv.ui.keyboard.KeyboardLayouts
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus

/**
 * Built-in on-screen keyboard (CONTRACT §8, SPEC §4.5).
 *
 * The ONLY input method in the app: every text field is read-only and the
 * device keyboard is never used. One shared keyboard is used for login,
 * search, renaming, custom values (DNS / User-Agent) and the add-playlist
 * form — exactly like the prototype's `renderKeyboard()` / `handleKbKey()`.
 *
 * Callback contract — handle text in ONE place:
 * - [onKey] receives every typed character (letters, digits, symbols).
 * - [onAction] receives every action, including [KeyboardAction.Char] for
 *   the same characters. Callers that use [onKey] for text should ignore
 *   [KeyboardAction.Char] in [onAction] (see [KeyboardSheet] for the
 *   reference wiring); callers that prefer a single callback can use
 *   [onAction] alone and ignore [onKey].
 *
 * Shift is local state: French defaults to uppercase ON (like the
 * prototype, which starts with `shift: true`), Arabic has no case so the
 * Shift key is only shown for French. Language and mode are owned by the
 * caller and passed in as [language] / [mode]; the composable only emits
 * [KeyboardAction.SwitchLanguage] / [KeyboardAction.Switch123] — it never
 * mutates them itself.
 */
enum class KeyboardLanguage { FR, AR }

enum class KeyboardMode { LETTERS, SYMBOLS }

sealed interface KeyboardAction {
    data class Char(val c: String) : KeyboardAction
    object Backspace : KeyboardAction
    object Space : KeyboardAction
    object Shift : KeyboardAction
    object SwitchLanguage : KeyboardAction
    object Switch123 : KeyboardAction
    object Validate : KeyboardAction
    object Close : KeyboardAction
}

@Composable
fun BuiltInKeyboard(
    onKey: (String) -> Unit,
    onAction: (KeyboardAction) -> Unit,
    language: KeyboardLanguage,
    mode: KeyboardMode,
    /** K02: when non-null, a ✕ "clear all" key is shown in the function row. */
    onClearAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Prototype default: shift ON for French (uppercase first), OFF for Arabic.
    var shift by remember(language) { mutableStateOf(language == KeyboardLanguage.FR) }

    val layout = when (mode) {
        KeyboardMode.SYMBOLS -> KeyboardLayouts.SYMBOLS_123
        KeyboardMode.LETTERS -> when (language) {
            KeyboardLanguage.FR -> KeyboardLayouts.AZERTY_FR
            KeyboardLanguage.AR -> KeyboardLayouts.ARABIC
        }
    }

    fun displayChar(raw: String): String =
        if (shift && language == KeyboardLanguage.FR && mode == KeyboardMode.LETTERS) {
            raw.uppercase()
        } else {
            raw
        }

    fun emitChar(raw: String) {
        val c = displayChar(raw)
        onKey(c)
        onAction(KeyboardAction.Char(c))
    }

    // .kb-panel (prototype): gradient panel, 24dp radius, 2px border.
    val panelShape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    0.0f to Color(0xF01E1606),
                    1.0f to Color(0xF50E0A03)
                ),
                panelShape
            )
            .border(2.dp, BorderGold, panelShape)
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        layout.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { raw ->
                    KbKey(
                        label = displayChar(raw),
                        onClick = { emitChar(raw) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Function row: language | 123/ABC | Shift (FR only) | Space | ⌫ | ✕ (K02, only when onClearAll is passed) | Valider
        // (Prototype also had a ✕ "clear" key; KeyboardAction has no Clear
        // action, so clearing is done through [onClearAll] when the host
        // screen passes it, or with repeated ⌫ otherwise.)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KbKey(
                label = if (language == KeyboardLanguage.FR) "عربي" else "FR",
                onClick = { onAction(KeyboardAction.SwitchLanguage) },
                modifier = Modifier.weight(1.2f),
                isFunction = true
            )
            KbKey(
                label = if (mode == KeyboardMode.LETTERS) "123" else "ABC",
                onClick = { onAction(KeyboardAction.Switch123) },
                modifier = Modifier.weight(1f),
                isFunction = true
            )
            if (language == KeyboardLanguage.FR && mode == KeyboardMode.LETTERS) {
                KbKey(
                    label = "⇧",
                    onClick = {
                        shift = !shift
                        onAction(KeyboardAction.Shift)
                    },
                    modifier = Modifier.weight(0.8f),
                    isFunction = true,
                    isActive = shift
                )
            }
            KbKey(
                label = if (language == KeyboardLanguage.AR) "مسافة" else "ESPACE",
                onClick = { onAction(KeyboardAction.Space) },
                modifier = Modifier.weight(3f),
                isFunction = true
            )
            KbKey(
                label = "⌫",
                onClick = { onAction(KeyboardAction.Backspace) },
                modifier = Modifier.weight(1f),
                isFunction = true
            )
            if (onClearAll != null) {
                KbKey(
                    label = "✕",
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f),
                    isFunction = true
                )
            }
            KbKey(
                // R-S3: ic_check icon + "Valider" instead of the "✓ " prefix.
                label = "Valider",
                iconRes = R.drawable.ic_check,
                onClick = { onAction(KeyboardAction.Validate) },
                modifier = Modifier.weight(1.6f),
                isGold = true
            )
        }
    }
}

/** One key of the built-in keyboard (K01: `.kb2-key` in the prototype). */
@Composable
private fun KbKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFunction: Boolean = false,
    isActive: Boolean = false,
    isGold: Boolean = false,
    @DrawableRes iconRes: Int? = null
) {
    // Prototype `.kb2-key`: height 62px, radius 15, font 24px (18px for
    // function keys), vertical gradient rgba(255,232,158,.16→.06), 1.5px
    // border rgba(255,232,158,.35); gold keys use the gold gradient with a
    // #fff8d6 border; active keys (Shift on) use the gold gradient too.
    val shape = RoundedCornerShape(15.dp)
    val fg = when {
        isGold || isActive -> GoldDeep
        else -> GoldWhite
    }
    var m = modifier
        .height(62.dp)
        .clip(shape)
    m = when {
        isGold || isActive -> m.background(GoldGradient)
        isFunction -> m.background(Color(0x8C000000))
        else -> m.background(
            Brush.verticalGradient(
                Color(0x29FFE89E),
                Color(0x0FFFE89E)
            )
        )
    }
    m = if (isGold || isActive) {
        m.border(1.5.dp, Color(0xFFFFF8D6), shape)
    } else {
        m.border(1.5.dp, Color(0x59FFE89E), shape)
    }
    m = m
        .tvFocus()
        .clickable { onClick() }
    Box(
        modifier = m,
        contentAlignment = Alignment.Center
    ) {
        // R-S3: optional leading icon (the Valider key's ic_check).
        if (iconRes != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    color = fg,
                    fontSize = when {
                        isGold -> 20.sp
                        isFunction -> 18.sp
                        else -> 24.sp
                    },
                    fontWeight = if (isFunction || isGold) FontWeight.Bold else FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = label,
                color = fg,
                fontSize = when {
                    isGold -> 20.sp
                    isFunction -> 18.sp
                    else -> 24.sp
                },
                fontWeight = if (isFunction || isGold) FontWeight.Bold else FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Bottom-sheet wrapper around [BuiltInKeyboard] (CONTRACT §8).
 *
 * Shows the target field label, the current text (read-only display — the
 * keyboard is the only way to edit it), the keyboard itself, and
 * Validate / Retour buttons. Used by login fields, rename dialogs, custom
 * DNS / User-Agent values and the add-playlist form.
 *
 * Owns the text, language and mode state internally and reports the final
 * text through [onValidate]; [onClose] is called by the Retour button
 * (or a [KeyboardAction.Close] from the keyboard).
 */
@Composable
fun KeyboardSheet(
    fieldLabel: String,
    initialText: String = "",
    language: KeyboardLanguage = KeyboardLanguage.FR,
    onValidate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(initialText) }
    var lang by remember { mutableStateOf(language) }
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0A04), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = fieldLabel,
            color = Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        // Current text — read-only display box (never an editable TextField).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = text.ifEmpty { "…" },
                color = if (text.isEmpty()) TextMuted else GoldWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        BuiltInKeyboard(
            onKey = { c -> text += c },
            onAction = { action ->
                when (action) {
                    is KeyboardAction.Char -> Unit // already handled via onKey
                    KeyboardAction.Backspace -> text = text.dropLast(1)
                    KeyboardAction.Space -> text += " "
                    KeyboardAction.Shift -> Unit // handled inside BuiltInKeyboard
                    KeyboardAction.SwitchLanguage ->
                        lang = if (lang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR

                    KeyboardAction.Switch123 ->
                        mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS

                    KeyboardAction.Validate -> onValidate(text)
                    KeyboardAction.Close -> onClose()
                }
            },
            language = lang,
            mode = mode
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GoldButton(
                text = "Valider",
                onClick = { onValidate(text) },
                modifier = Modifier.weight(1f)
            )
            GoldButton(
                text = "Retour",
                onClick = onClose,
                modifier = Modifier.weight(1f),
                outlined = true
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
