package com.streammaster.tv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Room entities. Each maps 1:1 to a §3 model; mappers live in the repository impls. */

@Entity(
    tableName = "channels",
    indices = [Index("id"), Index("category"), Index("bouquetId"), Index("playlistId")]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
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
    /** Encoded as "time\u001ftitle" pairs joined by "\u001e". See Converters. */
    val nextEncoded: String,
    val isFavorite: Boolean,
    val isBroken: Boolean,
    val streamUrl: String,
    /** Extra mirror URLs, encoded with "\u001f" separator. Primary streamUrl comes first. */
    val backupUrlsEncoded: String,
    val epgId: String?,
    /** Channel logo URL (see Channel.logoUrl); added by Migration(6, 7). */
    val logoUrl: String? = null,
    val customNum: Int?,
    val hidden: Boolean,
    val customName: String?,
    val bouquetId: String,
    val playlistId: String,
    val orderIndex: Int
)

@Entity(
    tableName = "films",
    indices = [Index("id"), Index("genre"), Index("playlistId")]
)
data class FilmEntity(
    @PrimaryKey val id: String,
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
    val trailerUrl: String?,
    val hidden: Boolean,
    val customName: String?,
    val playlistId: String,
    val orderIndex: Int
)

@Entity(
    tableName = "series",
    indices = [Index("id"), Index("genre"), Index("playlistId")]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
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
    val trailerUrl: String?,
    val hidden: Boolean,
    val customName: String?,
    val playlistId: String,
    val orderIndex: Int
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["seriesId", "season", "number"],
    indices = [Index("seriesId")]
)
data class EpisodeEntity(
    val seriesId: String,
    val season: Int,
    val number: Int,
    val title: String,
    val durationMin: Int,
    val code: String,
    val streamUrl: String? = null,
    /** Resume point inside the episode, in ms (§4.40 Continue Watching:
     *  the player agent reads/writes it via EpisodeDao.setProgress; 0 =
     *  never started / finished). */
    val progressMs: Long = 0
)

@Entity(
    tableName = "bouquets",
    indices = [Index("id"), Index("kind"), Index("playlistId")]
)
data class BouquetEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** BouquetKind name. */
    val kind: String,
    val order: Int,
    val hidden: Boolean,
    val playlistId: String
)

@Entity(
    tableName = "playlists",
    indices = [Index("id"), Index("isActive")]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** PlaylistType name. */
    val type: String,
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
    val xmltvUrl: String? = null
)

@Entity(
    tableName = "epg",
    primaryKeys = ["channelId", "startMillis"],
    indices = [Index("channelId"), Index(value = ["channelId", "startMillis"])]
)
data class EpgEntity(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String
)

@Entity(
    tableName = "history",
    indices = [Index("id"), Index("timestampMillis"), Index("profileId")]
)
data class HistoryEntity(
    /** "profileId:kind:refId" — dedupe key (per profile). */
    @PrimaryKey val id: String,
    val kind: String,
    val refId: String,
    val title: String,
    val timestampMillis: Long,
    val profileId: String = "default"
)

@Entity(
    tableName = "reminders",
    primaryKeys = ["channelId", "programTitle", "startMillis"],
    indices = [Index("startMillis")]
)
data class ReminderEntity(
    val channelId: String,
    val programTitle: String,
    val startMillis: Long
)

@Entity(
    tableName = "search_history",
    indices = [Index("timestampMillis")]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestampMillis: Long
)

/** QoE events written by the player agent (player/QoeLogger.kt) via QoeDao. */
@Entity(
    tableName = "qoe_events",
    indices = [Index("timestampMillis"), Index("eventType")]
)
data class QoeEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    /** e.g. "start", "rebuffer", "abr_switch", "engine_switch", "stall", "error". */
    val eventType: String,
    val engine: String,
    val channelId: String?,
    val detail: String,
    val valueMs: Long
)

/** One instant recording of a live channel (DVR, method 2: instant only). */
@Entity(
    tableName = "recordings",
    indices = [Index("startMillis")]
)
data class RecordingEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val startMillis: Long,
    val durationMs: Long,
    val filePath: String,
    val sizeBytes: Long,
    /** RecordingStatus name. */
    val status: String
)

