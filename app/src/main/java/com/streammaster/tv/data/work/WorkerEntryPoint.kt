package com.streammaster.tv.data.work

import com.streammaster.tv.data.repository.EpgRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.work.SeriesLinkScanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

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
}
