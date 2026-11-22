package com.streammaster.tv.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.streammaster.tv.data.matchcenter.GoalAlertService
import com.streammaster.tv.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Auto-start on boot (2026-11-22): launches the app when the device boots,
 *  only when the user enabled it in Settings. Note: some Android TV OEMs
 *  restrict background activity starts — on those devices the user must
 *  allow autostart for the app in the system settings. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking {
            SettingsStore(context).settings.first()
        }
        // Goal alerts (2026-11-22): restart the alert service after a
        // reboot when the user left it enabled — independent of the
        // auto-start-on-boot app launch below.
        if (settings.goalAlerts) {
            runCatching { GoalAlertService.start(context) }
        }
        if (!settings.autoStartOnBoot) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }
}
