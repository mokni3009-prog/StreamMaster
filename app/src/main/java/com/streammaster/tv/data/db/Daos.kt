package com.streammaster.tv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun observeAll(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getAll(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    /** All channels across playlists — backup-URL recomputation (2026-11-22). */
    @Query("SELECT * FROM channels")
    suspend fun getAllChannels(): List<ChannelEntity>

    @Query("UPDATE channels SET backupUrlsEncoded = :encoded WHERE id = :id")
    suspend fun updateBackupUrls(id: String, encoded: String)

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE customNum = :number LIMIT 1")
    suspend fun getByCustomNum(number: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' OR nowTitle LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 50")
    suspend fun search(query: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChannelEntity)

    @Update
    suspend fun update(item: ChannelEntity)

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE channels SET customNum = NULL WHERE customNum = :number AND id != :exceptId")
    suspend fun clearCustomNum(number: Int, exceptId: String)

    @Query("UPDATE channels SET customNum = :number WHERE id = :id")
    suspend fun setCustomNum(id: String, number: Int?)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int
}

@Dao
interface FilmDao {
    @Query("SELECT * FROM films WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun observeAll(playlistId: String): Flow<List<FilmEntity>>

    @Query("SELECT * FROM films WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getAll(playlistId: String): List<FilmEntity>

    @Query("SELECT * FROM films WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FilmEntity?

    @Query("SELECT * FROM films WHERE title LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title ASC LIMIT 50")
    suspend fun search(query: String): List<FilmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FilmEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FilmEntity)

    @Update
    suspend fun update(item: FilmEntity)

    @Query("UPDATE films SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE films SET recentProgress = :progress, remainingText = :remaining WHERE id = :id")
    suspend fun setResume(id: String, progress: Int, remaining: String)

    @Query("DELETE FROM films WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM films")
    suspend fun count(): Int
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun observeAll(playlistId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getAll(playlistId: String): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE title LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY title ASC LIMIT 50")
    suspend fun search(query: String): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SeriesEntity)

    @Update
    suspend fun update(item: SeriesEntity)

    @Query("UPDATE series SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE series SET recentProgress = :progress, remainingText = :remaining WHERE id = :id")
    suspend fun setResume(id: String, progress: Int, remaining: String)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM series")
    suspend fun count(): Int
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND season = :season ORDER BY number ASC")
    suspend fun getEpisodes(seriesId: String, season: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY season ASC, number ASC")
    suspend fun getAllForSeries(seriesId: String): List<EpisodeEntity>

    /** All episodes sharing one code (e.g. "S01E01") across series — used to
     *  resolve an episode code to its stored stream URL in getStreamUrls. */
    @Query("SELECT * FROM episodes WHERE code = :code")
    suspend fun getByCode(code: String): List<EpisodeEntity>

    /** The exact episode for one series+code — S01E01 exists in every series,
     *  so code alone cannot identify an episode. */
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND code = :code LIMIT 1")
    suspend fun getBySeriesAndCode(seriesId: String, code: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteBySeries(seriesId: String)
}

@Dao
interface BouquetDao {
    @Query("SELECT * FROM bouquets WHERE playlistId = :playlistId AND kind = :kind ORDER BY `order` ASC")
    fun observeByKind(playlistId: String, kind: String): Flow<List<BouquetEntity>>

    @Query("SELECT * FROM bouquets WHERE playlistId = :playlistId ORDER BY `order` ASC")
    suspend fun getAll(playlistId: String): List<BouquetEntity>

    @Query("SELECT * FROM bouquets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BouquetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BouquetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BouquetEntity)

    @Update
    suspend fun update(item: BouquetEntity)

    @Query("DELETE FROM bouquets WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("DELETE FROM bouquets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlaylistEntity)

    @Query("UPDATE playlists SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE playlists SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg WHERE channelId = :channelId AND startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis ASC")
    fun observePrograms(channelId: String, fromMillis: Long, toMillis: Long): Flow<List<EpgEntity>>

    @Query("SELECT * FROM epg WHERE channelId = :channelId AND startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis ASC")
    suspend fun getPrograms(channelId: String, fromMillis: Long, toMillis: Long): List<EpgEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpgEntity>)

    @Query("DELETE FROM epg WHERE channelId = :channelId AND startMillis >= :fromMillis AND startMillis < :toMillis")
    suspend fun deleteRange(channelId: String, fromMillis: Long, toMillis: Long)

    @Query("DELETE FROM epg WHERE startMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY timestampMillis DESC LIMIT 20")
    fun observeByProfile(profileId: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    suspend fun clearByProfile(profileId: String)

    @Query("DELETE FROM history WHERE profileId = :profileId AND id NOT IN (SELECT id FROM history WHERE profileId = :profileId ORDER BY timestampMillis DESC LIMIT 20)")
    suspend fun trimTo20(profileId: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY startMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE startMillis >= :fromMillis ORDER BY startMillis ASC")
    suspend fun getUpcoming(fromMillis: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReminderEntity)

    @Query("DELETE FROM reminders WHERE channelId = :channelId AND programTitle = :programTitle AND startMillis = :startMillis")
    suspend fun delete(channelId: String, programTitle: String, startMillis: Long)

    @Query("DELETE FROM reminders WHERE startMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestampMillis DESC LIMIT 10")
    suspend fun getRecent(): List<SearchHistoryEntity>

    @Insert
    suspend fun insert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestampMillis DESC LIMIT 10)")
    suspend fun trimTo10()

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

@Dao
interface QoeDao {
    @Insert
    suspend fun insert(event: QoeEvent)

    @Query("SELECT * FROM qoe_events ORDER BY timestampMillis DESC LIMIT 500")
    suspend fun getRecent(): List<QoeEvent>

    @Query("DELETE FROM qoe_events WHERE timestampMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("SELECT COUNT(*) FROM qoe_events WHERE eventType = :type AND timestampMillis >= :sinceMillis")
    suspend fun countByTypeSince(type: String, sinceMillis: Long): Int
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later WHERE profileId = :profileId ORDER BY addedMillis DESC")
    fun observeByProfile(profileId: String): Flow<List<WatchLaterEntity>>

    @Query("SELECT * FROM watch_later ORDER BY addedMillis DESC")
    suspend fun getAll(): List<WatchLaterEntity>

    @Query("SELECT * FROM watch_later WHERE kind = :kind AND refId = :refId AND profileId = :profileId")
    suspend fun get(kind: String, refId: String, profileId: String): WatchLaterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE kind = :kind AND refId = :refId AND profileId = :profileId")
    suspend fun delete(kind: String, refId: String, profileId: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdMillis ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ScheduledRecordingDao {
    @Query("SELECT * FROM scheduled_recordings WHERE status = 'scheduled' ORDER BY startMillis ASC")
    fun observeScheduled(): Flow<List<ScheduledRecordingEntity>>

    @Query("SELECT * FROM scheduled_recordings WHERE id = :id")
    suspend fun getById(id: String): ScheduledRecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ScheduledRecordingEntity)

    @Query("DELETE FROM scheduled_recordings WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SeriesRecordingRuleDao {
    @Query("SELECT * FROM series_recording_rules ORDER BY createdMillis ASC")
    fun observeAll(): Flow<List<SeriesRecordingRuleEntity>>

    @Query("SELECT * FROM series_recording_rules WHERE active = 1")
    suspend fun getActive(): List<SeriesRecordingRuleEntity>

    @Query("SELECT * FROM series_recording_rules WHERE channelId = :channelId AND normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun getByChannelAndTitle(channelId: String, normalizedTitle: String): SeriesRecordingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SeriesRecordingRuleEntity)

    @Query("DELETE FROM series_recording_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdMillis DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE kind = :kind AND refId = :refId LIMIT 1")
    suspend fun getByRef(kind: String, refId: String): DownloadEntity?

    /** Oldest queued download — the sequential download loop's next job. */
    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdMillis ASC LIMIT 1")
    suspend fun getNextQueued(): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = :total, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, total: Long, status: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}
