package com.streammaster.tv.data.api

import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import java.net.URL

/** Parsed M3U playlist: live channels grouped by group-title. */
data class ParsedM3u(
    val bouquets: List<Bouquet>,
    val channels: List<Channel>,
    /** Optional XMLTV EPG URL found in the playlist (#EXTM3U x-tvg-url). */
    val xmlTvUrl: String?
)

/**
 * M3U / M3U8 parser.
 * Handles #EXTINF attributes (tvg-id, tvg-logo, group-title, tvg-name, tvg-chno),
 * #EXTGRP, and plain stream-URL lines. Relative stream URLs are resolved
 * against the playlist URL. Non-#EXTINF entries (bare URLs) are kept as
 * channels in an "Autre" bouquet.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String, playlistUrl: String): ParsedM3u {
        val lines = text.lineSequence().map { it.trim() }.toList()
        var xmlTvUrl: String? = null
        val channels = mutableListOf<Channel>()
        val groups = linkedMapOf<String, Int>() // group name -> order
        var pendingGroup: String? = null
        var idx = 0

        fun resolveUrl(raw: String): String {
            if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("rtsp://")) return raw
            return runCatching { URL(URL(playlistUrl), raw).toString() }.getOrDefault(raw)
        }

        for ((lineNo, line) in lines.withIndex()) {
            when {
                line.startsWith("#EXTM3U") -> {
                    val attrs = parseAttrs(line)
                    xmlTvUrl = attrs["x-tvg-url"] ?: attrs["url-tvg"]
                }
                line.startsWith("#EXTGRP") -> {
                    pendingGroup = line.removePrefix("#EXTGRP").removePrefix(":").trim()
                        .takeIf { it.isNotEmpty() }
                }
                line.startsWith("#EXTINF") -> {
                    val attrs = parseAttrs(line)
                    val afterComma = line.substringAfter(",", "").trim()
                    val name = attrs["tvg-name"] ?: afterComma.ifEmpty { "Chaîne ${idx + 1}" }
                    val group = attrs["group-title"] ?: pendingGroup ?: "Autre"
                    if (!groups.containsKey(group)) groups[group] = groups.size
                    // The stream URL is the next non-empty, non-comment line.
                    var url: String? = null
                    for (j in lineNo + 1 until lines.size) {
                        val next = lines[j]
                        if (next.isEmpty()) continue
                        if (next.startsWith("#")) break
                        url = next
                        break
                    }
                    if (url == null) continue
                    val chno = attrs["tvg-chno"] ?: (idx + 1).toString()
                    channels += Channel(
                        id = "m3u-${attrs["tvg-id"]?.takeIf { it.isNotEmpty() } ?: "ch$idx"}",
                        num = chno.padStart(2, '0'),
                        name = name,
                        code = name.take(2).uppercase(),
                        category = group,
                        quality = "FHD",
                        nowTitle = "",
                        nowTime = "",
                        progress = 0,
                        hue = 44,
                        description = "",
                        next = emptyList(),
                        isFavorite = false,
                        isBroken = false,
                        streamUrl = resolveUrl(url),
                        epgId = attrs["tvg-id"]?.takeIf { it.isNotEmpty() },
                        customNum = null,
                        hidden = false,
                        customName = null,
                        bouquetId = "m3u-" + group.hashCode().toString().replace("-", "n")
                    )
                    idx++
                    pendingGroup = null
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    // Bare URL without #EXTINF — keep it as a channel.
                    val group = pendingGroup ?: "Autre"
                    if (!groups.containsKey(group)) groups[group] = groups.size
                    channels += Channel(
                        id = "m3u-bare$idx",
                        num = (idx + 1).toString().padStart(2, '0'),
                        name = line.substringAfterLast('/').substringBefore('?').ifEmpty { "Chaîne ${idx + 1}" },
                        code = "CH",
                        category = group,
                        quality = "FHD",
                        nowTitle = "",
                        nowTime = "",
                        progress = 0,
                        hue = 44,
                        description = "",
                        next = emptyList(),
                        isFavorite = false,
                        isBroken = false,
                        streamUrl = resolveUrl(line),
                        epgId = null,
                        customNum = null,
                        hidden = false,
                        customName = null,
                        bouquetId = "m3u-" + group.hashCode().toString().replace("-", "n")
                    )
                    idx++
                    pendingGroup = null
                }
            }
        }

        val bouquets = groups.entries.sortedBy { it.value }.map { (name, order) ->
            Bouquet(
                id = "m3u-" + name.hashCode().toString().replace("-", "n"),
                name = name,
                kind = BouquetKind.LIVE,
                order = order,
                hidden = false
            )
        }
        return ParsedM3u(bouquets, channels, xmlTvUrl)
    }

    private fun parseAttrs(line: String): Map<String, String> =
        ATTR_REGEX.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
}
