package com.streammaster.tv.data.matchcenter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streammaster.tv.R
import com.streammaster.tv.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.goalAlertsDataStore: DataStore<Preferences> by preferencesDataStore(name = "goal_alerts")
private val KEY_SEEN_GOALS = stringSetPreferencesKey("seenGoals")
/** LG-17 — ordered replacement for [KEY_SEEN_GOALS]: a "\n"-joined list,
 *  newest last, so pruning the oldest 300 never drops a recent goal
 *  (a Set has no order, so takeLast(300) on it was arbitrary). */
private val KEY_SEEN_GOALS_LIST = stringPreferencesKey("seenGoalsList")
/** §4.36.8 — pre-match alerts already sent ("pre:<espn event id>"). */
private val KEY_SEEN_PRE_MATCH_ALERTS = stringSetPreferencesKey("seenPreMatchAlerts")
/** LG-17 — ordered replacement for [KEY_SEEN_PRE_MATCH_ALERTS]. */
private val KEY_SEEN_PRE_MATCH_ALERTS_LIST = stringPreferencesKey("seenPreMatchAlertsList")

private const val SERVICE_CHANNEL_ID = "goal_alerts_service"
private const val ALERT_CHANNEL_ID = "goal_alerts"
private const val SERVICE_NOTIFICATION_ID = 1004
private const val POLL_INTERVAL_MS = 30_000L
private const val MAX_SEEN_GOALS = 300
private const val TAG = "GoalAlerts"
/** §4.36.8 — warn this long before kickoff. */
private const val PRE_MATCH_LEAD_MS = 15 * 60_000L

/**
 * Goal alerts (2026-11-22): foreground service that polls the ESPN
 * scoreboard every 30 seconds for ALL of today's matches and announces
 * every new goal — in-app popup when the app is open, system notification
 * when it is in the background. Opt-in only: the manager starts it when
 * the user enables goal alerts and stops it when they turn them off.
 *
 * First poll after start only seeds the seen set (no alerts for goals
 * scored before the user enabled the feature). Seen goal keys persist in
 * DataStore, so a service restart never re-announces an old goal.
 */
@AndroidEntryPoint
class GoalAlertService : Service() {

    @Inject
    lateinit var api: MatchCenterApi

    @Inject
    lateinit var bus: GoalAlertBus

