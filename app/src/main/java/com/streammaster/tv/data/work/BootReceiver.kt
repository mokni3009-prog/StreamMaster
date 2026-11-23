package com.streammaster.tv.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.matchcenter.GoalAlertService
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.toModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Auto-start on boot (2026-11-22): launches the app when the device boots,
 * only when the user enabled it in Settings. Note: some Android TV OEMs
 * restrict background activity starts — on those devices the user must
 * allow autostart for the app in the system settings.
 *
 * §4.42 (2026-11-23) — re-arm on BOOT_COMPLETED: AlarmManager alarms do
 * NOT survive a reboot, so every saved EPG reminder (table `reminders`)
 * and every scheduled recording (table `scheduled_recordings`, status
 * "scheduled") is re-scheduled here via [ReminderScheduler] and
 * [RecordingScheduler]. Past reminders are skipped by the DAO query
 * (`getUpcoming(now)`); past recordings (already ended) are skipped by
 * the `endMillis > now` check. The weekly auto-backup (§4.59) is also
 * re-enqueued when still enabled (WorkManager normally survives a
 * reboot; KEEP makes the re-enqueue a no-op when already scheduled).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var db: StreamMasterDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking {
            SettingsStore(context).settings.first()
        }
        // Goal alerts (2026-11-22): restart the alert service after a
        // reboot when the user left it enabled — independent of the
        // auto-start-on-boot app launch below. Same OR gate as
        // GoalAlertManager (§4.36.8): goalAlerts OR smartMatchAlerts.
        if (settings.goalAlerts || settings.smartMatchAlerts) {
            runCatching { GoalAlertService.start(context) }
        }
        // §4.42 — re-arm saved alarms (async: DB reads must not block the
        // receiver's main thread; goAsync keeps the process alive).
        val pending = goAsync()
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                runCatching {
                    db.reminderDao().getUpcoming(now).forEach { entity ->
                        ReminderScheduler.scheduleReminder(context, entity.toModel())
                    }
                }
                runCatching {
                    db.scheduledRecordingDao().observeScheduled().first()
                        .filter { it.endMillis > now }
                        .forEach { entity ->
                            RecordingScheduler.schedule(context, entity.toModel())
                        }
                }
                // §4.58 — re-arm saved channel timers (enabled + in the
                // future only). The model id "db:<rowId>" reproduces the
                // original PendingIntent request code, so the re-armed
                // alarm is the same alarm.
                runCatching {
                    db.channelTimerDao().getAll()
                        .filter { it.enabled && it.triggerAtMillis > now }
                        .forEach { entity ->
                            ChannelTimerScheduler.schedule(context, toModel(entity))
                        }
                }
                if (settings.autoBackupWeekly) {
                    runCatching { BackupWorker.enqueue(context) }
                }
            } finally {
                pending.finish()
            }
        }
        if (!settings.autoStartOnBoot) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }
}
