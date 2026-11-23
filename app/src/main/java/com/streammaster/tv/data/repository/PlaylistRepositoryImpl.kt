package com.streammaster.tv.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.streammaster.tv.R
import com.streammaster.tv.data.api.M3uParser
import com.streammaster.tv.data.api.StalkerApi
import com.streammaster.tv.data.api.XtreamApi
import com.streammaster.tv.data.db.Converters
import com.streammaster.tv.data.db.GenreFollow
import com.streammaster.tv.data.db.SeriesFollow
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.util.TitleNormalizer
import com.streammaster.tv.data.work.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val settingsStore: SettingsStore,
    /** §4.50/§4.57 — notifications need a Context; AppModule must pass
     *  it (providePlaylistRepository gains the @ApplicationContext arg). */
    @ApplicationContext private val context: Context
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
        val format = runCatching { settingsStore.settings.first().format }.getOrDefault("auto")
        val ok = try {
            when (playlist.type) {
                PlaylistType.XTREAM -> refreshXtream(playlist, format)
                PlaylistType.STALKER -> refreshStalker(playlist)
                PlaylistType.M3U -> refreshM3u(playlist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshPlaylist($id) failed", e)
            false
        }
        // §4.50 + §4.57 — after every successful sync, diff the followed
        // series/genres against their stored baselines and notify about
        // what is new. Never fails the refresh itself.
        if (ok) runCatching { checkFollowedUpdates() }
            .onFailure { Log.w(TAG, "followed-content check failed", it) }
        return ok
    }

    // ------------------------------------------------------------------
    // §4.50 new-episode alerts + §4.57 new-film-by-genre alerts.
    // Followed rows live in series_follows / genre_follows (DAOs wired by
    // the coordinator into StreamMasterDatabase). Following snapshots the
    // CURRENT state, so the first refresh after following stays silent
    // and only genuinely new items alert afterwards.
    // ------------------------------------------------------------------

    override suspend fun followSeries(seriesId: String, title: String) {
        val count = db.episodeDao().getAllForSeries(seriesId).size
        db.seriesFollowDao().upsert(
            SeriesFollow(seriesId, title, count, System.currentTimeMillis())
        )
    }

    override suspend fun unfollowSeries(seriesId: String) {
        db.seriesFollowDao().deleteById(seriesId)
    }

    override suspend fun followGenre(genre: String) {
        db.genreFollowDao().upsert(
            GenreFollow(genre, genreFilmIdsHash(genre), System.currentTimeMillis())
        )
    }

    override suspend fun unfollowGenre(genre: String) {
        db.genreFollowDao().deleteById(genre)
    }

    override fun observeFollowedSeriesIds(): Flow<Set<String>> =
        db.seriesFollowDao().observeAll().map { list -> list.map { it.seriesId }.toSet() }

    override fun observeFollowedGenres(): Flow<Set<String>> =
        db.genreFollowDao().observeAll().map { list -> list.map { it.genre }.toSet() }

    /** Sorted film ids of one genre, hashed — the §4.57 baseline. */
    private suspend fun genreFilmIdsHash(genre: String): String =
        db.filmDao().getAllFilms()
            .filter { it.genre == genre }
            .map { it.id }.sorted()
            .joinToString("|").hashCode().toString()

    private suspend fun checkFollowedUpdates() {
        val now = System.currentTimeMillis()
        // Series: episode count grew since the stored baseline.
        for (follow in db.seriesFollowDao().getAll()) {
            val count = db.episodeDao().getAllForSeries(follow.seriesId).size
            if (count > follow.lastEpisodeCount) {
                val newCount = count - follow.lastEpisodeCount
                postFollowNotification(
                    context.getString(R.string.follow_new_episodes_title),
                    context.getString(R.string.follow_new_episodes_text, newCount, follow.title),
                    ("series|" + follow.seriesId).hashCode()
                )
            }
            if (count != follow.lastEpisodeCount) {
                db.seriesFollowDao().upsert(follow.copy(lastEpisodeCount = count, lastChecked = now))
            }
        }
        // Genres: the set of film ids changed since the stored hash.
        for (follow in db.genreFollowDao().getAll()) {
            val hash = genreFilmIdsHash(follow.genre)
            if (follow.lastFilmIdsHash.isNotEmpty() && hash != follow.lastFilmIdsHash) {
                postFollowNotification(
                    context.getString(R.string.follow_new_films_title),
                    context.getString(R.string.follow_new_films_text, follow.genre),
                    ("genre|" + follow.genre).hashCode()
                )
            }
            if (hash != follow.lastFilmIdsHash) {
                db.genreFollowDao().upsert(follow.copy(lastFilmIdsHash = hash, lastChecked = now))
            }
        }
    }

    /** Posts on the "reminders" channel (created at app start by
     *  ReminderScheduler.initialize; created defensively here too). */
    private fun postFollowNotification(title: String, text: String, id: Int) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(ReminderScheduler.CHANNEL_ID) == null
            ) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        ReminderScheduler.CHANNEL_ID,
                        context.getString(R.string.follow_alerts_channel),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.follow_alerts_channel_desc)
                    }
                )
            }
            val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, notification)
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
                backupUrlsEncoded = old.backupUrlsEncoded,
                logoUrl = c.logoUrl?.takeIf { it.isNotBlank() } ?: old.logoUrl,
                isBroken = old.isBroken
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
                bouquetId = old.bouquetId,
                logoUrl = c.logoUrl?.takeIf { it.isNotBlank() } ?: old.logoUrl,
                isBroken = old.isBroken
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
                bouquetId = old.bouquetId,
                logoUrl = c.logoUrl?.takeIf { it.isNotBlank() } ?: old.logoUrl,
                isBroken = old.isBroken
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
