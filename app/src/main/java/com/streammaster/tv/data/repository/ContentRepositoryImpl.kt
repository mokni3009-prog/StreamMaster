package com.streammaster.tv.data.repository

import android.content.Context
import android.util.Log
import com.streammaster.tv.data.api.XtreamApi
import com.streammaster.tv.data.cleanup.PlaylistCleanupScanner
import com.streammaster.tv.data.db.Converters
import com.streammaster.tv.data.db.HistoryEntity
import com.streammaster.tv.data.db.ReminderEntity
import com.streammaster.tv.data.db.SearchHistoryEntity
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.HistoryEntry
import com.streammaster.tv.data.model.Reminder
import com.streammaster.tv.data.model.SearchResult
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.work.ChannelTimerScheduler
import com.streammaster.tv.data.work.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"
private const val HISTORY_CAP = 20
/** Id of the demo playlist removed in 2026-11-23 — only used to clean old installs. */
private const val LEGACY_DEMO_PLAYLIST_ID = "demo"

/** Series episode codes look like "S01E01". */
private val EPISODE_CODE_REGEX = Regex("^S\\d{2}E\\d{2}$")

/**
 * Single source of truth for UI content: every observe* Flow reads Room.
 * The active playlist id is resolved on each call; null when no playlist
 * exists at all (first run goes to Login).
 *
 * No demo data: the demo playlist was removed in 2026-11-23. A one-time
 * cleanup ([purgeLegacyDemoPlaylist]) deletes it from installs that still
 * carry it, so it never comes back.
 */
