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

/** Periodic series-link scan (every 6h, network CONNECTED): schedules the
 *  upcoming airings of every active series-recording rule. */
class SeriesLinkWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
    }

    override suspend fun doWork(): Result {
        return try {
            entryPoint.seriesLinkScanner().scan()
            Result.success()
        } catch (e: Exception) {
            // OP-19: cap retries — a permanently failing scan must not
            // reschedule forever.
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "series-link-scan"
        /** OP-19 — retries are for transient failures only. */
        const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SeriesLinkWorker>(6, TimeUnit.HOURS)
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
