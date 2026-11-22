package com.streammaster.tv.data.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.streammaster.tv.data.model.Reminder

/**
 * Real program reminders: an exact AlarmManager alarm fires at program start
 * and ReminderReceiver posts a notification that deep-links to the channel
 * (streammaster://player/live/{channelId}).
 *
 * NOTE (build agent): ReminderReceiver must be declared in AndroidManifest.xml
 * (exported=false) for the alarm PendingIntent to fire.
 */
object ReminderScheduler {

    const val CHANNEL_ID = "reminders"
    const val EXTRA_CHANNEL_ID = "extra_channel_id"
    const val EXTRA_PROGRAM_TITLE = "extra_program_title"
    const val EXTRA_START_MILLIS = "extra_start_millis"

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rappels de programmes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alertes au début des programmes suivis" }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        // (Re-)enqueue the periodic background work.
        PlaylistRefreshWorker.enqueue(context)
        EpgSyncWorker.enqueue(context)
        SeriesLinkWorker.enqueue(context)
    }

    fun scheduleReminder(context: Context, reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_CHANNEL_ID, reminder.channelId)
            putExtra(EXTRA_PROGRAM_TITLE, reminder.programTitle)
            putExtra(EXTRA_START_MILLIS, reminder.startMillis)
        }
        // The request code includes the start time: the same program title
        // repeats on several days, and each day needs its own alarm.
        val requestCode = (reminder.channelId + "|" + reminder.programTitle + "|" + reminder.startMillis).hashCode()
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startMillis, pending)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startMillis, pending)
        }
    }

    fun cancelReminder(context: Context, reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = (reminder.channelId + "|" + reminder.programTitle + "|" + reminder.startMillis).hashCode()
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending)
        pending.cancel()
    }
}