@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val db: StreamMasterDatabase,
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) : ContentRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // §4.36.6 — the cleanup scanner is a no-arg object (CleanupViewModel
        // contract), so it gets the database here; until then its scan()
        // returns an empty report. Same for the channel-timer scheduler
        // (§4.58): until installed, observeTimers() is empty.
        PlaylistCleanupScanner.install(db)
        ChannelTimerScheduler.install(db)
        scope.launch { runCatching { purgeLegacyDemoPlaylist() }.onFailure { Log.w(TAG, "Demo purge failed", it) } }
    }

    /** Null when no playlist exists at all (first run goes to Login). */
    private suspend fun activePlaylistId(): String? {
        val active = db.playlistDao().getActive()
        if (active != null) return active.id
        return db.playlistDao().getAll().firstOrNull()?.id
    }

    /**
     * One-time cleanup for installs that still carry the demo playlist
     * removed in 2026-11-23: deletes the playlist and all its content
     * (channels/films/series/episodes/bouquets/EPG) so first run goes to
     * Login. No-op when no demo playlist exists.
     */
    private suspend fun purgeLegacyDemoPlaylist() {
        val pid = LEGACY_DEMO_PLAYLIST_ID
        if (db.playlistDao().getById(pid) == null) return
        val channelIds = db.channelDao().getAll(pid).map { it.id }
        channelIds.forEach { db.epgDao().deleteRange(it, 0L, Long.MAX_VALUE) }
        db.seriesDao().getAll(pid).forEach { db.episodeDao().deleteBySeries(it.id) }
        db.channelDao().deleteByPlaylist(pid)
        db.filmDao().deleteByPlaylist(pid)
        db.seriesDao().deleteByPlaylist(pid)
        db.bouquetDao().deleteByPlaylist(pid)
        db.playlistDao().deleteById(pid)
        Log.i(TAG, "Purged legacy demo playlist")
    }

    override fun observeChannels(): Flow<List<Channel>> = flow {
        val pid = activePlaylistId()
        if (pid == null) emit(emptyList())
        else emitAll(db.channelDao().observeAll(pid).map { list -> list.map { it.toModel() } })
    }

    override fun observeFilms(): Flow<List<Film>> = flow {
        val pid = activePlaylistId()
        if (pid == null) emit(emptyList())
        else emitAll(db.filmDao().observeAll(pid).map { list -> list.map { it.toModel() } })
    }

    override fun observeSeries(): Flow<List<Series>> = flow {
        val pid = activePlaylistId()
        if (pid == null) emit(emptyList())
        else emitAll(db.seriesDao().observeAll(pid).map { list -> list.map { it.toModel() } })
    }

    /** §4.40 — favourites screen only: every playlist at once. */
    override fun observeAllChannels(): Flow<List<Channel>> =
        db.channelDao().observeAllChannels().map { list -> list.map { it.toModel() } }

    override fun observeAllFilms(): Flow<List<Film>> =
        db.filmDao().observeAllFilms().map { list -> list.map { it.toModel() } }

    override fun observeAllSeries(): Flow<List<Series>> =
        db.seriesDao().observeAllSeries().map { list -> list.map { it.toModel() } }

    /** §4.36.2 — genre search for the natural-language layer. */
    override suspend fun searchFilmsByGenre(genre: String?, newestFirst: Boolean): List<Film> {
        val entities = when {
            newestFirst -> db.filmDao().getByGenreNewest(genre)
            genre.isNullOrBlank() -> db.filmDao().getAllFilms()
            else -> db.filmDao().search(genre)
        }
        return entities.map { it.toModel() }
    }

    override suspend fun searchSeriesByGenre(genre: String?, newestFirst: Boolean): List<Series> {
        val entities = when {
            newestFirst -> db.seriesDao().getByGenreNewest(genre)
            genre.isNullOrBlank() -> db.seriesDao().getAllSeries()
            else -> db.seriesDao().search(genre)
        }
        return entities.map { it.toModel() }
    }

    override fun observeBouquets(kind: BouquetKind): Flow<List<Bouquet>> = flow {
        val pid = activePlaylistId()
        if (pid == null) emit(emptyList())
        else emitAll(db.bouquetDao().observeByKind(pid, kind.name).map { list -> list.map { it.toModel() } })
    }

    override fun observeEpg(channelId: String, fromMillis: Long, toMillis: Long): Flow<List<EpgProgram>> =
        db.epgDao().observePrograms(channelId, fromMillis, toMillis).map { list -> list.map { it.toModel() } }

    /** History is per profile (profiles, 2026-11-22): only the active
     *  profile's entries are observed. */
    override fun observeHistory(): Flow<List<HistoryEntry>> =
        settingsStore.settings.flatMapLatest { settings ->
            db.historyDao().observeByProfile(settings.activeProfileId)
                .map { list -> list.map { it.toModel() } }
        }

    override fun observeReminders(): Flow<List<Reminder>> =
        db.reminderDao().observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun search(query: String, filter: ContentKind?): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<SearchResult>()
        if (filter == null || filter == ContentKind.LIVE) {
            db.channelDao().search(q).forEach { c ->
                out += SearchResult(ContentKind.LIVE, c.id, c.customName ?: c.name, c.category, c.hue)
            }
        }
        if (filter == null || filter == ContentKind.FILM) {
            db.filmDao().search(q).forEach { f ->
                out += SearchResult(ContentKind.FILM, f.id, f.customName ?: f.title, f.genre, f.hue)
            }
        }
        if (filter == null || filter == ContentKind.SERIES) {
            db.seriesDao().search(q).forEach { s ->
                out += SearchResult(ContentKind.SERIES, s.id, s.customName ?: s.title, s.genre, s.hue)
            }
        }
        return out
    }

    override suspend fun getChannel(id: String): Channel? = db.channelDao().getById(id)?.toModel()
    override suspend fun getFilm(id: String): Film? = db.filmDao().getById(id)?.toModel()
    override suspend fun getSeries(id: String): Series? = db.seriesDao().getById(id)?.toModel()

    override suspend fun getEpisodes(seriesId: String, season: Int): List<Episode> {
        val stored = db.episodeDao().getEpisodes(seriesId, season)
        if (stored.isNotEmpty()) return stored.map { it.toModel() }
        return emptyList()
    }

    override suspend fun setFavorite(kind: ContentKind, id: String, favorite: Boolean) {
        when (kind) {
            ContentKind.LIVE -> db.channelDao().setFavorite(id, favorite)
            ContentKind.FILM -> db.filmDao().setFavorite(id, favorite)
            ContentKind.SERIES -> db.seriesDao().setFavorite(id, favorite)
        }
    }

    override suspend fun recordHistory(kind: ContentKind, id: String, title: String) {
        val profileId = settingsStore.settings.first().activeProfileId
        val entry = HistoryEntity(
            id = "$profileId:${kind.name}:$id", kind = kind.name, refId = id,
            title = title, timestampMillis = System.currentTimeMillis(),
            profileId = profileId
        )
        db.historyDao().upsert(entry)
        db.historyDao().trimTo20(profileId)
    }

    override suspend fun clearHistory() {
        val profileId = settingsStore.settings.first().activeProfileId
        db.historyDao().clearByProfile(profileId)
    }

    override suspend fun recordResume(kind: ContentKind, id: String, progressPercent: Int, remainingText: String) {
        when (kind) {
            ContentKind.FILM -> {
                if (progressPercent >= 95) db.filmDao().setResume(id, 0, "")
                else db.filmDao().setResume(id, progressPercent, remainingText)
            }
            ContentKind.SERIES -> {
                if (progressPercent >= 95) db.seriesDao().setResume(id, 0, "")
                else db.seriesDao().setResume(id, progressPercent, remainingText)
            }
            ContentKind.LIVE -> Unit // live has no resume point
        }
    }

    override suspend fun setReminder(channelId: String, programTitle: String, startMillis: Long) {
        db.reminderDao().upsert(ReminderEntity(channelId, programTitle, startMillis))
        // Arm the real alarm now: reminders were stored but never scheduled
        // before, so the "real reminder" from the spec never fired.
        runCatching {
            ReminderScheduler.scheduleReminder(context, Reminder(channelId, programTitle, startMillis))
        }
    }

    override suspend fun removeReminder(channelId: String, programTitle: String, startMillis: Long) {
        db.reminderDao().delete(channelId, programTitle, startMillis)
        runCatching {
            ReminderScheduler.cancelReminder(context, Reminder(channelId, programTitle, startMillis))
        }
    }

    /**
     * Persists a Manage-screen draft: bouquet order/hidden/names, channel
     * hidden/custom names/custom numbers/order/bouquet membership, and the
     * same customizations for films/series. Applied as upserts against the
     * stored entities so server fields (streamUrl, now/next, ratings) are kept.
     */
    override suspend fun saveManageDraft(
        bouquets: List<Bouquet>,
        channels: List<Channel>,
        films: List<Film>,
        series: List<Series>
    ) {
        val pid = activePlaylistId() ?: return
        db.bouquetDao().upsertAll(bouquets.map { it.toEntity(pid) })
        val storedChannels = db.channelDao().getAll(pid).associateBy { it.id }
        db.channelDao().upsertAll(channels.mapIndexed { idx, c ->
            val old = storedChannels[c.id]
            if (old == null) {
                c.toEntity(pid, idx)
            } else {
                old.copy(
                    name = c.customName ?: c.name,
                    customName = c.customName,
                    hidden = c.hidden,
                    customNum = c.customNum,
                    bouquetId = c.bouquetId,
                    orderIndex = idx
                )
            }
        })
        val storedFilms = db.filmDao().getAll(pid).associateBy { it.id }
        db.filmDao().upsertAll(films.mapIndexed { idx, f ->
            val old = storedFilms[f.id]
            if (old == null) f.toEntity(pid, idx)
            // Films carry hidden/customName on the model now, persisted like
            // channels (hide/show + rename survive Save).
            else old.copy(
                title = f.customName ?: f.title, customName = f.customName,
                hidden = f.hidden, orderIndex = idx
            )
        })
        val storedSeries = db.seriesDao().getAll(pid).associateBy { it.id }
        db.seriesDao().upsertAll(series.mapIndexed { idx, s ->
            val old = storedSeries[s.id]
            if (old == null) s.toEntity(pid, idx)
            else old.copy(
                title = s.customName ?: s.title, customName = s.customName,
                hidden = s.hidden, orderIndex = idx
            )
        })
    }

    override suspend fun setCustomNumber(channelId: String, number: Int?) {
        val n = number?.takeIf { it in 1..100 } ?: run {
            // null or out of range clears the number (out-of-range is ignored as a set).
            if (number == null) db.channelDao().setCustomNum(channelId, null)
            return
        }
        // Uniqueness: clear the same number from any other channel first.
        db.channelDao().clearCustomNum(n, channelId)
        db.channelDao().setCustomNum(channelId, n)
    }

    override suspend fun getStreamUrls(channelId: String): List<String> {
        // Catchup (§4.43): encoded id "catchup:<channelId>:<startMillis>:<durationMin>".
        // Resolves to the Xtream timeshift URL of the ACTIVE playlist.
        // The player agent handles kind="catchup" by playing this URL with
        // the real channel's metadata; Stalker/M3U have no catchup here.
        if (channelId.startsWith("catchup:")) {
            val parts = channelId.removePrefix("catchup:").split(":")
            if (parts.size == 3) {
                val realId = parts[0]
                val start = parts[1].toLongOrNull()
                val duration = parts[2].toIntOrNull()
                val playlist = db.playlistDao().getActive()
                    ?: db.playlistDao().getAll().firstOrNull()
                val streamId = realId.takeIf { it.startsWith("xt-") }
                    ?.removePrefix("xt-")?.toIntOrNull()
                if (playlist != null && start != null && duration != null && streamId != null) {
                    return listOf(
                        XtreamApi.buildTimeshiftUrl(
                            playlist.serverUrl, playlist.username, playlist.password,
                            streamId, start, duration
                        )
                    )
                }
            }
            return emptyList()
        }
        val entity = db.channelDao().getById(channelId)
        if (entity != null) {
            val backups = Converters.decodeStrings(entity.backupUrlsEncoded)
            return listOf(entity.streamUrl) + backups.filter { it.isNotBlank() && it != entity.streamUrl }
        }
        // Episode codes (S01E01): the player resolves series episodes through
        // this method (PlayerManagerImpl.resolveUrls). Episodes now carry
        // their resolved stream URL (XtreamApi -> Episode -> Room), so return
        // the first stored URL for this code. (Before the fix, real Xtream
        // episodes lost their stream id when mapped to Episode.code and
        // returned empty here, showing the error screen.)
        if (EPISODE_CODE_REGEX.matches(channelId)) {
            val byCode = db.episodeDao().getByCode(channelId)
            val stored = byCode.mapNotNull { it.streamUrl }.firstOrNull { it.isNotBlank() }
            if (stored != null) return listOf(stored)
        }
        return emptyList()
    }

    override suspend fun getEpisodeStreamUrls(seriesId: String, code: String): List<String> {
        val url = db.episodeDao().getBySeriesAndCode(seriesId, code)?.streamUrl
        return if (!url.isNullOrBlank()) listOf(url) else emptyList()
    }

    override suspend fun getRecentSearches(): List<String> =
        db.searchHistoryDao().getRecent().map { it.query }

    override suspend fun recordSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        db.searchHistoryDao().deleteByQuery(q)
        db.searchHistoryDao().insert(SearchHistoryEntity(query = q, timestampMillis = System.currentTimeMillis()))
        db.searchHistoryDao().trimTo10()
    }
}
