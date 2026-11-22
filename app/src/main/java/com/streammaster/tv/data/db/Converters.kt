package com.streammaster.tv.data.db

import androidx.room.TypeConverter

/** Small string-list encoders so Room entities stay free of Gson. */
object Converters {
    private const val ITEM_SEP = "\u001f"
    private const val PAIR_SEP = "\u001e"

    @TypeConverter
    @JvmStatic
    fun stringListToString(list: List<String>): String = list.joinToString(ITEM_SEP)

    @TypeConverter
    @JvmStatic
    fun stringToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(ITEM_SEP)

    @TypeConverter
    @JvmStatic
    fun pairListToString(list: List<Pair<String, String>>): String =
        list.joinToString(PAIR_SEP) { "${it.first}$ITEM_SEP${it.second}" }

    @TypeConverter
    @JvmStatic
    fun stringToPairList(value: String): List<Pair<String, String>> {
        if (value.isEmpty()) return emptyList()
        return value.split(PAIR_SEP).mapNotNull { part ->
            val bits = part.split(ITEM_SEP)
            if (bits.size == 2) bits[0] to bits[1] else null
        }
    }

    // Convenience helpers used by the mappers (same encoding as the converters).
    fun encodePairs(list: List<Pair<String, String>>): String = pairListToString(list)
    fun decodePairs(value: String): List<Pair<String, String>> = stringToPairList(value)
    fun encodeStrings(list: List<String>): String = stringListToString(list)
    fun decodeStrings(value: String): List<String> = stringToStringList(value)
}
