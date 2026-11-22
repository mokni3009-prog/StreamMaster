package com.streammaster.tv.data.util

import java.text.Normalizer

/**
 * Normalizes titles / channel names for fuzzy matching: lowercase, no
 * accents, no punctuation, no quality tags (HD/FHD/4K/UHD/SD), no
 * "S01E01" markers, no "(year)" suffixes, single spaces.
 *
 * Used by series-link recording rules, EPG enrichment (program title ->
 * VOD catalog) and backup-URL grouping (same channel in several
 * playlists). Both sides of a comparison must go through this function,
 * so stripping a word can never break a match — only (rarely) merge two
 * different titles, which the caller guards against with the channel id.
 */
object TitleNormalizer {

    private val QUALITY_WORDS = setOf("hd", "fhd", "uhd", "4k", "8k", "sd")
    private val SEASON_EPISODE = Regex("s\\d{1,2}e\\d{1,2}")
    private val YEAR_SUFFIX = Regex("\\(\\d{4}\\)")
    private val NON_ALNUM = Regex("[^a-z0-9 ]")

    fun normalize(raw: String): String {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
        s = YEAR_SUFFIX.replace(s, " ")
        s = SEASON_EPISODE.replace(s, " ")
        s = NON_ALNUM.replace(s, " ")
        return s.split(" ")
            .filter { it.isNotBlank() && it !in QUALITY_WORDS }
            .joinToString(" ")
    }
}
