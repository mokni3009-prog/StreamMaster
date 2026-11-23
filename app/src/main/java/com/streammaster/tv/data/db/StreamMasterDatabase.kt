package com.streammaster.tv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChannelEntity::class,
        FilmEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        BouquetEntity::class,
        PlaylistEntity::class,
        EpgEntity::class,
        HistoryEntity::class,
        ReminderEntity::class,
        SearchHistoryEntity::class,
        RecordingEntity::class,
        WatchLaterEntity::class,
        ProfileEntity::class,
        ScheduledRecordingEntity::class,
        SeriesRecordingRuleEntity::class,
        DownloadEntity::class,
        QoeEvent::class,
        SeriesFollow::class,
        GenreFollow::class,
        ChannelTimer::class,
        MyPlaylistBouquet::class,
        MyPlaylistItem::class,
        UserRating::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StreamMasterDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun filmDao(): FilmDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun bouquetDao(): BouquetDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun epgDao(): EpgDao
    abstract fun historyDao(): HistoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun recordingDao(): RecordingDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun profileDao(): ProfileDao
    abstract fun scheduledRecordingDao(): ScheduledRecordingDao
    abstract fun seriesRecordingRuleDao(): SeriesRecordingRuleDao
    abstract fun downloadDao(): DownloadDao
    abstract fun qoeDao(): QoeDao
    abstract fun seriesFollowDao(): SeriesFollowDao
    abstract fun genreFollowDao(): GenreFollowDao
    abstract fun channelTimerDao(): ChannelTimerDao
    abstract fun myPlaylistBouquetDao(): MyPlaylistBouquetDao
    abstract fun myPlaylistItemDao(): MyPlaylistItemDao
    abstract fun userRatingDao(): UserRatingDao

    companion object {
        const val NAME = "stream_master.db"

        /** 6 -> 7 (zero-diff C-3): channel logos. Nullable column, so
         *  existing rows keep their data and read back NULL (no logo). */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN logoUrl TEXT")
            }
        }

        /** 7 -> 8: the six new tables (§4.50/§4.57/§4.58/§4.62/§4.53) +
         *  the episode resume column. CREATE TABLE statements mirror
         *  Entities.kt exactly (table/column names, PKs, defaults).
         *  The episodes ALTER is guarded by a PRAGMA check: a fresh
         *  install already has progressMs (Room creates tables from the
         *  entities), so a bare ALTER would crash the 7 -> 8 upgrade
         *  on those databases; only v7 databases upgraded from an older
         *  install lack the column. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `series_follows` (" +
                        "`seriesId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`lastEpisodeCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastChecked` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`seriesId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `genre_follows` (" +
                        "`genre` TEXT NOT NULL, `lastFilmIdsHash` TEXT NOT NULL DEFAULT '', " +
                        "`lastChecked` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`genre`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `channel_timers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, " +
                        "`triggerAtMillis` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channel_timers_triggerAtMillis` " +
                        "ON `channel_timers` (`triggerAtMillis`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `myplaylist_bouquets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_myplaylist_bouquets_type` " +
                        "ON `myplaylist_bouquets` (`type`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `myplaylist_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bouquetId` INTEGER NOT NULL, `sourcePlaylistId` TEXT NOT NULL, " +
                        "`itemType` TEXT NOT NULL, `itemId` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_myplaylist_items_bouquetId` " +
                        "ON `myplaylist_items` (`bouquetId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_ratings` (" +
                        "`itemType` TEXT NOT NULL, `itemId` TEXT NOT NULL, " +
                        "`stars` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`itemType`, `itemId`))"
                )
                val hasProgress = db.query("PRAGMA table_info(`episodes`)").use { cursor ->
                    var found = false
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIdx) == "progressMs") { found = true; break }
                    }
                    found
                }
                if (!hasProgress) {
                    db.execSQL("ALTER TABLE episodes ADD COLUMN progressMs INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun create(context: Context): StreamMasterDatabase =
            Room.databaseBuilder(context, StreamMasterDatabase::class.java, NAME)
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
    }
}
