package com.streammaster.tv.data.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Fires at program start: posts a notification that opens the channel. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = intent.getStringExtra(ReminderScheduler.EXTRA_CHANNEL_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_PROGRAM_TITLE) ?: return
        val startMillis = intent.getLongExtra(ReminderScheduler.EXTRA_START_MILLIS, 0L)
        // One notification id per (channel, start time): the same title on
        // another day must not replace this notification.
        val notifId = (channelId + "|" + startMillis).hashCode()
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("streammaster://player/live/$channelId")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, notifId, deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Votre programme commence maintenant — appuyez pour regarder.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}
