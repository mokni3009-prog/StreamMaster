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
        "pixar", "baby", "bébé", "jeunesse", "child", "children", "toons", "anime"
    )

    /** Words that disqualify a title from kids mode, wherever they appear
     *  (category or description). Checked BEFORE the kid words, so an
     *  "Action / violence" title never passes because it also says
     *  "family". */
    private val ADULT_WORDS = listOf(
        "horreur", "horror", "violence", "violent", "sex", "sexe", "sexuel",
        "sexual", "érotique", "erotic", "adulte", "adult", "+18", "18+",
        "nc-17", "drogue", "drugs", "crime", "meurtre", "murder", "gore",
        "sanglant", "peur", "terreur", "terror", "guerre", "porn", "xxx"
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

    fun isKidCategory(name: String): Boolean {
        val lower = name.lowercase()
        return KID_WORDS.any { lower.contains(it) }
    }

    fun isKidContent(category: String, description: String, rating: String? = null): Boolean {
        val text = (category + " " + description).lowercase()
        // 1. Adult signal anywhere wins immediately.
        if (ADULT_WORDS.any { text.contains(it) }) return false
        // 2. Age rating, when present, decides on its own.
        rating?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { r ->
            if (ADULT_RATINGS.any { r.contains(it) }) return false
            if (KID_RATINGS.any { r == it || r.contains(it) }) return true
            AGE_NUMBER.find(r)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { age ->
                if (age >= 12) return false
                if (age <= 7) return true
                // 8–11: fall through to the content words below.
            }
        }
        // 3. Kid signal in the category or the description.
        return KID_WORDS.any { text.contains(it) }
    }
}
