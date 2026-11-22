package com.streammaster.tv.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Playlist
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

private const val TAG = "StreamMaster"

/**
 * Stalker Portal (Ministra / Stalker Middleware) client.
 *
 * ASSUMPTIONS (documented, per CONTRACT §4 — portals vary by provider):
 * - Portal base URL is the playlist portalUrl (e.g. http://portal.example.com/stalker_portal).
 * - Handshake: GET {portal}/server/load.php?type=stalker_portal&action=handshake
 *   &token=&JsHttpRequest=1-xml, with header "X-User-Agent: Model: MAG250; Link: WiFi".
 *   The response JSON contains js.token, which must be sent on later calls.
 * - Profile: action=get_profile. Channels: action=get_all_channels (or
 *   get_live_channels). EPG: action=get_epg&ch_id=...&period=... (short EPG).
 * - Stream URLs come from action=create_link&type=itv&cmd=<channel cmd> and are
 *   returned in js.cmd; if create_link fails we fall back to the channel's cmd
 *   itself when it already looks like a URL.
 * - MAC address is sent as mac=<MAC> on handshake and as a Cookie header
 *   "mac=<MAC>" on later calls; many portals also accept it as a query param.
 * - If a portal answers with a different JSON shape, the parsers below read
 *   defensively (null-safe) and the caller treats empty results as failure.
 */
