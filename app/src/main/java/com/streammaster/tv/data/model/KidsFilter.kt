package com.streammaster.tv.data.model

/** Kids mode (2026-11-22): only categories whose name looks kid-friendly
 *  are shown. Heuristic on the bouquet/category name — Xtream/M3U
 *  providers do not expose age ratings. */
object KidsFilter {
    private val KID_WORDS = listOf(
        "kid", "kids", "enfant", "enfants", "animation", "cartoon", "cartoons",
        "dessin", "dessins", "famille", "family", "junior", "disney", "nickelodeon",
        "pixar", "baby", "bébé", "jeunesse", "child", "children", "toons", "anime"
    )

    fun isKidCategory(name: String): Boolean {
        val lower = name.lowercase()
        return KID_WORDS.any { lower.contains(it) }
    }
}
