package com.streammaster.tv.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.streammaster.tv.R
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DOWNLOAD_CHANNEL_ID = "downloads"
private const val DOWNLOAD_NOTIFICATION_ID = 1003

/**
 * Foreground service that keeps an offline VOD download alive while the
 * user leaves the app (method: same dataSync foreground type as the DVR
 * recording service). The notification shows the file being downloaded
 * with its progress and a Cancel action. It stops itself when no
 * download is active; [VodDownloadManager] restarts it for the next
 * queued file.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var downloadManager: VodDownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // R-03: a single collector for the life of the service (started once in
    // onCreate), not one per onStartCommand — repeated starts used to pile
    // up parallel collectors competing on startForeground/stopSelf.
    private var collectJob: Job? = null
    // Only stop the service once a download has actually been seen: the
    // service is started just before the row flips to DOWNLOADING, so an
    // early "no active download" emission must not kill it mid-queue.
    private var seenActiveDownload = false

    companion object {
        const val ACTION_CANCEL = "com.streammaster.tv.action.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        DOWNLOAD_CHANNEL_ID,
                        getString(R.string.download_notification_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.download_notification_desc)
                    }
                )
            }
        }
        collectJob = serviceScope.launch {
            downloadRepository.observeDownloads().collectLatest { list ->
                val active = list.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                if (active == null) {
                    // Queue empty (or between files): stop, but only after
                    // at least one download was seen — the manager restarts
                    // the service for the next file.
                    if (seenActiveDownload) stopSelf()
                    return@collectLatest
                }
                seenActiveDownload = true
                val pct = if (active.totalBytes > 0) {
                    (active.downloadedBytes * 100 / active.totalBytes).toInt().coerceIn(0, 100)
                } else 0
                startForegroundNotification(active.title, pct, active.totalBytes)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            downloadManager.cancelActive()
            return START_NOT_STICKY
        }
        startForegroundNotification(getString(R.string.download_starting), 0, -1)
        return START_NOT_STICKY
    }

    private fun startForegroundNotification(title: String, progressPct: Int, totalBytes: Long) {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setContentTitle(getString(R.string.download_notification_title))
            .setContentText(title)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setProgress(100, progressPct, totalBytes < 0)
            .addAction(0, getString(R.string.cancel), cancelIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DOWNLOAD_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
