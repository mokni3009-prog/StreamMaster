package com.streammaster.tv.data.model

import kotlinx.coroutines.flow.Flow

enum class PlaylistType { XTREAM, STALKER, M3U }
enum class BouquetKind { LIVE, FILMS, SERIES }
enum class ContentKind { LIVE, FILM, SERIES }

data class Channel(
    val id: String,
    val num: String,
    val name: String,
    val code: String,
    val category: String,
    val quality: String,
    val nowTitle: String,
    val nowTime: String,
    val progress: Int,
    val hue: Int,
    val description: String,
    val next: List<Pair<String, String>>,
    val isFavorite: Boolean,
    val isBroken: Boolean,
    val streamUrl: String,
    val epgId: String?,
    val customNum: Int?,
    val hidden: Boolean,
    val customName: String?,
    val bouquetId: String
)

data class Film(
    val id: String,
    val title: String,
    val year: Int,
    val rating: Double,
    val quality: String,
    val hue: Int,
    val genre: String,
    val durationMin: Int,
    val isFavorite: Boolean,
    val recentProgress: Int,
    val remainingText: String,
    val description: String,
    val isBroken: Boolean,
    val streamUrl: String,
    val posterUrl: String?,
    /** YouTube trailer URL (Xtream `youtube_trailer`, series only — VOD
     *  list responses do not carry one; see TrailerOpener fallback). */
    val trailerUrl: String? = null,
    /** Manage hide/show + rename, persisted like Channel.hidden/customName. */
    val hidden: Boolean = false,
    val customName: String? = null
)

data class Series(
    val id: String,
    val title: String,
    val seasons: Int,
    val rating: Double,
    val quality: String,
    val hue: Int,
    val genre: String,
    val isFavorite: Boolean,
    val recentProgress: Int,
    val remainingText: String,
    val description: String,
    val isBroken: Boolean,
    val posterUrl: String?,
    /** YouTube trailer URL (Xtream `youtube_trailer` from get_series). */
    val trailerUrl: String? = null,
    /** Manage hide/show + rename, persisted like Channel.hidden/customName. */
    val hidden: Boolean = false,
    val customName: String? = null
)

data class Episode(
    val seriesId: String,
    val season: Int,
    val number: Int,
    val title: String,
    val durationMin: Int,
    val code: String,
    /** Full stream URL resolved at sync time (Xtream episode stream id ->
     *  series URL). Null when the episode has no resolvable URL. */
    val streamUrl: String? = null
)

enum class RecordingStatus { RECORDING, DONE, ERROR }

/** A film/series saved to watch later (per profile, 2026-11-22). */
data class WatchLaterItem(
    val kind: ContentKind,
    val refId: String,
    val title: String,
    val addedMillis: Long,
    val profileId: String = "default"
)

/** A user profile: per-person history + watch-later (2026-11-22). */
data class UserProfile(
    val id: String,
    val name: String,
    val hue: Int,
    val pin: String,
    val createdMillis: Long
)

/** A recording scheduled from the EPG (2026-11-22). */
data class ScheduledRecording(
    val id: String,
    val channelId: String,
    val channelName: String,
    val programTitle: String,
    val startMillis: Long,
    val endMillis: Long,
    /** "once" | "daily" | "weekly". */
    val repeat: String,
    /** "scheduled" | "done" | "cancelled". */
    val status: String
)

/** A series-link recording rule: record every future airing of this
 *  program title on this channel (2026-11-22). The scanner matches
 *  [normalizedTitle] against upcoming EPG programs on [channelId]. */
data class SeriesRecordingRule(
    val id: String,
    val channelId: String,
    val channelName: String,
    val programTitle: String,
    val normalizedTitle: String,
    val createdMillis: Long,
    val active: Boolean = true
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, DONE, ERROR }

/** A VOD title downloaded for offline watching (2026-11-22). */
data class Download(
    val id: String,
    val kind: ContentKind,
    val refId: String,
    val title: String,
    val coverUrl: String?,
    val fileName: String,
    val filePath: String,
    /** Direct file URL (mp4/mkv/ts); used to resume after a pause. */
    val sourceUrl: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val createdMillis: Long
)

