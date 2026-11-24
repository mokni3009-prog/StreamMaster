package com.streammaster.tv

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.streammaster.tv.R
import com.streammaster.tv.data.matchcenter.AppForeground
import com.streammaster.tv.data.matchcenter.GoalAlertManager
import com.streammaster.tv.data.update.UpdateCheckWorker
import com.streammaster.tv.data.update.UpdateDownloader
import com.streammaster.tv.data.update.UpdatePromptState
import com.streammaster.tv.player.PlayerManager
import com.streammaster.tv.player.remote.RemoteKeyDispatcher
import com.streammaster.tv.player.remote.RemoteServerManager
import com.streammaster.tv.ui.components.ConfirmDialog
import com.streammaster.tv.ui.navigation.AppNavHost
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.ScaledContent
import com.streammaster.tv.ui.theme.StreamMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * CONTRACT §2 — single activity, @AndroidEntryPoint, Compose for TV entry.
 * - setContent: StreamMasterTheme + AppNavHost.
 * - PiP: onUserLeaveHint enters system PiP while playing (API 26+);
 *   onPictureInPictureModeChanged clears the player's PiP state when the
 *   user expands out of system PiP.
 * - Deep link: reminder notifications open streammaster://player/live/{id};
 *   the navigation waits until the app has settled past Splash/Login/
 *   Loading, then opens the channel.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var remoteServerManager: RemoteServerManager

    @Inject
    lateinit var goalAlertManager: GoalAlertManager

    private val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phone web remote (2026-11-22): the activity receives the remote's
        // navigation keys as real key events; the manager starts the local
        // HTTP server when the setting is enabled.
        RemoteKeyDispatcher.activity = this
        remoteServerManager.bind()
        // Goal alerts (2026-11-22): the manager starts/stops the alert
        // service from the goalAlerts setting.
        goalAlertManager.bind()
        // Automatic update check (§4.25b, 2026-11-23): every 24h, reads the
        // public update.json; a newer version shows the popup on next launch.
        UpdateCheckWorker.enqueue(this)
        handleDeepLink(intent)
        setContent {
            val navController = rememberNavController()
            // Pending update (§4.25b): loaded from DataStore on every launch.
            val pendingUpdate by UpdatePromptState.pending.collectAsState()
            LaunchedEffect(Unit) { UpdatePromptState.loadPending(applicationContext) }
            StreamMasterTheme {
                // UI scaling (2026-11-23): the whole nav graph is measured
                // against the 1920×1080dp design canvas (see UiScale.kt).
                ScaledContent {
                    AppNavHost(navController)
                    // Update popup (§4.25b): a Dialog above every screen,
                    // even while watching. "Update now" starts the
                    // DownloadManager download (system notification, then
                    // install over the old version); "Remind me later"
                    // hides it until the next app launch. Never forced.
                    val update = pendingUpdate
                    if (update != null) {
                        ConfirmDialog(
                            title = stringResource(R.string.update_available_title),
                            message = stringResource(
                                R.string.update_available_message,
                                update.version, update.changelog
                            ),
                            confirmText = stringResource(R.string.update_now),
                            dismissText = stringResource(R.string.update_later),
                            onConfirm = {
                                UpdateDownloader.download(this@MainActivity, update.downloadUrl)
                                UpdatePromptState.dismissForSession()
                            },
                            onDismiss = { UpdatePromptState.dismissForSession() }
                        )
                    }
                }
            }
            // Navigate to a pending reminder deep link once the destination
            // settles on a main screen (not Splash/Login/Loading).
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect {
                    maybeNavigateToDeepLink(navController)
                }
            }
            LaunchedEffect(pendingDeepLink.value) {
                maybeNavigateToDeepLink(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** Parses streammaster://player/live/{channelId} into [pendingDeepLink]. */
    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (intent?.action != Intent.ACTION_VIEW || data?.scheme != "streammaster") return
        val segments = data.pathSegments
        if (segments.size >= 2 && segments[0] == "live") {
            pendingDeepLink.value = segments[1]
        }
    }

    private fun maybeNavigateToDeepLink(navController: NavHostController) {
        val channelId = pendingDeepLink.value ?: return
        val current = navController.currentDestination?.route ?: return
        if (current in listOf(Routes.SPLASH, Routes.LOGIN, Routes.LOADING)) return
        if (current == Routes.PLAYER) {
            // Channel-timer deep link while already in the player: switch
            // immediately when it names a DIFFERENT channel (the old code
            // returned here, so the timer's link stayed queued forever);
            // the same channel is a no-op.
            val currentId = navController.currentBackStackEntry
                ?.arguments?.getString("id")
            pendingDeepLink.value = null
            if (currentId == channelId) return
            navController.navigate(Routes.player("live", channelId)) {
                popUpTo(Routes.PLAYER) { inclusive = true }
                launchSingleTop = true
            }
            return
        }
        pendingDeepLink.value = null
        navController.navigate(Routes.player("live", channelId))
    }

    override fun onResume() {
        super.onResume()
        // Goal alerts (2026-11-22): the service shows the in-app popup
        // while the app is in the foreground, a system notification otherwise.
        AppForeground.isForeground = true
    }

    override fun onPause() {
        AppForeground.isForeground = false
        super.onPause()
    }

    override fun onDestroy() {
        RemoteKeyDispatcher.activity = null
        // R-08: closing the PiP window (X) destroys the activity while the
        // singleton PlayerManager + PlaybackService keep playing with no UI
        // left to stop them — stop playback when this is a real finish
        // (not a config-change recreation) and PiP was active.
        if (isFinishing && playerManager.state.value.isPip) {
            runCatching { playerManager.stop() }
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!playerManager.state.value.isPlaying) return
        if (!playerManager.state.value.isPip) playerManager.enterPip()
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && playerManager.state.value.isPip) {
            playerManager.exitPip()
        }
    }
}
