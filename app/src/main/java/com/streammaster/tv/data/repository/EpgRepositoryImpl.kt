package com.streammaster.tv.data.repository

import android.util.Log
import com.streammaster.tv.data.api.StalkerApi
import com.streammaster.tv.data.api.XmlTvParser
import com.streammaster.tv.data.api.XtreamApi
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.util.TitleNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/**
 * Multi-day EPG refresh (the grid navigates tonight + the next nights, up to
 * 7 days per the EPG window setting), incremental: the near days first.
 * - Xtream: get_short_epg per channel (larger limit so it covers the window).
 * - Stalker: get_epg per channel for the whole window.
 * - M3U: external XMLTV URL — the playlist's xmltvUrl (stored there by
 *   PlaylistRepositoryImpl from #EXTM3U x-tvg-url); serverUrl is only a
 *   legacy fallback when it already points at an .xml file.
 * - Second source: the playlist's optional xmltvUrl is fetched after the
 *   primary source and fills ONLY the channels the primary did not cover —
 *   provider data always wins.
 */
@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase,
    private val xtreamApi: XtreamApi,
    private val stalkerApi: StalkerApi,
    private val okHttpClient: OkHttpClient,
    private val settingsStore: SettingsStore
) : EpgRepository {

    override fun observePrograms(channelId: String, fromMillis: Long, toMillis: Long): Flow<List<EpgProgram>> =
        db.epgDao().observePrograms(channelId, fromMillis, toMillis).map { list -> list.map { it.toModel() } }

    override suspend fun refreshEpg(playlistId: String) {
        val playlist = db.playlistDao().getById(playlistId)?.toModel() ?: return
        val now = System.currentTimeMillis()
        // Days of guide kept, from the EPG window setting: 24h = tonight +
        // tomorrow night, 48h = one more night, 7d = a full week.
        // Keep in sync with EpgViewModel.daysForWindow.
        val days = when (runCatching { settingsStore.settings.first().epgWindow }.getOrDefault("24h")) {
            "48h" -> 3
            "7d" -> 7
            else -> 2
        }
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val from = now - 2 * 3600_000L
        // Covers the 02:00 end of the last navigable night.
        val to = dayStart + days * 24 * 3600_000L + 2 * 3600_000L
        // Prune stale programs first so the grid never shows yesterday's data.
        // OP-06: prune by END time — a program that started >2h ago and is
        // still airing must not be deleted mid-broadcast.
        db.epgDao().deleteEndedBefore(from)

        val covered = mutableSetOf<String>()
        var primaryXml: String? = null
        when (playlist.type) {
            PlaylistType.XTREAM -> {
                val channels = db.channelDao().getAll(playlistId)
                // LG-21: one get_short_epg per channel, in parallel (4 at a
                // time) instead of hundreds of sequential calls; each
                // channel's delete+insert is one transaction, so a failure
                // mid-sync no longer leaves half a grid.
                val results = fetchAllEpg(channels) { channel ->
                    val streamId = channel.id.removePrefix("xt-").toIntOrNull()
                    if (streamId == null) null
                    else runCatching { xtreamApi.fetchShortEpg(playlist, streamId, limit = 100) }
                        .getOrDefault(emptyList())
                        .takeIf { it.isNotEmpty() }
                        ?.let { programs -> channel.id to programs }
                }
                for ((channelId, programs) in results) {
                    db.withTransaction {
                        db.epgDao().deleteRange(channelId, from, to)
                        db.epgDao().upsertAll(programs.map { it.toEntity() })
                    }
                    covered += channelId
                }
            }
            PlaylistType.STALKER -> {
                val channels = db.channelDao().getAll(playlistId)
                val results = fetchAllEpg(channels) { channel ->
                    runCatching {
                        stalkerApi.fetchEpg(playlist, channel.id, days * 24)
                    }.getOrDefault(emptyList())
                        .takeIf { it.isNotEmpty() }
                        ?.let { programs -> channel.id to programs }
                }
                for ((channelId, programs) in results) {
                    db.withTransaction {
                        db.epgDao().deleteRange(channelId, from, to)
                        db.epgDao().upsertAll(programs.map { it.toEntity() })
                    }
                    covered += channelId
                }
            }
            PlaylistType.M3U -> {
                // LG-03: the XMLTV URL now lives in the dedicated xmltvUrl
                // field; serverUrl is only a legacy fallback when it
                // already points at an .xml file.
                primaryXml = playlist.xmltvUrl?.takeIf { it.isNotBlank() }
                    ?: playlist.serverUrl.takeIf { it.endsWith(".xml", ignoreCase = true) }
                    ?: playlist.m3uUrl.takeIf { it.endsWith(".xml", ignoreCase = true) }
                if (primaryXml != null) {
                    covered += syncXmlTv(primaryXml, playlistId, from, to, emptySet())
                }
            }
        }
        // Second EPG source (optional XMLTV URL on the playlist): fills only
        // the channels the primary source did not cover — provider data wins.
        val extraXml = playlist.xmltvUrl?.takeIf { it.isNotBlank() }
        if (extraXml != null && extraXml != primaryXml) {
            covered += syncXmlTv(extraXml, playlistId, from, to, covered)
        }
        Log.i(TAG, "EPG refresh done for playlist $playlistId (${covered.size} channels)")
    }

    /**
     * LG-21 — fetches EPG for many channels concurrently (4 at a time),
     * keeping channel order in the results. Each [fetch] must be safe to
     * run on any thread (network inside is IO-dispatched by the APIs).
     */
    private suspend fun <T> fetchAllEpg(
        channels: List<com.streammaster.tv.data.db.ChannelEntity>,
        fetch: suspend (com.streammaster.tv.data.db.ChannelEntity) -> T?
    ): List<T> = coroutineScope {
        val semaphore = Semaphore(4)
        channels.map { channel ->
            async { semaphore.withPermit { fetch(channel) } }
        }.awaitAll().filterNotNull()
    }

    /**
     * XMLTV sync for one URL: maps XMLTV channel ids to our channels via
     * epgId (tvg-id) then display name; inserts programs inside [from, to]
     * for channels NOT in [skipChannelIds]. Returns the channel ids covered.
     */
    private suspend fun syncXmlTv(
        url: String,
        playlistId: String,
        from: Long,
        to: Long,
        skipChannelIds: Set<String>
    ): Set<String> {
        val req = Request.Builder().url(url).build()
        // OP-01: synchronous execute() must never run on the Main thread
        // (callers refresh from viewModelScope / workers) — dispatch to IO.
        val resp = withContext(Dispatchers.IO) {
            runCatching { okHttpClient.newCall(req).execute() }.getOrNull()
        } ?: return emptySet()
        val result = resp.use { r ->
            if (!r.isSuccessful) return emptySet()
            val body = r.body ?: return emptySet()
            runCatching { XmlTvParser.parse(body.byteStream()) }.getOrNull() ?: return emptySet()
        }
        val channels = db.channelDao().getAll(playlistId)
        val byEpgId = channels.filter { it.epgId != null }.associateBy { it.epgId }
        // §4.36.4 — smart EPG linking: match on the NORMALIZED name
        // (accents / spaces / case / quality tags stripped), not on the
        // raw lowercase name. epgId still wins when it matches.
        // LG-21: groupBy, not associateBy — two channels with the same
        // normalized name used to leave the first one without EPG forever.
        // Blank normalized names never match (they would group everything).
        val byName = channels
            .filter { TitleNormalizer.normalize(it.name).isNotBlank() }
            .groupBy { TitleNormalizer.normalize(it.name) }
        val xmlNameById = result.channels.associate { it.id to it.displayName }
        val grouped = result.programs.groupBy { it.channelId }
        val covered = mutableSetOf<String>()
        for ((xmlId, programs) in grouped) {
            val normalizedXmlName = xmlNameById[xmlId]
                ?.let { TitleNormalizer.normalize(it) }
                ?.takeIf { it.isNotBlank() }
            val channel = byEpgId[xmlId]
                ?: normalizedXmlName?.let { name ->
                    byName[name]?.firstOrNull { it.id !in skipChannelIds }
                }
                ?: continue
            if (channel.id in skipChannelIds) continue
            val inWindow = programs.filter { it.startMillis in from..to }
            if (inWindow.isEmpty()) continue
            db.withTransaction {
                db.epgDao().deleteRange(channel.id, from, to)
                db.epgDao().upsertAll(
                    inWindow.map { it.copy(channelId = channel.id).toEntity() }
                )
            }
            covered += channel.id
        }
        return covered
    }
}
