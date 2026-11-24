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
import kotlinx.coroutines.cancel
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

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RecordingScheduler.EXTRA_SCHEDULED_ID) ?: return
        // OP-08: the old code launched coroutines from a never-cancelled
        // scope with no goAsync() (the process could be killed before the
        // re-schedule ran), and the STOP branch started the service with a
        // bare startService that crashes on Android 12+ when a foreground
        // service start from the background is refused. Everything now
        // runs in one goAsync() coroutine on a fresh per-receive scope,
        // and both branches use the guarded startForegroundService.
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    RecordingScheduler.ACTION_START -> {
                        val serviceIntent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_SCHEDULED_START
                            putExtra(RecordingScheduler.EXTRA_SCHEDULED_ID, id)
                        }
                        startRecordingService(context, serviceIntent)
                        // Re-schedule the next occurrence of a repeating recording.
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
                    RecordingScheduler.ACTION_STOP -> {
                        val serviceIntent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_STOP
                        }
                        startRecordingService(context, serviceIntent)
                        val rec = scheduledRecordingRepository.getScheduled(id) ?: return@launch
                        if (rec.repeat == "once" && rec.status == "scheduled") {
                            scheduledRecordingRepository.save(rec.copy(status = "done"))
                        }
                    }
                }
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    /** Guarded: Android 12+ can refuse a foreground service started from
     *  the background (ForegroundServiceStartNotAllowedException). */
    private fun startRecordingService(context: Context, serviceIntent: Intent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