/** A film/series saved to watch later (per profile). */
@Entity(
    tableName = "watch_later",
    primaryKeys = ["kind", "refId", "profileId"],
    indices = [Index("addedMillis")]
)
data class WatchLaterEntity(
    val kind: String,
    val refId: String,
    val title: String,
    val addedMillis: Long,
    val profileId: String
)

/** A user profile (per-person history + watch-later). */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hue: Int,
    val pin: String,
    val createdMillis: Long
)

/** A recording scheduled from the EPG. */
@Entity(
    tableName = "scheduled_recordings",
    indices = [Index("startMillis")]
)
data class ScheduledRecordingEntity(
    @PrimaryKey val id: String,
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

/** A series-link recording rule: record every future airing of a program
 *  title on one channel (2026-11-22). */
@Entity(tableName = "series_recording_rules")
data class SeriesRecordingRuleEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val channelName: String,
    val programTitle: String,
    val normalizedTitle: String,
    val createdMillis: Long,
    val active: Boolean
)

/** A VOD title downloaded for offline watching (2026-11-22). */
@Entity(
    tableName = "downloads",
    indices = [Index("refId")]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    /** ContentKind name: FILM | SERIES. */
    val kind: String,
    val refId: String,
    val title: String,
    val coverUrl: String?,
    val fileName: String,
    val filePath: String,
    val sourceUrl: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    /** DownloadStatus name. */
    val status: String,
    val createdMillis: Long
)

/**
 * A series the user follows (§4.50): when a playlist refresh finds more
 * episodes than [lastEpisodeCount], a "new episode" notification is sent.
 * Table added in DB v8 (MIGRATION_7_8).
 */
@Entity(tableName = "series_follows")
data class SeriesFollow(
    @PrimaryKey val seriesId: String,
    val title: String,
    val lastEpisodeCount: Int = 0,
    val lastChecked: Long = 0
)

/**
 * A film genre the user follows (§4.57): when a playlist refresh finds a
 * different set of film ids for the genre than [lastFilmIdsHash], a
 * "new films" notification is sent. The hash is
 * `sortedFilmIds.joinToString("|").hashCode().toString()`; "" means "no
 * baseline yet — take the current set silently".
 * Table added in DB v8 (MIGRATION_7_8).
 */
@Entity(tableName = "genre_follows")
data class GenreFollow(
    @PrimaryKey val genre: String,
    val lastFilmIdsHash: String = "",
    val lastChecked: Long = 0
)

/**
 * A channel timer (§4.58): switch to [channelId] automatically at
 * [triggerAtMillis]. The §4.58 builder owns the UI + scheduler; this file
 * only declares the storage.
 * Table added in DB v8 (MIGRATION_7_8).
 */
@Entity(
    tableName = "channel_timers",
    indices = [Index("triggerAtMillis")]
)
data class ChannelTimer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val triggerAtMillis: Long,
    val enabled: Boolean = true
)

/**
 * A "My Playlist" bouquet (§4.62): a user-created bouquet of the
 * app-owned virtual playlist #11. [type] is LIVE | FILM | SERIES.
 * Table added in DB v8 (MIGRATION_7_8).
 */
@Entity(
    tableName = "myplaylist_bouquets",
    indices = [Index("type")]
)
data class MyPlaylistBouquet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** LIVE | FILM | SERIES. */
    val type: String,
    val sortOrder: Int = 0
)

/**
 * One item inside a "My Playlist" bouquet (§4.62): a COPY (reference) of
 * content from one of the user's subscriptions — the original stays in
 * its source playlist. [itemType] is CHANNEL | FILM | SERIES.
 * Table added in DB v8 (MIGRATION_7_8).
 */
@Entity(
    tableName = "myplaylist_items",
    indices = [Index("bouquetId")]
)
data class MyPlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bouquetId: Long,
    val sourcePlaylistId: String,
    /** CHANNEL | FILM | SERIES. */
    val itemType: String,
    val itemId: String
)

/**
 * A user star rating (§4.53): 1..5 stars on a film or series, fed into
 * the "Recommandé pour vous" recommendations (§4.36.1). Composite PK
 * (itemType, itemId); [itemType] is FILM | SERIES.
 * Table added in DB v8 (MIGRATION_7_8).
 */
@Entity(
    tableName = "user_ratings",
    primaryKeys = ["itemType", "itemId"]
)
data class UserRating(
    val itemType: String,
    val itemId: String,
    /** 1..5. */
    val stars: Int,
    val updatedAt: Long
)
