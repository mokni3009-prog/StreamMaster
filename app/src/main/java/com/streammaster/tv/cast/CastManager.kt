package com.streammaster.tv.cast

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "StreamMasterCast"

/**
 * SPEC §4.46 — Chromecast sender: device discovery (MediaRouter),
 * Cast session, load the currently-playing URL on the receiver, and
 * play / pause / stop control from the app.
 *
 * The Player builder owns the Cast button in PlayerScreen; this class is
 * the engine behind it, and [CastButton] (cast/CastButton.kt) is the
 * ready-made composable it drops in.
 *
 * ### Verification limits (honest — no Android SDK / device in this task)
 * - Compiled against the Cast SDK 21.4.0 API surface from Google's docs;
 *   NOT verified by a Gradle build or on a real Chromecast here.
 * - [CastContext] only initialises when the manifest registers
 *   [StreamMasterCastOptionsProvider] (see that file). Until the manifest
 *   entry exists, [isCastAvailable] is false and discovery still lists
 *   routes via MediaRouter, but no Cast session can start — the manifest
 *   step is a one-line integration for the app builder.
 * - Requires Google Play services on the device (Android TV boxes without
 *   Play services cannot Cast; [isCastAvailable] reports that).
 */
class CastManager(context: Context) {

    private val appContext = context.applicationContext

    /** One discovered Cast-capable route (a Chromecast / Cast-enabled TV). */
    data class CastDeviceInfo(val id: String, val name: String, val description: String = "")

    data class CastState(
        val available: Boolean = false,
        val devices: List<CastDeviceInfo> = emptyList(),
        val connectedDeviceName: String? = null,
        val isPlaying: Boolean = false,
        val isCasting: Boolean = false,
        val lastError: String? = null
    )

    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    /** False until CastContext initialises (manifest entry + Play services). */
    val isCastAvailable: Boolean get() = _state.value.available

    private var castContext: CastContext? = null
    private var mediaRouter: MediaRouter? = null
    private var currentSession: CastSession? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onSession(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onSession(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onSessionEnded()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = onSessionEnded()
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _state.update { it.copy(lastError = "Cast session failed ($error)") }
        }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit

        private fun onSession(session: CastSession) {
            currentSession = session
            _state.update {
                it.copy(
                    connectedDeviceName = session.castDevice?.friendlyName,
                    isCasting = true,
                    lastError = null
                )
            }
        }

        private fun onSessionEnded() {
            currentSession = null
            _state.update { it.copy(connectedDeviceName = null, isCasting = false, isPlaying = false) }
        }
    }

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes(router)
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes(router)
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes(router)
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutes(router)
            // Selecting a Cast route makes CastContext open the session;
            // the SessionManagerListener above reports the connection.
            _state.update { it.copy(connectedDeviceName = route.name?.toString(), lastError = null) }
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            _state.update { it.copy(connectedDeviceName = null, isCasting = false, isPlaying = false) }
        }

        private fun refreshRoutes(router: MediaRouter) {
            val devices = router.routes
                .filter { it.isEnabled }
                .map {
                    CastDeviceInfo(
                        id = it.id,
                        name = it.name?.toString()?.takeIf { n -> n.isNotBlank() } ?: "Cast",
                        description = it.description?.toString() ?: ""
                    )
                }
            _state.update { it.copy(devices = devices) }
        }
    }

    init {
        runCatching {
            castContext = CastContext.getSharedInstance(appContext)
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            mediaRouter = MediaRouter.getInstance(appContext)
            mediaRouter?.addCallback(
                com.google.android.gms.cast.CastMediaControlIntent.categoryForCast(
                    StreamMasterCastOptionsProvider.RECEIVER_APP_ID
                ).let { category ->
                    androidx.mediarouter.media.MediaRouteSelector.Builder()
                        .addControlCategory(category)
                        .build()
                },
                routeCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            _state.update { it.copy(available = true) }
        }.onFailure { e ->
            // Expected on devices without Play services, or before the
            // manifest registers StreamMasterCastOptionsProvider.
            Log.w(TAG, "Cast unavailable: ${e.message}")
            _state.update { it.copy(available = false, lastError = e.message) }
        }
    }

    /** Ask MediaRouter to connect to [device] (opens the Cast chooser session). */
    fun selectDevice(device: CastDeviceInfo) {
        val router = mediaRouter ?: return
        val route = router.routes.firstOrNull { it.id == device.id } ?: return
        router.selectRoute(route)
    }

    /** Disconnect from the current Cast session (playback returns to the TV). */
    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        currentSession = null
        _state.update { it.copy(connectedDeviceName = null, isCasting = false, isPlaying = false) }
    }

    /**
     * Load [url] on the receiver and start playing it.
     * [title] is shown on the receiver / TV. [isLive] picks the live
     * stream type (HLS/DASH live) instead of buffered VOD.
     * Returns false when no Cast session is active yet.
     */
    fun castUrl(url: String, title: String, isLive: Boolean = false): Boolean {
        val client: RemoteMediaClient = currentSession?.remoteMediaClient ?: return false
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(if (url.contains(".m3u8")) "application/x-mpegURL" else "video/mp4")
            .setMetadata(metadata)
            .build()
        return runCatching {
            client.load(
                MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .build()
            )
            _state.update { it.copy(isPlaying = true, lastError = null) }
            true
        }.onFailure { e ->
            Log.w(TAG, "Cast load failed", e)
            _state.update { it.copy(lastError = e.message) }
        }.getOrDefault(false)
    }

    fun play() {
        currentSession?.remoteMediaClient?.play()
        _state.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        currentSession?.remoteMediaClient?.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        currentSession?.remoteMediaClient?.stop()
        _state.update { it.copy(isPlaying = false, isCasting = false) }
    }

    /** 0.0 – 1.0 receiver volume. */
    fun setVolume(level: Float) {
        currentSession?.remoteMediaClient?.setVolume(level.toDouble())
    }

    /** Stop discovery and unregister listeners (call from onDispose / onCleared). */
    fun release() {
        runCatching {
            mediaRouter?.removeCallback(routeCallback)
            castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        }
    }
}
