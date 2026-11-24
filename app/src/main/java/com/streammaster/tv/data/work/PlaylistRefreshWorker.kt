package com.streammaster.tv.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

/**
 * Periodic playlist refresh (every 12h, network CONNECTED).
 * Refreshes the active playlist; merge preserves user customizations.
 * Plain CoroutineWorker resolving its repository via WorkerEntryPoint
 * (no hilt-work dependency).
 */
class PlaylistRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val playlistRepository by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext, WorkerEntryPoint::class.java
        ).playlistRepository()
    }

    override suspend fun doWork(): Result {
        val active = playlistRepository.getActive() ?: return Result.success()
        val ok = runCatching { playlistRepository.refreshPlaylist(active.id) }.getOrDefault(false)
        if (ok) return Result.success()
        // OP-19: a permanently failing refresh (bad credentials, dead
        // server) used to retry forever — cap the retries, then fail so
        // WorkManager stops rescheduling.
        return if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "playlist-refresh"
        /** OP-19 — retries are for transient failures only. */
        const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
