package com.streammaster.tv.data.model

/**
 * Kids mode (§4.36.3, smart kids classification): content is classified
 * from its category, description and age rating — not from the bouquet
 * name alone (the old keyword-on-name heuristic misfired both ways:
 * adult content in a "Family" bouquet passed, kids cartoons in a
 * generically-named bouquet were hidden).
 *
 * [isKidCategory] keeps the old bouquet-name behaviour for callers that
 * only have a name. [isKidContent] is what kids mode must use: a title
 * is kid content only when nothing adult is detected AND (a kid signal
 * is present in the category/description OR the age rating says so).
 * When in doubt the answer is false (kids mode is a whitelist).
 *
 * [rating] is the AGE classification string when the provider supplies
 * one ("G", "PG", "7+", "12+", "16+", "18+", "TP", "Tous publics",
 * "NC-17", "R"...), NOT the 0-10 provider score — a score cannot say
 * whether a title is for children.
 */
object KidsFilter {
    private val KID_WORDS = listOf(
        "kid", "kids", "enfant", "enfants", "animation", "cartoon", "cartoons",
        "dessin", "dessins", "famille", "family", "junior", "disney", "nickelodeon",
        "pixar", "baby", "bébé", "jeunesse", "child", "children", "toons"
    )

    /** Words that disqualify a title from kids mode, wherever they appear
     *  (category or description). Checked BEFORE the kid words, so an
     *  "Action / violence" title never passes because it also says
     *  "family". */
    private val ADULT_WORDS = listOf(
        "horreur", "horror", "violence", "violent", "sex", "sexe", "sexuel",
        "sexual", "érotique", "erotic", "adulte", "adult", "+18", "18+",
        "nc-17", "drogue", "drugs", "crime", "meurtre", "murder", "gore",
        "sanglant", "peur", "terreur", "terror", "guerre", "porn", "pornography",
        "pornographic", "xxx"
    )

    /** Age ratings that are always kid-safe. */
    private val KID_RATINGS = listOf(
        "g", "pg", "u", "tp", "tous publics", "tous public", "tout public",
        "3+", "7+", "6+", "7 ans", "jeunesse"
    )

    /** Age ratings that are never kid content. */
    private val ADULT_RATINGS = listOf(
        "18+", "16+", "nc-17", "rated r", "interdit", "adults only"
    )

    private val AGE_NUMBER = Regex("(\\d+)\\s*\\+?")

    /** Whole-word match: "sex" must not fire on "Sussex"/"Middlesex",
     *  "kid" must not fire on "kidnapping". A match counts when the
     *  characters glued to it are not letters (a leading "+" or digit
     *  does not need a boundary on its own side: "film+18" still
     *  matches "+18"). */
    private fun containsWord(text: String, word: String): Boolean {
        var from = 0
        while (true) {
            val idx = text.indexOf(word, from)
            if (idx < 0) return false
            val beforeOk = idx == 0 || !text[idx - 1].isLetter() || !word.first().isLetter()
            val end = idx + word.length
            val afterOk = end >= text.length || !text[end].isLetter() || !word.last().isLetter()
            if (beforeOk && afterOk) return true
            from = idx + 1
        }
    }

    fun isKidCategory(name: String): Boolean {
        val lower = name.lowercase()
        return KID_WORDS.any { containsWord(lower, it) }
    }

    fun isKidContent(category: String, description: String, rating: String? = null): Boolean {
        val text = (category + " " + description).lowercase()
        // 1. Adult signal anywhere wins immediately.
        if (ADULT_WORDS.any { containsWord(text, it) }) return false
        // 2. Age rating, when present, decides on its own.
        rating?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { r ->
            if (ADULT_RATINGS.any { r.contains(it) }) return false
            // Age number FIRST: "PG-13" is 13 (not a kid), "12+" is 12,
            // "7+" is 7 — before any word-list lookup.
            AGE_NUMBER.find(r)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { age ->
                if (age >= 12) return false
                if (age <= 7) return true
                // 8–11: fall through to the content words below.
            }
            // OP-03: exact whole-rating match only. The old
            // `r.contains(it)` classified "PG-13" as kid content because
            // it contains "g", and "Mature" because it contains "u".
            if (KID_RATINGS.any { r == it }) return true
        }
        // 3. Kid signal in the category or the description.
        return KID_WORDS.any { containsWord(text, it) }
    }
}
