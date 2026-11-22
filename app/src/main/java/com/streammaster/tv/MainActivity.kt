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
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.streammaster.tv.data.matchcenter.AppForeground
import com.streammaster.tv.data.matchcenter.GoalAlertManager
import com.streammaster.tv.player.PlayerManager
import com.streammaster.tv.player.remote.RemoteKeyDispatcher
import com.streammaster.tv.player.remote.RemoteServerManager
import com.streammaster.tv.ui.navigation.AppNavHost
import com.streammaster.tv.ui.navigation.Routes
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
        handleDeepLink(intent)
        setContent {
            val navController = rememberNavController()
            StreamMasterTheme {
                AppNavHost(navController)
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
        if (current == Routes.PLAYER) return
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
