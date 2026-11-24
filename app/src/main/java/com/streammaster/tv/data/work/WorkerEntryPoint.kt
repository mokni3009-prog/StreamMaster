package com.streammaster.tv.data.work

import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.EpgRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.work.SeriesLinkScanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

/**
 * Hilt entry point for WorkManager workers. The workers are plain
 * CoroutineWorkers (no androidx.hilt:hilt-work dependency, which is not in
 * CONTRACT §2) and resolve their repositories through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun playlistRepository(): PlaylistRepository
    fun epgRepository(): EpgRepository
    fun seriesLinkScanner(): SeriesLinkScanner
    /** OP-10 — the update worker reads the user's updateUrl + the DI
     *  OkHttpClient (DNS/UA/timeouts) instead of a bare client. */
    fun settingsStore(): SettingsStore
    fun okHttpClient(): OkHttpClient
}
