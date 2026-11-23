package com.streammaster.tv.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.streammaster.tv.data.backup.BackupManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * §4.59 — automatic weekly backup (2026-11-23).
 *
 * Every 7 days (WorkManager periodic work, no network needed) exports a
 * backup with [BackupManager] into a fixed app-private folder
 * (`filesDir/backups/stream-master-backup-<yyyyMMdd-HHmm>.json`) — no
 * picker, no user action. Only the last [KEEP_LAST] files are kept;
 * older ones are deleted on each run.
 *
 * NOTE (per task): the automatic copy is ALWAYS plain JSON (no password).
 * A password-protected backup stays a manual action from Settings
 * (§4.42) — the worker never sees, stores or invents a password. There
 * is no saved SAF backup folder setting, so the fixed app-private folder
 * is the destination (not the recordings SAF folder, which belongs to
 * recordings).
 *
 * Enabled/disabled by AppSettings.autoBackupWeekly: SettingsViewModel
 * enqueues on save when on, cancels when off; BootReceiver re-enqueues
 * after a reboot when still on (WorkManager normally survives reboots,
 * this is a belt-and-braces re-enqueue with KEEP, so it never duplicates).
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val backupManager by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext, BackupWorkerEntryPoint::class.java
        ).backupManager()
    }

    override suspend fun doWork(): Result {
        return try {
            val json = backupManager.export()
            val dir = File(applicationContext.filesDir, BACKUP_DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val file = File(dir, "stream-master-backup-$stamp.json")
            file.writeText(json)
            pruneOld(dir)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun pruneOld(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("stream-master-backup-") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP_LAST).forEach { runCatching { it.delete() } }
    }

    companion object {
        const val UNIQUE_NAME = "auto-backup-weekly"
        const val BACKUP_DIR = "backups"

        /** Weekly backups kept on disk; older ones are deleted. */
        const val KEEP_LAST = 4

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

/**
 * Hilt entry point for [BackupWorker]. Declared here (not in
 * WorkerEntryPoint.kt) so the worker resolves its [BackupManager] without
 * touching the shared entry-point file owned by the other builders.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun backupManager(): BackupManager
}
