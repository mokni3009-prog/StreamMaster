package com.streammaster.tv.data.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.streammaster.tv.data.model.ScheduledRecording

/**
 * Scheduled recordings (2026-11-22): exact AlarmManager alarms start and
 * stop a recording at the program's boundaries. Two alarms per recording
 * (start + stop); a daily/weekly repeat re-schedules itself at start time.
 *
 * Same pattern as [ReminderScheduler]: exact alarms need the
 * SCHEDULE_EXACT_ALARM permission (Android 12+) — when not granted, the
 * inexact fallback is used and the recording may start late.
 */
object RecordingScheduler {

    const val ACTION_START = "com.streammaster.tv.action.START_SCHEDULED_RECORDING"
    const val ACTION_STOP = "com.streammaster.tv.action.STOP_SCHEDULED_RECORDING"
    const val EXTRA_SCHEDULED_ID = "extra_scheduled_id"

    fun schedule(context: Context, rec: ScheduledRecording) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startIntent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_SCHEDULED_ID, rec.id)
        }
        val startPending = PendingIntent.getBroadcast(
            context, requestCode(rec.id, "start"), startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(am, rec.startMillis, startPending)
        val stopIntent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_SCHEDULED_ID, rec.id)
        }
        val stopPending = PendingIntent.getBroadcast(
            context, requestCode(rec.id, "stop"), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExact(am, rec.endMillis, stopPending)
    }

    fun cancel(context: Context, rec: ScheduledRecording) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startIntent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_START
        }
        val startPending = PendingIntent.getBroadcast(
            context, requestCode(rec.id, "start"), startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(startPending)
        startPending.cancel()
        val stopIntent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getBroadcast(
            context, requestCode(rec.id, "stop"), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(stopPending)
        stopPending.cancel()
    }

    /** LG-16: start/stop used to be id.hashCode() and id.hashCode()+1 — the
     *  stop code of one recording could equal the start code of another
     *  (cancelling / firing the wrong alarm). Namespaced hashes keep the
     *  two alarms in separate spaces. */
    private fun requestCode(id: String, which: String): Int = ("rec-$which|$id").hashCode()

    private fun setExact(am: AlarmManager, atMillis: Long, pending: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }
}
