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
        QoeEvent::class
    ],
    version = 7,
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

    companion object {
        const val NAME = "stream_master.db"

        /** 6 -> 7 (zero-diff C-3): channel logos. Nullable column, so
         *  existing rows keep their data and read back NULL (no logo). */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN logoUrl TEXT")
            }
        }

        fun create(context: Context): StreamMasterDatabase =
            Room.databaseBuilder(context, StreamMasterDatabase::class.java, NAME)
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
    }
}