/** One instant recording of a live channel (DVR, method 2: instant only). */
data class Recording(
    val id: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val startMillis: Long,
    val durationMs: Long,
    val filePath: String,
    val sizeBytes: Long,
    val status: RecordingStatus
)

data class Bouquet(
    val id: String,
    val name: String,
    val kind: BouquetKind,
    val order: Int,
    val hidden: Boolean
)

data class Playlist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val serverUrl: String,
    val username: String,
    val password: String,
    val portalUrl: String,
    val mac: String,
    val m3uUrl: String,
    val isActive: Boolean,
    val expiryDate: String,
    val connections: Int,
    val lastUpdate: String,
    val status: String,
    /** Optional second EPG source (XMLTV URL): merged per channel where the
     *  primary source has no programs. Null = provider EPG only. */
    val xmltvUrl: String? = null
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String
)

data class HistoryEntry(
    val id: String,
    val kind: ContentKind,
    val refId: String,
    val title: String,
    val timestampMillis: Long
)

data class Reminder(
    val channelId: String,
    val programTitle: String,
    val startMillis: Long
)

data class AppSettings(
    val lang: String = "fr",
    val dns: String = "auto",
    val dnsCustom: String = "",
    val ua: String = "default",
    val uaCustom: String = "",
    val format: String = "auto",
    val engine: String = "exo",
    val epgWindow: String = "24h",
    val autoResume: Boolean = false,
    val sleepMinutes: Int = 0,
    val pin: String = "",
    val lockedBouquets: Map<String, List<String>> = emptyMap(),
    val lockedItems: List<String> = emptyList(),
    /** Auto-start the app when the device boots (2026-11-22). */
    val autoStartOnBoot: Boolean = false,
    /** Lock the whole app with the PIN at launch (2026-11-22). */
    val appLock: Boolean = false,
    /** Kids mode: only kid categories shown, admin screens hidden (2026-11-22). */
    val kidsMode: Boolean = false,
    /** Match the TV refresh rate to the video frame rate (2026-11-22). */
    val frameRateSwitch: Boolean = true,
    /** External player package (e.g. org.videolan.vlc); "" = internal player. */
    val externalPlayer: String = "",
    /** SAF tree URI chosen for recordings; "" = app-private folder. */
    val recordingFolderUri: String = "",
    /** Active user profile id (2026-11-22). */
    val activeProfileId: String = "default",
    /** Update-check JSON URL (AppUpdateChecker); "" = check disabled. */
    val updateUrl: String = "",
    /** Smart failover to a backup channel on failure — opt-in, off by default (2026-11-22). */
    val smartFailover: Boolean = false,
    /** Match Center (live scores) in the player — opt-in, off by default (2026-11-22). */
    val matchCenter: Boolean = false,
    /** Goal alerts (2026-11-22): instant popup + system notification for every
     *  goal in today's matches — opt-in, off by default. */
    val goalAlerts: Boolean = false,
    /** Maximum speed (2026-11-22): use all available bandwidth — highest
     *  rendition forced, ABR uses 100% of the measured bandwidth. ON by
     *  default. The ceiling stays the ISP speed, the device and the
     *  channel's own bitrate. */
    val maxSpeed: Boolean = true,
    /** Phone web remote (2026-11-22): local HTTP server + QR pairing — opt-in, off by default. */
    val remoteEnabled: Boolean = false,
    /** 6-digit pairing code for the phone remote (shown as QR + text). */
    val remoteCode: String = ""
)

sealed class PlaybackItem {
    data class Live(val channel: Channel) : PlaybackItem()
    data class Movie(val film: Film) : PlaybackItem()
    data class EpisodeItem(val series: Series, val episode: Episode) : PlaybackItem()
    data class RecordingItem(val recording: Recording) : PlaybackItem()
    data class DownloadItem(val download: Download) : PlaybackItem()
}

data class SearchResult(
    val kind: ContentKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val hue: Int
)
