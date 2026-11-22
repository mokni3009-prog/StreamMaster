package com.streammaster.tv.player.remote

import android.content.Context
import android.util.Log
import com.streammaster.tv.data.model.PlaybackItem
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.player.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Phone web remote (2026-11-22): owns the embedded HTTP server's lifecycle.
 * [bind] (called once from MainActivity) follows the persisted
 * `remoteEnabled` setting: when enabled the server starts on the first free
 * port in 8080..8090 and keeps running while the app process is alive; when
 * disabled it stops. The pairing code comes from settings (`remoteCode`,
 * 6 digits); the server refuses every /api/... call without it.
 *
 * Limits (v1): local Wi-Fi only (the phone and the TV must share the same
 * network), and the server stops when the app process dies — there is no
 * background service keeping it alive.
 */
@Singleton
class RemoteServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val playerManager: PlayerManager,
    private val handler: RemoteCommandHandler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: RemoteHttpServer? = null
    private var bound = false
    private var currentCode: String = ""

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _port = MutableStateFlow(0)
    val port: StateFlow<Int> = _port.asStateFlow()
    private val _localIp = MutableStateFlow("")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    /** Starts following the settings; safe to call more than once. */
    fun bind() {
        if (bound) return
        bound = true
        scope.launch {
            settingsRepository.settings.map { it.remoteCode }.distinctUntilChanged().collect {
                currentCode = it
            }
        }
        scope.launch {
            settingsRepository.settings.map { it.remoteEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) startServer() else stopServer()
            }
        }
    }

    private fun startServer() {
        if (server != null) return
        val ip = localIpAddress()
        _localIp.value = ip
        if (ip.isEmpty()) {
            Log.w(TAG, "Remote server not started: no LAN address (Wi-Fi off?)")
            return
        }
        if (currentCode.isBlank()) {
            Log.w(TAG, "Remote server not started: no pairing code yet")
            return
        }
        for (port in 8080..8090) {
            try {
                val http = RemoteHttpServer(
                    port = port,
                    context = context,
                    handler = handler,
                    codeProvider = { currentCode },
                    statusProvider = { buildStatusJson() }
                )
                http.start()
                server = http
                _port.value = port
                _running.value = true
                Log.i(TAG, "Remote server started on $ip:$port")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Remote server: port $port busy (${e.message})")
            }
        }
        Log.w(TAG, "Remote server not started: no free port in 8080..8090")
    }

    private fun stopServer() {
        try {
            server?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Remote server stop failed", e)
        }
        server = null
        _port.value = 0
        _running.value = false
    }

    /** Current playback, for the phone page's status line (polled). */
    private fun buildStatusJson(): String {
        val state = playerManager.state.value
        val (kind, title) = when (val item = state.item) {
            is PlaybackItem.Live -> "live" to (item.channel.customName ?: item.channel.name)
            is PlaybackItem.Movie -> "film" to item.film.title
            is PlaybackItem.EpisodeItem -> "series" to "${item.series.title} — ${item.episode.title}"
            is PlaybackItem.RecordingItem -> "recording" to item.recording.title
            is PlaybackItem.DownloadItem -> "download" to item.download.title
            null -> "" to ""
        }
        return """{"playing":${state.isPlaying},"kind":"$kind","title":"${jsonEscape(title)}"}"""
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** First non-loopback IPv4 address (wlan/eth), "" when offline. */
    private fun localIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = interfaces.flatMap { iface ->
                iface.inetAddresses.toList().map { addr -> iface.name to addr }
            }.filter { (_, addr) -> !addr.isLoopbackAddress && addr is Inet4Address }
            val preferred = candidates.firstOrNull { (name, _) ->
                name.startsWith("wlan") || name.startsWith("eth")
            } ?: candidates.firstOrNull()
            (preferred?.second as? Inet4Address)?.hostAddress ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Could not read LAN address", e)
            ""
        }
    }

    companion object {
        private const val TAG = "RemoteServerManager"
    }
}
