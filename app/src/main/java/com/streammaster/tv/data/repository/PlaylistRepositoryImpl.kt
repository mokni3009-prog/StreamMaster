package com.streammaster.tv.data.repository

import android.util.Log
import com.streammaster.tv.data.api.M3uParser
import com.streammaster.tv.data.api.StalkerApi
import com.streammaster.tv.data.api.XtreamApi
import com.streammaster.tv.data.db.Converters
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.util.TitleNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"
const val MAX_PLAYLISTS = 10

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase,
    private val okHttpClient: OkHttpClient,
    private val xtreamApi: XtreamApi,
    private val stalkerApi: StalkerApi,
    private val settingsStore: SettingsStore
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        db.playlistDao().observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun addPlaylist(p: Playlist): String {
        val count = db.playlistDao().count()
        require(count < MAX_PLAYLISTS) { "Maximum $MAX_PLAYLISTS playlists" }
        val id = p.id.ifEmpty { UUID.randomUUID().toString() }
        val entity = p.copy(id = id).toEntity()
        db.playlistDao().upsert(entity)
        if (p.isActive || count == 0) activatePlaylist(id)
        return id
    }

    override suspend fun updatePlaylist(p: Playlist) {
        db.playlistDao().upsert(p.toEntity())
    }

    override suspend fun deletePlaylist(id: String) {
        val wasActive = db.playlistDao().getById(id)?.isActive == true
        db.channelDao().deleteByPlaylist(id)
        db.filmDao().deleteByPlaylist(id)
        db.seriesDao().deleteByPlaylist(id)
        db.bouquetDao().deleteByPlaylist(id)
        db.playlistDao().deleteById(id)
        if (wasActive) {
            db.playlistDao().getAll().firstOrNull()?.let { activatePlaylist(it.id) }
        }
    }

    override suspend fun activatePlaylist(id: String) {
        db.playlistDao().deactivateAll()
        db.playlistDao().activate(id)
    }

    override suspend fun getActive(): Playlist? = db.playlistDao().getActive()?.toModel()

    /**
     * Re-fetches the playlist from its real server and MERGES into Room,
     * preserving user customizations: hidden flags, customNum, customName,
     * favorites and bouquet assignment. Returns false on any fetch failure
     * (stored content is left untouched).
     */
    override suspend fun refreshPlaylist(id: String): Boolean {
        val playlist = db.playlistDao().getById(id)?.toModel() ?: return false
        if (playlist.id == com.streammaster.tv.data.demo.DemoData.DEMO_PLAYLIST_ID) {
            // Demo playlist has no server; nothing to refresh.
            return true
        }
        val format = runCatching { settingsStore.settings.first().format }.getOrDefault("auto")
        return try {
            when (playlist.type) {
                PlaylistType.XTREAM -> refreshXtream(playlist, format)
                PlaylistType.STALKER -> refreshStalker(playlist)
                PlaylistType.M3U -> refreshM3u(playlist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshPlaylist($id) failed", e)
            false
        }
    }

    private suspend fun refreshXtream(playlist: Playlist, format: String): Boolean {
        val content = runCatching { xtreamApi.fetchAll(playlist, format) }.getOrNull() ?: return false
        val pid = playlist.id
        // Preserve customizations from stored rows.
        val oldChannels = db.channelDao().getAll(pid).associateBy { it.id }
        val oldFilms = db.filmDao().getAll(pid).associateBy { it.id }
        val oldSeries = db.seriesDao().getAll(pid).associateBy { it.id }
        val oldBouquets = db.bouquetDao().getAll(pid).associateBy { it.id }

        db.bouquetDao().upsertAll(content.bouquets.map { b ->
            val old = oldBouquets[b.id]
            b.toEntity(pid).copy(hidden = old?.hidden ?: false, order = old?.order ?: b.order)
        })
        db.channelDao().upsertAll(content.channels.mapIndexed { idx, c ->
            val old = oldChannels[c.id]
            if (old == null) c.toEntity(pid, idx)
            else c.toEntity(pid, idx).copy(
                isFavorite = old.isFavorite,
                hidden = old.hidden,
                customNum = old.customNum,
                customName = old.customName,
                bouquetId = old.bouquetId,
                backupUrlsEncoded = old.backupUrlsEncoded
            )
        })
        db.filmDao().upsertAll(content.films.mapIndexed { idx, f ->
            val old = oldFilms[f.id]
            if (old == null) f.toEntity(pid, idx)
            else f.toEntity(pid, idx).copy(isFavorite = old.isFavorite, hidden = old.hidden)
        })
        db.seriesDao().upsertAll(content.series.mapIndexed { idx, s ->
            val old = oldSeries[s.id]
            if (old == null) s.toEntity(pid, idx)
            else s.toEntity(pid, idx).copy(isFavorite = old.isFavorite, hidden = old.hidden)
        })
        db.episodeDao().upsertAll(content.episodes.map { it.toEntity() })
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        db.playlistDao().upsert(
            playlist.copy(
                expiryDate = content.expiryDate,
                connections = content.connections,
                status = content.status,
                lastUpdate = now
            ).toEntity()
        )
        recomputeBackupUrls()
        return true
    }

    private suspend fun refreshStalker(playlist: Playlist): Boolean {
        val content = stalkerApi.fetchAll(playlist) ?: return false
        val pid = playlist.id
        val oldChannels = db.channelDao().getAll(pid).associateBy { it.id }
        val oldBouquets = db.bouquetDao().getAll(pid).associateBy { it.id }
        db.bouquetDao().upsertAll(content.bouquets.map { b ->
            val old = oldBouquets[b.id]
            b.toEntity(pid).copy(hidden = old?.hidden ?: false, order = old?.order ?: b.order)
        })
        db.channelDao().upsertAll(content.channels.mapIndexed { idx, c ->
            val old = oldChannels[c.id]
            if (old == null) c.toEntity(pid, idx)
            else c.toEntity(pid, idx).copy(
                isFavorite = old.isFavorite,
                hidden = old.hidden,
                customNum = old.customNum,
                customName = old.customName,
                bouquetId = old.bouquetId
            )
        })
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        db.playlistDao().upsert(playlist.copy(status = content.status, lastUpdate = now).toEntity())
        recomputeBackupUrls()
        return true
    }

    private suspend fun refreshM3u(playlist: Playlist): Boolean {
        val url = playlist.m3uUrl.ifEmpty { return false }
        val req = Request.Builder().url(url).build()
        val resp = runCatching { okHttpClient.newCall(req).execute() }.getOrNull() ?: return false
        val text = resp.use { if (!it.isSuccessful) return false; it.body?.string() ?: return false }
        val parsed = M3uParser.parse(text, url)
        if (parsed.channels.isEmpty()) return false
        val pid = playlist.id
        val oldChannels = db.channelDao().getAll(pid).associateBy { it.id }
        val oldBouquets = db.bouquetDao().getAll(pid).associateBy { it.id }
        db.bouquetDao().upsertAll(parsed.bouquets.map { b ->
            val old = oldBouquets[b.id]
            b.toEntity(pid).copy(hidden = old?.hidden ?: false, order = old?.order ?: b.order)
        })
        db.channelDao().upsertAll(parsed.channels.mapIndexed { idx, c ->
            val old = oldChannels[c.id]
            if (old == null) c.toEntity(pid, idx)
            else c.toEntity(pid, idx).copy(
                isFavorite = old.isFavorite,
                hidden = old.hidden,
                customNum = old.customNum,
                customName = old.customName,
                bouquetId = old.bouquetId
            )
        })
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        // Remember the XMLTV URL (if the playlist advertises one) in serverUrl
        // so EpgRepository can use it for M3U EPG refreshes.
        val xmlTv = parsed.xmlTvUrl
        db.playlistDao().upsert(
            playlist.copy(
                lastUpdate = now,
                status = "Actif",
                serverUrl = xmlTv ?: playlist.serverUrl
            ).toEntity()
        )
        recomputeBackupUrls()
        return true
    }

    /**
     * Smart failover (2026-11-22): channels with the same normalized name
     * in different playlists (or duplicated inside one) become each
     * other's backup URLs. The player tries them in order when the primary
     * stream fails (see PlayerManagerImpl.handleFailure). Runs after every
     * playlist refresh, across ALL playlists, so adding a second playlist
     * and syncing it wires the backups automatically. Channels with no
     * same-name sibling get their stale backups cleared.
     */
    private suspend fun recomputeBackupUrls() {
        val all = db.channelDao().getAllChannels()
        val byName = all.groupBy { TitleNormalizer.normalize(it.name) }
        for ((_, group) in byName) {
            val urls = group.map { it.streamUrl }.distinct()
            for (channel in group) {
                val backups = urls.filter { it != channel.streamUrl }.take(3)
                db.channelDao().updateBackupUrls(
                    channel.id, Converters.encodeStrings(backups)
                )
            }
        }
    }
}
