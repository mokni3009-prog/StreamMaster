package com.streammaster.tv.data.repository

import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Download
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.HistoryEntry
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.Reminder
import com.streammaster.tv.data.model.ScheduledRecording
import com.streammaster.tv.data.model.SearchResult
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.model.SeriesRecordingRule
import com.streammaster.tv.data.model.UserProfile
import com.streammaster.tv.data.model.WatchLaterItem
import kotlinx.coroutines.flow.Flow

/** CONTRACT §4 — exact signatures, plus the two additions the contract itself requests. */
interface ContentRepository {
    fun observeChannels(): Flow<List<Channel>>
    fun observeFilms(): Flow<List<Film>>
    fun observeSeries(): Flow<List<Series>>
    fun observeBouquets(kind: BouquetKind): Flow<List<Bouquet>>
    fun observeEpg(channelId: String, fromMillis: Long, toMillis: Long): Flow<List<EpgProgram>>
    fun observeHistory(): Flow<List<HistoryEntry>>
    fun observeReminders(): Flow<List<Reminder>>
    suspend fun search(query: String, filter: ContentKind?): List<SearchResult>
    suspend fun getChannel(id: String): Channel?
    suspend fun getFilm(id: String): Film?
    suspend fun getSeries(id: String): Series?
    suspend fun getEpisodes(seriesId: String, season: Int): List<Episode>
    suspend fun setFavorite(kind: ContentKind, id: String, favorite: Boolean)
    suspend fun recordHistory(kind: ContentKind, id: String, title: String)
    suspend fun clearHistory()
    suspend fun recordResume(kind: ContentKind, id: String, progressPercent: Int, remainingText: String)
    suspend fun setReminder(channelId: String, programTitle: String, startMillis: Long)
    suspend fun removeReminder(channelId: String, programTitle: String, startMillis: Long)
    suspend fun saveManageDraft(bouquets: List<Bouquet>, channels: List<Channel>, films: List<Film>, series: List<Series>)
    suspend fun setCustomNumber(channelId: String, number: Int?)

    /** CONTRACT §5: mirror URLs for the player fallback chain (primary streamUrl first). */
    suspend fun getStreamUrls(channelId: String): List<String>

    /** Exact series-episode URL(s) for playback (seriesId disambiguates the
     *  S01E01 code shared by every series). Empty when the episode has no
     *  stored stream URL. */
    suspend fun getEpisodeStreamUrls(seriesId: String, code: String): List<String>

    /** Recent search queries (max 10), newest first. */
    suspend fun getRecentSearches(): List<String>
    suspend fun recordSearch(query: String)
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun addPlaylist(p: Playlist): String
    suspend fun updatePlaylist(p: Playlist)
    suspend fun deletePlaylist(id: String)
    suspend fun activatePlaylist(id: String)
    suspend fun refreshPlaylist(id: String): Boolean
    suspend fun getActive(): Playlist?
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun setPin(pin: String)
    suspend fun isLocked(kind: ContentKind, id: String): Boolean
}

interface EpgRepository {
    suspend fun refreshEpg(playlistId: String)
    fun observePrograms(channelId: String, fromMillis: Long, toMillis: Long): Flow<List<EpgProgram>>
}

/** Instant recordings (DVR, method 2): persistence only — the recorder
 *  itself lives in [com.streammaster.tv.player.RecordingManager]. */
interface RecordingRepository {
    fun observeRecordings(): Flow<List<Recording>>
    suspend fun getRecording(id: String): Recording?
    suspend fun saveRecording(recording: Recording)
    suspend fun deleteRecording(id: String)
}

/** "Watch later" list, per profile. */
interface WatchLaterRepository {
    fun observeWatchLater(profileId: String): Flow<List<WatchLaterItem>>
    suspend fun isSaved(kind: ContentKind, refId: String, profileId: String): Boolean
    suspend fun toggle(kind: ContentKind, refId: String, title: String, profileId: String)
    suspend fun remove(kind: ContentKind, refId: String, profileId: String)
}

/** User profiles (per-person history + watch-later). */
interface ProfileRepository {
    fun observeProfiles(): Flow<List<UserProfile>>
    suspend fun getProfile(id: String): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
    suspend fun deleteProfile(id: String)
}

/** Recordings scheduled from the EPG. */
interface ScheduledRecordingRepository {
    fun observeScheduled(): Flow<List<ScheduledRecording>>
    suspend fun getScheduled(id: String): ScheduledRecording?
    suspend fun save(scheduled: ScheduledRecording)
    suspend fun delete(id: String)
}

/** Series-link recording rules (2026-11-22): record every future airing of
 *  a program title on one channel. */
interface SeriesRecordingRepository {
    fun observeRules(): Flow<List<SeriesRecordingRule>>
    suspend fun getRule(channelId: String, normalizedTitle: String): SeriesRecordingRule?
    suspend fun save(rule: SeriesRecordingRule)
    suspend fun delete(id: String)
}

/** Offline VOD downloads (2026-11-22). */
interface DownloadRepository {
    fun observeDownloads(): Flow<List<Download>>
    suspend fun getDownload(id: String): Download?
    suspend fun getByRef(kind: ContentKind, refId: String): Download?
    suspend fun getNextQueued(): Download?
    suspend fun save(download: Download)
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long, status: DownloadStatus)
    suspend fun delete(id: String)
}