    /** §4.36.8 — reads AppSettings.smartMatchAlerts (added by the
     *  settings builder, default true). */
    @Inject
    lateinit var settingsStore: SettingsStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GoalAlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GoalAlertService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        SERVICE_CHANNEL_ID,
                        getString(R.string.goal_alert_service_channel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.goal_alert_service_channel_desc)
                    }
                )
            }
            if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ALERT_CHANNEL_ID,
                        getString(R.string.goal_alert_channel),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = getString(R.string.goal_alert_channel_desc)
                    }
                )
            }
        }
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.goal_alert_service_running))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollJob?.isActive != true) {
            pollJob = serviceScope.launch {
                var seeded = false
                while (true) {
                    // OP-05: a DataStore IO failure used to kill this loop
                    // silently (and the exception escaped the coroutine) —
                    // log it and keep polling instead.
                    runCatching { pollOnce(seedOnly = !seeded) }
                        .onFailure { Log.w(TAG, "goal poll failed, will retry", it) }
                    seeded = true
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollOnce(seedOnly: Boolean) {
        val matches = runCatching { api.fetchToday() }.getOrDefault(emptyList())
        val seen = loadSeen().toMutableList()
        val fresh = mutableListOf<GoalAlert>()
        for (match in matches) {
            for (goal in match.goals) {
                if (goal.key in seen) continue
                seen += goal.key
                if (!seedOnly) {
                    fresh += GoalAlert(
                        key = goal.key,
                        leagueName = match.leagueName,
                        homeTeam = match.homeTeam,
                        awayTeam = match.awayTeam,
                        homeScore = match.homeScore ?: "?",
                        awayScore = match.awayScore ?: "?",
                        scorer = goal.scorer,
                        scorerTeam = goal.scorerTeam,
                        minute = goal.minute
                    )
                }
            }
        }
        // Prune the seen list so it never grows without bound. LG-17: the
        // list is insertion-ordered (oldest first), so takeLast drops the
        // oldest goals — never a recent one.
        saveSeen(seen.takeLast(MAX_SEEN_GOALS))
        for (alert in fresh) {
            bus.emit(alert)
            if (!AppForeground.isForeground) postSystemNotification(alert)
        }
        // §4.36.8 — pre-match alerts for ALL of today's matches (not
        // just favourites): 15 minutes before kickoff, once per match.
        runCatching { checkPreMatchAlerts(matches) }
    }

    /**
     * §4.36.8 — for every not-yet-started match whose kickoff is within
     * the next 15 minutes, post one notification (never twice: the key
     * persists in the same `goal_alerts` DataStore as the seen goals).
     * Gated on AppSettings.smartMatchAlerts (default true).
     * NOTE: this service only runs while goal alerts are enabled
     * (GoalAlertManager starts it on `goalAlerts`); for smart match
     * alerts to work on their own, the manager must also start the
     * service when `smartMatchAlerts` is on.
     */
    private suspend fun checkPreMatchAlerts(matches: List<LiveMatch>) {
        val enabled = runCatching { settingsStore.settings.first().smartMatchAlerts }
            .getOrDefault(true)
        if (!enabled) return
        val now = System.currentTimeMillis()
        val seen = loadSeenPreMatch().toMutableList()
        var changed = false
        for (match in matches) {
            if (match.state != "pre" || match.startMillis <= 0L) continue
            val lead = match.startMillis - now
            if (lead <= 0L || lead > PRE_MATCH_LEAD_MS) continue
            val key = "pre:${match.id}"
            if (key in seen) continue
            seen += key
            changed = true
            postPreMatchNotification(match)
        }
        if (changed) saveSeenPreMatch(seen.takeLast(MAX_SEEN_GOALS))
    }

    /** LG-17 — ordered (oldest first); migrates once from the old
     *  unordered stringSet key. Goal keys never contain "\n". */
    private suspend fun loadSeenPreMatch(): List<String> {
        val prefs = applicationContext.goalAlertsDataStore.data.first()
        val list = prefs[KEY_SEEN_PRE_MATCH_ALERTS_LIST]
        if (!list.isNullOrBlank()) return list.split("\n").filter { it.isNotBlank() }
        return (prefs[KEY_SEEN_PRE_MATCH_ALERTS] ?: emptySet()).toList()
    }

    private suspend fun saveSeenPreMatch(keys: List<String>) {
        applicationContext.goalAlertsDataStore.edit { prefs ->
            prefs[KEY_SEEN_PRE_MATCH_ALERTS_LIST] = keys.joinToString("\n")
            prefs.remove(KEY_SEEN_PRE_MATCH_ALERTS)
        }
    }

    /** Pre-match notification — posted whether the app is in the
     *  foreground or not (unlike goal alerts, there is no in-app popup
     *  for a match that has not started yet). */
    private fun postPreMatchNotification(match: LiveMatch) {
        runCatching {
            val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                this,
                ("pre:${match.id}").hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = "${match.homeTeam} vs ${match.awayTeam} • ${match.leagueName}"
            val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle(getString(R.string.match_starts_soon_title))
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(("pre:${match.id}").hashCode(), notification)
        }
    }

    /** LG-17 — ordered (oldest first); migrates once from the old
     *  unordered stringSet key. Goal keys never contain "\n". */
    private suspend fun loadSeen(): List<String> {
        val prefs = applicationContext.goalAlertsDataStore.data.first()
        val list = prefs[KEY_SEEN_GOALS_LIST]
        if (!list.isNullOrBlank()) return list.split("\n").filter { it.isNotBlank() }
        return (prefs[KEY_SEEN_GOALS] ?: emptySet()).toList()
    }

    private suspend fun saveSeen(keys: List<String>) {
        applicationContext.goalAlertsDataStore.edit { prefs ->
            prefs[KEY_SEEN_GOALS_LIST] = keys.joinToString("\n")
            prefs.remove(KEY_SEEN_GOALS)
        }
    }

    /** System notification, only when the app is in the background. */
    private fun postSystemNotification(alert: GoalAlert) {
        runCatching {
            val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                this,
                alert.key.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = if (alert.scorer.isNotBlank()) {
                "${getString(R.string.goal_alert_goal)} ${alert.scorer}"
            } else {
                getString(R.string.goal_alert_goal)
            }
            val text = "${alert.homeTeam} ${alert.homeScore} - ${alert.awayScore} " +
                "${alert.awayTeam} • ${alert.minute}"
            val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(alert.key.hashCode(), notification)
        }
    }
}