class StalkerApi(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()

    data class StalkerChannel(
        val id: String,
        val name: String,
        val number: String,
        val cmd: String,
        val tvGenreId: String,
        val logo: String?,
        val epgId: String?
    )

    data class StalkerContent(
        val bouquets: List<Bouquet>,
        val channels: List<Channel>,
        val expiryDate: String,
        val status: String
    )

    private fun portalBase(playlist: Playlist): String =
        playlist.portalUrl.trimEnd('/')

    private fun handshakeRequest(portal: String, mac: String): Request {
        val url = "$portal/server/load.php?type=stalker_portal&action=handshake" +
            "&token=&JsHttpRequest=1-xml"
        return Request.Builder()
            .url(url)
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Paris")
            .build()
    }

    /** Performs the handshake and returns the session token, or null on failure. */
    suspend fun handshake(playlist: Playlist): String? {
        val portal = portalBase(playlist)
        val resp = runCatching {
            okHttpClient.newCall(handshakeRequest(portal, playlist.mac)).execute()
        }.getOrNull() ?: return null
        resp.use {
            if (!it.isSuccessful) {
                Log.w(TAG, "Stalker handshake failed: HTTP ${it.code}")
                return null
            }
            val body = it.body?.string() ?: return null
            return runCatching {
                val obj = gson.fromJson(body, JsonObject::class.java)
                obj.getAsJsonObject("js")?.get("token")?.asString
            }.getOrNull()
        }
    }

    private fun authedRequest(portal: String, mac: String, token: String, actionUrl: String): Request {
        return Request.Builder()
            .url("$portal/server/load.php?$actionUrl&JsHttpRequest=1-xml")
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Authorization", "Bearer $token")
            .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Paris; token=$token")
            .build()
    }

    private fun getJson(playlist: Playlist, token: String, actionUrl: String): JsonObject? {
        val req = authedRequest(portalBase(playlist), playlist.mac, token, actionUrl)
        val resp = runCatching { okHttpClient.newCall(req).execute() }.getOrNull() ?: return null
        resp.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            return runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
        }
    }

    data class StalkerGenre(val id: String, val title: String)

    suspend fun fetchGenres(playlist: Playlist, token: String): List<StalkerGenre> {
        val json = getJson(playlist, token, "type=stalker_portal&action=get_genres&type=itv")
            ?: getJson(playlist, token, "type=itv&action=get_genres") ?: return emptyList()
        val arr = json.getAsJsonArray("js") ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                StalkerGenre(
                    id = o.get("id")?.asString ?: return@runCatching null,
                    title = o.get("title")?.asString ?: "Autre"
                )
            }.getOrNull()
        }
    }

    suspend fun fetchChannels(playlist: Playlist, token: String): List<StalkerChannel> {
        val json = getJson(playlist, token, "type=stalker_portal&action=get_all_channels")
            ?: getJson(playlist, token, "type=itv&action=get_all_channels")
            ?: return emptyList()
        val arr = json.getAsJsonArray("js") ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                StalkerChannel(
                    id = o.get("id")?.asString ?: return@runCatching null,
                    name = o.get("name")?.asString ?: "Chaîne",
                    number = o.get("number")?.asString ?: o.get("id")?.asString ?: "0",
                    cmd = o.get("cmd")?.asString ?: "",
                    tvGenreId = o.get("tv_genre_id")?.asString ?: "",
                    logo = o.get("logo")?.takeIf { !it.isJsonNull }?.asString,
                    epgId = o.get("epg_id")?.takeIf { !it.isJsonNull }?.asString
                )
            }.getOrNull()
        }
    }

    /** Resolves a playable URL for a channel cmd via create_link. */
    suspend fun resolveStreamUrl(playlist: Playlist, token: String, cmd: String): String {
        if (cmd.startsWith("http")) return cmd
        val encoded = java.net.URLEncoder.encode(cmd, "UTF-8")
        val json = getJson(
            playlist, token,
            "type=stalker_portal&action=create_link&type=itv&cmd=$encoded&forced_storage=undefined&disable_ad=0"
        ) ?: return cmd
        val js = json.getAsJsonObject("js") ?: return cmd
        return js.get("cmd")?.asString ?: cmd
    }

    /** Full fetch: handshake -> genres + channels. Stream URLs resolved lazily per channel. */
    suspend fun fetchAll(playlist: Playlist): StalkerContent? {
        val token = handshake(playlist) ?: return null
        val genres = fetchGenres(playlist, token)
        val genreName = genres.associate { it.id to it.title }
        val raw = fetchChannels(playlist, token)
        if (raw.isEmpty()) return null
        val bouquets = genres.mapIndexed { i, g ->
            Bouquet("st-${g.id}", g.title, BouquetKind.LIVE, i, false)
        }
        val channels = raw.mapIndexed { idx, c ->
            val genreId = c.tvGenreId.ifEmpty { "0" }
            Channel(
                id = "st-${c.id}",
                num = c.number.padStart(2, '0'),
                name = c.name,
                code = c.name.take(2).uppercase(),
                category = genreName[genreId] ?: "Autre",
                quality = "FHD",
                nowTitle = "",
                nowTime = "",
                progress = 0,
                hue = 44,
                description = "",
                next = emptyList(),
                isFavorite = false,
                isBroken = false,
                streamUrl = c.cmd, // resolved on demand via resolveStreamUrl when played
                epgId = c.epgId,
                customNum = null,
                hidden = false,
                customName = null,
                bouquetId = "st-$genreId"
            )
        }
        return StalkerContent(bouquets, channels, playlist.expiryDate, "Actif")
    }

    /** Short EPG for a channel (period in hours, default 24). Best effort. */
    suspend fun fetchEpg(playlist: Playlist, channelId: String, periodHours: Int = 24): List<EpgProgram> {
        val token = handshake(playlist) ?: return emptyList()
        val chId = channelId.removePrefix("st-")
        val json = getJson(
            playlist, token,
            "type=stalker_portal&action=get_epg&ch_id=$chId&period=$periodHours"
        ) ?: return emptyList()
        val arr = json.getAsJsonArray("js") ?: return emptyList()
        return arr.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                val start = o.get("start_timestamp")?.asLong?.times(1000) ?: return@runCatching null
                val stop = o.get("stop_timestamp")?.asLong?.times(1000) ?: (start + 3600_000L)
                EpgProgram(
                    channelId = channelId,
                    title = o.get("name")?.asString ?: "",
                    startMillis = start,
                    endMillis = stop,
                    description = o.get("descr")?.takeIf { !it.isJsonNull }?.asString ?: ""
                )
            }.getOrNull()
        }
    }

    @Suppress("unused")
    data class StalkerProfile(
        @SerializedName("id") val id: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("phone") val phone: String?,
        @SerializedName("ls") val ls: String?
    )
}
