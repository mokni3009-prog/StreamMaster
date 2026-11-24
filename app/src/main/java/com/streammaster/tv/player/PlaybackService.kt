package com.streammaster.tv.player

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.streammaster.tv.MainActivity
import com.streammaster.tv.data.model.PlaybackItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StreamMaster"
const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "playback"

/**
 * Asks the system to exempt the app from battery optimization so long playback
 * sessions are not killed by Doze (SPEC §10). Called from Settings (agent 2).
 * Note: Play Store policy restricts this permission; it is most appropriate
 * for direct-APK distribution.
 */
fun requestBatteryExemption(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (powerManager.isIgnoringBatteryOptimizations(activity.packageName)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${activity.packageName}")
    }
    try {
        activity.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "Could not open battery exemption settings", e)
    }
}

/**
 * Foreground media service (GUIDE B18/B37, SPEC §10 "long playback").
 *
 * - [MediaSessionService] subclass: the session wraps the ExoPlayer instance
 *   (Media3 Player) so playback stays controllable from the TV UI / background.
 *   When VLC is the active engine there is no Media3 Player to wrap, so the
 *   session is absent — playback itself is unaffected.
 * - START_STICKY: the system restarts the service if it is killed.
 * - WakeLock + WifiLock are held while the service runs (i.e. while something
 *   is playing — [PlayerManagerImpl] starts the service on play() and stops
 *   it on stop()/release()) so Doze cannot starve the buffer (B18).
 * - NetworkCallback (D8): on network loss the stall timer in PlayerManager
 *   keeps the failure from being misread; on regain the live stream is
 *   re-prepared at the live edge instead of resuming a dead session.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkLost = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            networkLost = true
            Log.w(TAG, "Network lost during playback")
        }

        override fun onAvailable(network: Network) {
            if (!networkLost) return
            networkLost = false
            Log.i(TAG, "Network regained — re-preparing at the live edge")
            scope.launch {
                val state = playerManager.state.value
                if (state.isPlaying && state.item is PlaybackItem.Live) {
                    playerManager.seekToLiveEdge()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // R-07: START_STICKY restarts can resurrect the service while the
        // player is idle (no item, not playing) — acquiring WakeLock +
        // WifiLock then would hold them forever with nothing to stop the
        // service. Stop instead; a real play() starts it again.
        // (play() sets state.item synchronously right after start(), before
        // this onCreate can run on the main thread, so a live start is
        // never mistaken for idle here.)
        val state = playerManager.state.value
        if (state.item == null && !state.isPlaying) {
            Log.i(TAG, "PlaybackService restarted while idle — stopping without acquiring locks")
            stopSelf()
            return
        }
        createNotificationChannel()
        val player = playerManager.exoPlayer
        if (player != null) {
            val sessionActivity = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()
        } else {
            Log.i(TAG, "No ExoPlayer (API < 23 / VLC active) — no MediaSession")
        }
        acquireLocks()
        registerNetworkCallback()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            playerManager.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Stop on task removed failed", e)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            unregisterNetworkCallback()
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback unregister failed", e)
        }
        releaseLocks()
        try {
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession release failed", e)
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(PLAYBACK_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            PLAYBACK_NOTIFICATION_CHANNEL_ID,
            "Lecture en cours",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification de lecture Stream Master"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "StreamMaster:playback"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire failed", e)
        }
        try {
            @Suppress("DEPRECATION")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "StreamMaster:playback"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock acquire failed", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release failed", e)
        }
        wakeLock = null
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock release failed", e)
        }
        wifiLock = null
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback register failed", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    companion object {
        /** Started by [PlayerManagerImpl] on play(); stopped on stop()/release(). */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start PlaybackService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop PlaybackService", e)
            }
        }
    }
}
