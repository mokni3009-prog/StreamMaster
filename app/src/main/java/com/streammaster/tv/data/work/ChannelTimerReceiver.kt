package com.streammaster.tv.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * §4.58 — channel timer firing (2026-11-23).
 *
 * At the scheduled time, opens the channel through the existing player
 * deep link `streammaster://player/live/{channelId}` (same link the
 * reminder notifications use). MainActivity is `singleTask`, so:
 * - app closed / in background → cold/warm start lands on the channel;
 * - app open on another screen → `onNewIntent` re-navigates to the player.
 *
 * The receiver does NOT post a notification and does NOT wait for a tap:
 * the switch is automatic, as §4.58 requires. No PlayerViewModel call is
 * made from here — there is no bus command for "play channel by id"
 * (RemoteBus only has zap up/down), so the deep link is the switch
 * mechanism. While the player is already open on another channel,
 * MainActivity's `maybeNavigateToDeepLink` navigates to the new
 * channel (OP-13, fixed 2026-11-24) instead of leaving the link queued.
 *
 * NOTE: this receiver must be declared in AndroidManifest.xml
 * (`exported=false`), like ReminderReceiver — the manifest is owned by
 * another builder; flag it if it is missing.
 */
class ChannelTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = intent.getStringExtra(ChannelTimerScheduler.EXTRA_CHANNEL_ID) ?: return
        val deepLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("streammaster://player/live/$channelId")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { context.startActivity(deepLink) }
    }
}
