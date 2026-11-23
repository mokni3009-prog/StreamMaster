package com.streammaster.tv.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.BuildConfig
import com.streammaster.tv.data.db.QoeDao
import com.streammaster.tv.data.db.QoeEvent
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.update.ServerStatus
import com.streammaster.tv.data.update.ServerStatusChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val serverStatus: ServerStatus? = null,
    val serverChecking: Boolean = false,
    val serverName: String = "",
    val stallCountSession: Int = 0,
    val errorCountSession: Int = 0,
    val recentErrors: List<QoeEvent> = emptyList(),
    val network: String = "",
    val appVersion: String = "",
    val loading: Boolean = true
)

/**
 * Local-only error log (§4.38). Kept in memory, on the device, never sent
 * anywhere — telemetry is suspended by the user's order, so the diagnostics
 * screen reads only this log and the local QoE table.
 */
object LocalErrorLog {
    data class Entry(val message: String, val timestampMillis: Long = System.currentTimeMillis())

    private const val MAX_ENTRIES = 50
    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(message: String) {
        entries.addLast(Entry(message))
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()
}

/**
 * Diagnostics (§4.38) — local only, no Firebase, no telemetry.
 * Server status reuses [ServerStatusChecker]; stall/error counts come from
 * the local `qoe_events` table ([QoeDao]); network from ConnectivityManager;
 * version from BuildConfig. NOTE: [QoeDao] only has rows once a Room-backed
 * QoeStore is bound (today LoggingQoeStore writes to logcat only), so the
 * screen shows "no errors recorded" until that binding lands.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val qoeDao: QoeDao,
    private val playlistRepository: PlaylistRepository,
    private val serverStatusChecker: ServerStatusChecker
) : ViewModel() {

    private val sessionStartMillis = System.currentTimeMillis()

    private val _state = MutableStateFlow(
        DiagnosticsUiState(
            network = readNetwork(),
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
    )
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val playlist = runCatching { playlistRepository.getActive() }.getOrNull()
        val serverStatus = playlist?.let { runCatching { serverStatusChecker.check(it) }.getOrNull() }
        // QoE eventType strings are stored lowercase ("stall", "error") by
        // the Room store; count both casings defensively.
        val stalls = runCatching {
            qoeDao.countByTypeSince("stall", sessionStartMillis) +
                qoeDao.countByTypeSince("STALL", sessionStartMillis)
        }.getOrDefault(0)
        val errors = runCatching {
            qoeDao.countByTypeSince("error", sessionStartMillis) +
                qoeDao.countByTypeSince("ERROR", sessionStartMillis)
        }.getOrDefault(0)
        val recent = runCatching { qoeDao.getRecent() }.getOrDefault(emptyList())
            .filter { it.eventType.equals("error", ignoreCase = true) }
            .take(10)
        _state.update {
            it.copy(
                serverStatus = serverStatus,
                serverName = playlist?.name ?: "",
                stallCountSession = stalls,
                errorCountSession = errors,
                recentErrors = recent,
                network = readNetwork(),
                loading = false
            )
        }
    }

    fun checkServer() = viewModelScope.launch {
        _state.update { it.copy(serverChecking = true) }
        val playlist = runCatching { playlistRepository.getActive() }.getOrNull()
        val status = playlist?.let { runCatching { serverStatusChecker.check(it) }.getOrNull() }
        _state.update { it.copy(serverStatus = status, serverName = playlist?.name ?: "", serverChecking = false) }
    }

    private fun readNetwork(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "unknown"
        }
    }
}
