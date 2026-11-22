package com.streammaster.tv.player

import android.app.Notification
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
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.ScheduledRecordingRepository
import com.streammaster.tv.data.work.RecordingScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RECORDING_CHANNEL_ID = "recording"
private const val RECORDING_NOTIFICATION_ID = 1002

/**
 * Foreground service that keeps an instant DVR recording alive after the
 * user leaves the player (method 2). The notification shows the channel
 * being recorded and a Stop action; stopping from the notification, from
 * the player REC button, or from the Enregistrements screen all end up in
 * [RecordingManager.stop].
 *
 * Also runs scheduled recordings (2026-11-22): ACTION_SCHEDULED_START
 * (from ScheduledRecordingReceiver) resolves the channel and starts an
 * instant recording of it.
 *
 * Foreground type is dataSync (downloading segments to a file), declared in
 * the manifest with FOREGROUND_SERVICE_DATA_SYNC.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var contentRepository: ContentRepository

    @Inject
    lateinit var scheduledRecordingRepository: ScheduledRecordingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(RECORDING_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        RECORDING_CHANNEL_ID,
                        getString(R.string.recording_notification_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.recording_notification_desc)
                    }
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            recordingManager.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SCHEDULED_START) {
            // Scheduled recording: go foreground immediately, then resolve
            // the channel and start an instant recording of it.
            startRecordingForeground("")
            val scheduledId = intent.getStringExtra(RecordingScheduler.EXTRA_SCHEDULED_ID)
            if (scheduledId == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            serviceScope.launch {
                val rec = scheduledRecordingRepository.getScheduled(scheduledId)
                val channel = rec?.let { contentRepository.getChannel(it.channelId) }
                if (rec == null || rec.status != "scheduled" || channel == null) {
                    stopSelf()
                } else if (!recordingManager.start(channel)) {
                    // Another recording is already running.
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }
        val recordingId = intent?.getStringExtra(EXTRA_RECORDING_ID) ?: return START_NOT_STICKY
        val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val path = intent.getStringExtra(EXTRA_PATH) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        startRecordingForeground(title)
        recordingManager.runRecording(recordingId, url, path) { stopSelf() }
        return START_NOT_STICKY
    }

    /** Foreground with the dataSync type on API 29+, plain on older APIs. */
    private fun startRecordingForeground(title: String) {
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                RECORDING_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(RECORDING_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RECORDING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.recording_in_progress))
            .setContentText(title)
            .addAction(0, getString(R.string.record_stop), stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        recordingManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RECORDING_ID = "recording_id"
        const val EXTRA_URL = "url"
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        const val ACTION_STOP = "com.streammaster.tv.action.STOP_RECORDING"
        const val ACTION_SCHEDULED_START = "com.streammaster.tv.action.SCHEDULED_START"
    }
}
