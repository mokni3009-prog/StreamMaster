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

/** Periodic EPG sync (every 6h, network CONNECTED), today + tomorrow first. */
class EpgSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
    }
    private val epgRepository get() = entryPoint.epgRepository()
    private val playlistRepository get() = entryPoint.playlistRepository()

    override suspend fun doWork(): Result {
        val active = playlistRepository.getActive() ?: return Result.success()
        return try {
            epgRepository.refreshEpg(active.id)
            Result.success()
        } catch (e: Exception) {
            // OP-19: cap retries — a permanently failing sync must not
            // reschedule forever.
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "epg-sync"
        /** OP-19 — retries are for transient failures only. */
        const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(6, TimeUnit.HOURS)
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
