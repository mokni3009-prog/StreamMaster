package com.streammaster.tv.data.util

import java.text.Normalizer

/**
 * Natural-language search (§4.36.2): a rule-based intent layer that runs
 * BEFORE the keyword search. It understands plain sentences in French,
 * Arabic (Tunisian-friendly) and English — no on-device model, no API key.
 *
 * Examples:
 * - «ماتشات اليوم» / «matchs aujourd'hui» → [SearchIntent.MatchesToday]
 * - «أفلام أكشن جدد» / «films d'action récents» → Films("Action", newestFirst = true)
 * - «مسلسلات جديدة» → Series(genre = null, newestFirst = true)
 * - «شنوة نتفرج الليلة؟» / «un film ce soir» → Tonight(genre?)
 * - «أكشن» / «comédie» → Films(genre, newestFirst = false)
 * - anything else → Keyword(raw query), i.e. the old LIKE search.
 *
 * Genre names returned are the canonical French labels used by the
 * providers' categories (e.g. "Action", "Comédie"), so they match
 * [com.streammaster.tv.data.model.Film.genre] / Series.genre directly.
 * "Newest" is served by the repository's getByGenreNewest queries (year,
 * then sync order); see ContentRepository.searchFilmsByGenre.
 */
sealed interface SearchIntent {
    /** Today's matches — the UI renders them from the Match Center feed
     *  (ESPN), not from the catalog; SearchViewModel returns no rows. */
    data object MatchesToday : SearchIntent

    /** Films of [genre] (null = any), newest first when [newestFirst]. */
    data class Films(val genre: String?, val newestFirst: Boolean) : SearchIntent

    /** Series of [genre] (null = any), newest first when [newestFirst]. */
    data class Series(val genre: String?, val newestFirst: Boolean) : SearchIntent

    /** "What to watch tonight" — newest films + series, optionally
     *  restricted to [genre]. */
    data class Tonight(val genre: String?) : SearchIntent

    /** Plain keyword search (the old behaviour). */
    data class Keyword(val query: String) : SearchIntent
}

object NaturalSearchParser {

    /** keyword (lowercase, no diacritics) -> canonical genre label. */
    private val GENRE_KEYWORDS: Map<String, String> = mapOf(
        "action" to "Action",
        "اكشن" to "Action",
        "كوميديا" to "Comédie",
        "comedie" to "Comédie",
        "comedy" to "Comédie",
        "كوميدي" to "Comédie",
        "drame" to "Drame",
        "دراما" to "Drame",
        "horreur" to "Horreur",
        "رعب" to "Horreur",
        "romance" to "Romance",
        "رومانسي" to "Romance",
        "thriller" to "Thriller",
        "اثارة" to "Thriller",
        "تشويق" to "Thriller",
        "science fiction" to "Science-fiction",
        // LG-10: "science-fiction" / "sci-fi" can never match — normalize()
        // turns "-" into a space before matching, so the keys must use
        // spaces too.
        "sci fi" to "Science-fiction",
        "خيال علمي" to "Science-fiction",
        "animation" to "Animation",
        "كرتون" to "Animation",
        "انيميشن" to "Animation",
        "documentaire" to "Documentaire",
        "وثائقي" to "Documentaire",
        "famille" to "Famille",
        "عائلي" to "Famille",
        "aventure" to "Aventure",
        "مغامرة" to "Aventure",
        "crime" to "Crime",
        "جريمة" to "Crime",
        "guerre" to "Guerre",
        "حرب" to "Guerre",
        "western" to "Western",
        "fantastique" to "Fantastique",
        "فانتازيا" to "Fantastique",
        "sport" to "Sport",
        "رياضة" to "Sport",
        "musique" to "Musique",
        "موسيقى" to "Musique"
    )

    private val MATCH_WORDS = listOf(
        "match", "matchs", "ماتش", "ماتشات", "مباراة", "مباريات", "مبارة",
        "football", "كرة القدم", "foot", "كورة"
    )

    private val TONIGHT_WORDS = listOf(
        "ce soir", "tonight", "الليلة", "ليلة", "maintenant", "توة", "توا",
        "now", "شنوة نتفرج", "quoi regarder"
    )

    private val NEW_WORDS = listOf(
        "nouveau", "nouveaux", "nouvelle", "nouvelles", "récent", "récents",
        "récente", "récentes", "dernier", "derniers", "dernière", "dernières",
        "جديد", "جديدة", "جدد", "new", "latest"
    )

    private val FILM_WORDS = listOf("film", "films", "فيلم", "أفلام", "افلام", "movie", "movies")
    private val SERIES_WORDS = listOf("série", "séries", "serie", "series", "مسلسل", "مسلسلات")

    /** Lowercase, no diacritics (Latin accents AND Arabic tashkeel),
     *  punctuation -> spaces, single spaces. Unlike TitleNormalizer this
     *  keeps Arabic letters. */
    fun normalize(raw: String): String {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
        s = s.replace(Regex("[^a-z0-9\\u0600-\\u06FF ]"), " ")
        return s.split(" ").filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Canonical genre for a normalized query, or null. */
    fun detectGenre(normalized: String): String? {
        val padded = " $normalized "
        for ((keyword, genre) in GENRE_KEYWORDS) {
            if (padded.contains(" $keyword ")) return genre
        }
        return null
    }

    private fun containsAny(normalized: String, words: List<String>): Boolean {
        val padded = " $normalized "
        // LG-09: whole words only — the old `normalized.contains(it)` made
        // "knowledge" match "now" (Tonight) and "matchbox" match MatchesToday.
        return words.any { padded.contains(" $it ") }
    }

    fun parseIntent(rawQuery: String): SearchIntent {
        val query = rawQuery.trim()
        if (query.isEmpty()) return SearchIntent.Keyword("")
        val n = normalize(query)
        // 1. Matches first: «ماتشات اليوم», «matchs aujourd'hui», «ماتشات الليلة».
        if (containsAny(n, MATCH_WORDS)) return SearchIntent.MatchesToday
        val genre = detectGenre(n)
        val isNew = containsAny(n, NEW_WORDS)
        // 2. Tonight: «الليلة», «ce soir», «توة», «شنوة نتفرج الليلة؟».
        if (containsAny(n, TONIGHT_WORDS)) return SearchIntent.Tonight(genre)
        // 3. Explicit kind + (genre and/or "new").
        val isFilm = containsAny(n, FILM_WORDS)
        val isSeries = containsAny(n, SERIES_WORDS)
        if (isFilm) return SearchIntent.Films(genre, isNew)
        if (isSeries) return SearchIntent.Series(genre, isNew)
        // 4. Bare genre («أكشن», «comédie») -> films of that genre.
        if (genre != null) return SearchIntent.Films(genre, isNew)
        // 5. Bare "new" («جدد», «nouveautés») -> newest films.
        if (isNew) return SearchIntent.Films(null, true)
        // 6. Fallback: plain keyword search.
        return SearchIntent.Keyword(query)
    }
}
