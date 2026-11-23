package com.streammaster.tv.di

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.streammaster.tv.data.api.StalkerApi
import com.streammaster.tv.data.api.XtreamApi
import com.streammaster.tv.data.db.QoeDao
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.network.NetworkFactory
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.ContentRepositoryImpl
import com.streammaster.tv.data.repository.DownloadRepository
import com.streammaster.tv.data.repository.DownloadRepositoryImpl
import com.streammaster.tv.data.repository.EpgRepository
import com.streammaster.tv.data.repository.EpgRepositoryImpl
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.PlaylistRepositoryImpl
import com.streammaster.tv.data.repository.ProfileRepository
import com.streammaster.tv.data.repository.ProfileRepositoryImpl
import com.streammaster.tv.data.repository.RecordingRepository
import com.streammaster.tv.data.repository.RecordingRepositoryImpl
import com.streammaster.tv.data.repository.ScheduledRecordingRepository
import com.streammaster.tv.data.repository.ScheduledRecordingRepositoryImpl
import com.streammaster.tv.data.repository.SeriesRecordingRepository
import com.streammaster.tv.data.repository.SeriesRecordingRepositoryImpl
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.repository.SettingsRepositoryImpl
import com.streammaster.tv.data.repository.WatchLaterRepository
import com.streammaster.tv.data.repository.WatchLaterRepositoryImpl
import com.streammaster.tv.data.update.AppUpdateChecker
import com.streammaster.tv.player.LoggingQoeStore
import com.streammaster.tv.player.PlayerManager
import com.streammaster.tv.player.PlayerManagerImpl
import com.streammaster.tv.player.QoeStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import javax.inject.Singleton

