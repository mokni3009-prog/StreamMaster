package com.streammaster.tv.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.streammaster.tv.data.repository.ScheduledRecordingRepository
import com.streammaster.tv.player.RecordingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires a scheduled recording (2026-11-22). The receiver itself does no
 * recording — it forwards to [RecordingService], which runs the foreground
 * service and the recorder.
 *
 * - ACTION_START: starts the recording via the service; a daily/weekly
 *   repeat re-schedules its next occurrence here.
 * - ACTION_STOP: asks the service to stop the recording; a one-off
 *   recording is marked "done" here.
 */
@AndroidEntryPoint
class ScheduledRecordingReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduledRecordingRepository: ScheduledRecordingRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RecordingScheduler.EXTRA_SCHEDULED_ID) ?: return
        when (intent.action) {
            RecordingScheduler.ACTION_START -> {
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_SCHEDULED_START
                    putExtra(RecordingScheduler.EXTRA_SCHEDULED_ID, id)
                }
                // Guarded: Android 12+ can refuse a foreground service started
                // from the background (ForegroundServiceStartNotAllowedException).
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
                // Re-schedule the next occurrence of a repeating recording.
                scope.launch {
                    val rec = scheduledRecordingRepository.getScheduled(id) ?: return@launch
                    if (rec.status != "scheduled") return@launch
                    val period = when (rec.repeat) {
                        "daily" -> 86_400_000L
                        "weekly" -> 7 * 86_400_000L
                        else -> return@launch
                    }
                    val next = rec.copy(
                        startMillis = rec.startMillis + period,
                        endMillis = rec.endMillis + period
                    )
                    scheduledRecordingRepository.save(next)
                    RecordingScheduler.schedule(context, next)
                }
            }
            RecordingScheduler.ACTION_STOP -> {
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_STOP
                }
                context.startService(serviceIntent)
                scope.launch {
                    val rec = scheduledRecordingRepository.getScheduled(id) ?: return@launch
                    if (rec.repeat == "once" && rec.status == "scheduled") {
                        scheduledRecordingRepository.save(rec.copy(status = "done"))
                    }
                }
            }
        }
    }
}
