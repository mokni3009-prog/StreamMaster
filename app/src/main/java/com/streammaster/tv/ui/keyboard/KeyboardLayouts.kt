package com.streammaster.tv.ui.keyboard

/**
 * Key grids for the built-in on-screen keyboard (CONTRACT §8, SPEC §4.5).
 *
 * Pure data — no Compose, no Android dependencies — so the layouts can be
 * unit-tested and reused by [com.streammaster.tv.ui.components.BuiltInKeyboard]
 * and by the Search screen.
 *
 * Mirrors the prototype's `renderKeyboard()` rows:
 * - French AZERTY: full number row + letter rows + the prototype's accented
 *   row (Ê Ë Â Î Ï Ô Û Ù Ü Æ Œ). Shift (handled by the composable) uppercases
 *   the letters; the grids themselves store the lowercase base form.
 * - Arabic: all letters incl. ة ى ؤ ئ ء لا, plus a row of tashkeel
 *   (ً ٌ ٍ َ ُ ِ ّ ْ) and punctuation (، ؛ ؟).
 * - 123 mode: all digits 0–9 plus every symbol the prototype offered
 *   (! ? . , ; : @ # $ % & * ( ) - _ + = / \ | < > [ ] { } € £ ~),
 *   plus the Arabic punctuation/tashkeel row the prototype appended in
 *   123 mode when the language was Arabic.
 */
data class KeyboardLayout(val rows: List<List<String>>)

object KeyboardLayouts {

    /** French AZERTY, full layout. */
    val AZERTY_FR = KeyboardLayout(
        rows = listOf(
            // Number row (² & é " ' ( - è _ ç à ) =)
            listOf("²", "&", "é", "\"", "'", "(", "-", "è", "_", "ç", "à", ")", "="),
            // a z e r t y u i o p ^ $
            listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p", "^", "$"),
            // q s d f g h j k l m ù *
            listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m", "ù", "*"),
            // w x c v b n , ; : !
            listOf("w", "x", "c", "v", "b", "n", ",", ";", ":", "!"),
            // Accented letters (prototype row 4)
            listOf("ê", "ë", "â", "î", "ï", "ô", "û", "ü", "æ", "œ")
        )
    )

    /** Arabic, full layout: every letter + tashkeel + punctuation. */
    val ARABIC = KeyboardLayout(
        rows = listOf(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د", "ذ"),
            listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
            listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ"),
            // Tashkeel + Arabic punctuation
            listOf("ً", "ٌ", "ٍ", "َ", "ُ", "ِ", "ّ", "ْ", "،", "؛", "؟")
        )
    )

    /** 123 mode: all digits 0–9 + symbols. */
    val SYMBOLS_123 = KeyboardLayout(
        rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("!", "?", ".", ",", ";", ":", "@", "#", "$", "%"),
            listOf("&", "*", "(", ")", "-", "_", "+", "=", "/", "\\"),
            listOf("|", "<", ">", "[", "]", "{", "}", "€", "£", "~"),
            // Arabic punctuation / tashkeel (prototype appends these in 123 mode)
            listOf("،", "؛", "؟", "ً", "ٌ", "ٍ", "َ", "ُ", "ِ", "ّ", "ْ")
        )
    )
}