/**
 * CONTRACT §9. Provides database, DAOs, the 4 repositories, OkHttpClient
 * (DNS/User-Agent from settings, read synchronously at build time),
 * CronetEngine, Coil ImageLoader and the PlayerManager binding.
 *
 * NOTE: PlayerManagerImpl and player.PlayerFactory live in agent 3's
 * player/ package and are referenced here exactly as the contract names them.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StreamMasterDatabase =
        StreamMasterDatabase.create(context)

    @Provides
    fun provideChannelDao(db: StreamMasterDatabase) = db.channelDao()

    @Provides
    fun provideFilmDao(db: StreamMasterDatabase) = db.filmDao()

    @Provides
    fun provideSeriesDao(db: StreamMasterDatabase) = db.seriesDao()

    @Provides
    fun provideEpisodeDao(db: StreamMasterDatabase) = db.episodeDao()

    @Provides
    fun provideBouquetDao(db: StreamMasterDatabase) = db.bouquetDao()

    @Provides
    fun providePlaylistDao(db: StreamMasterDatabase) = db.playlistDao()

    @Provides
    fun provideEpgDao(db: StreamMasterDatabase) = db.epgDao()

    @Provides
    fun provideHistoryDao(db: StreamMasterDatabase) = db.historyDao()

    @Provides
    fun provideReminderDao(db: StreamMasterDatabase) = db.reminderDao()

    @Provides
    fun provideSearchHistoryDao(db: StreamMasterDatabase) = db.searchHistoryDao()

    @Provides
    fun provideQoeDao(db: StreamMasterDatabase): QoeDao = db.qoeDao()

    @Provides
    fun provideRecordingDao(db: StreamMasterDatabase) = db.recordingDao()

    @Provides
    fun provideWatchLaterDao(db: StreamMasterDatabase) = db.watchLaterDao()

    @Provides
    fun provideProfileDao(db: StreamMasterDatabase) = db.profileDao()

    @Provides
    fun provideScheduledRecordingDao(db: StreamMasterDatabase) = db.scheduledRecordingDao()

    @Provides
    fun provideSeriesRecordingRuleDao(db: StreamMasterDatabase) = db.seriesRecordingRuleDao()

    @Provides
    fun provideDownloadDao(db: StreamMasterDatabase) = db.downloadDao()

    @Provides
    fun provideSeriesFollowDao(db: StreamMasterDatabase) = db.seriesFollowDao()

    @Provides
    fun provideGenreFollowDao(db: StreamMasterDatabase) = db.genreFollowDao()

    @Provides
    fun provideChannelTimerDao(db: StreamMasterDatabase) = db.channelTimerDao()

    @Provides
    fun provideMyPlaylistBouquetDao(db: StreamMasterDatabase) = db.myPlaylistBouquetDao()

    @Provides
    fun provideMyPlaylistItemDao(db: StreamMasterDatabase) = db.myPlaylistItemDao()

    @Provides
    fun provideUserRatingDao(db: StreamMasterDatabase) = db.userRatingDao()

    @Provides
    @Singleton
    fun provideRecordingRepository(
        db: StreamMasterDatabase
    ): RecordingRepository = RecordingRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideWatchLaterRepository(
        db: StreamMasterDatabase
    ): WatchLaterRepository = WatchLaterRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideProfileRepository(
        db: StreamMasterDatabase
    ): ProfileRepository = ProfileRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideScheduledRecordingRepository(
        db: StreamMasterDatabase
    ): ScheduledRecordingRepository = ScheduledRecordingRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideSeriesRecordingRepository(
        db: StreamMasterDatabase
    ): SeriesRecordingRepository = SeriesRecordingRepositoryImpl(db)

    @Provides
    @Singleton
    fun provideDownloadRepository(
        db: StreamMasterDatabase
    ): DownloadRepository = DownloadRepositoryImpl(db)

    /**
     * Assembly fix (coordinator): QoeLogger requires a QoeStore binding.
     * Until a Room-backed RoomQoeStore is added, QoE events go to logcat
     * via LoggingQoeStore (see QoeLogger.kt KDoc).
     */
    @Provides
    @Singleton
    fun provideQoeStore(impl: LoggingQoeStore): QoeStore = impl

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        settingsStore: SettingsStore,
        db: StreamMasterDatabase
    ): SettingsRepository = SettingsRepositoryImpl(settingsStore, db)

    @Provides
    @Singleton
    fun provideContentRepository(
        db: StreamMasterDatabase,
        @ApplicationContext context: Context,
        settingsStore: SettingsStore
    ): ContentRepository = ContentRepositoryImpl(db, context, settingsStore)

    @Provides
    @Singleton
    fun providePlaylistRepository(
        db: StreamMasterDatabase,
        okHttpClient: OkHttpClient,
        xtreamApi: XtreamApi,
        stalkerApi: StalkerApi,
        settingsStore: SettingsStore,
        @ApplicationContext context: Context
    ): PlaylistRepository =
        PlaylistRepositoryImpl(db, okHttpClient, xtreamApi, stalkerApi, settingsStore, context)

    @Provides
    @Singleton
    fun provideEpgRepository(
        db: StreamMasterDatabase,
        xtreamApi: XtreamApi,
        stalkerApi: StalkerApi,
        okHttpClient: OkHttpClient,
        settingsStore: SettingsStore
    ): EpgRepository =
        EpgRepositoryImpl(db, xtreamApi, stalkerApi, okHttpClient, settingsStore)

    @Provides
    @Singleton
    fun provideXtreamApi(okHttpClient: OkHttpClient): XtreamApi = XtreamApi(okHttpClient)

    @Provides
    @Singleton
    fun provideStalkerApi(okHttpClient: OkHttpClient): StalkerApi = StalkerApi(okHttpClient)

    @Provides
    @Singleton
    fun provideAppUpdateChecker(okHttpClient: OkHttpClient): AppUpdateChecker =
        AppUpdateChecker(okHttpClient)

    /**
     * OkHttpClient with DNS/User-Agent from settings — read synchronously
     * (first value) at build time, per CONTRACT §9. Settings changes that
     * affect networking require an app restart to take effect on this client;
     * the player reads settings again when it (re)creates its data sources.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(settingsRepository: SettingsRepository): OkHttpClient {
        val settings = runBlocking { settingsRepository.settings.first() }
        return NetworkFactory.buildOkHttpClient(settings)
    }

    @Provides
    @Singleton
    fun provideCronetEngine(@ApplicationContext context: Context): CronetEngine? =
        NetworkFactory.createCronetEngine(context)

    /**
     * Coil ImageLoader: disk cache 200 MB normal, 80 MB on low-RAM devices
     * (SPEC §10). Low-RAM is read here via ActivityManager directly, because
     * util/DeviceCaps (agent 1) does not exist yet — same check, same result.
     */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowRam = am.isLowRamDevice
        val diskBytes = if (lowRam) 80L * 1024 * 1024 else 200L * 1024 * 1024
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(if (lowRam) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    /** Binds the player agent's implementation as the app-wide PlayerManager. */
    @Provides
    @Singleton
    fun providePlayerManager(impl: PlayerManagerImpl): PlayerManager = impl
}
