package com.streammaster.tv.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.matchcenter.GoalAlert
import com.streammaster.tv.data.matchcenter.GoalAlertBus
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.player.remote.RemoteBus
import com.streammaster.tv.player.remote.RemoteCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteBus: RemoteBus,
    goalAlertBus: GoalAlertBus,
    contentRepository: ContentRepository
) : ViewModel() {
    /** Phone remote navigation commands (2026-11-22), handled by Shell. */
    val remoteCommands: SharedFlow<RemoteCommand> = remoteBus.commands

    /** Goal alerts (2026-11-22): the Shell shows the popup over every screen. */
    val goalAlerts: SharedFlow<GoalAlert> = goalAlertBus.alerts
    val activePlaylist: StateFlow<Playlist?> = playlistRepository.observePlaylists()
        .map { list -> list.firstOrNull { it.isActive } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Sidebar count badges (prototype): content counts + favourites + playlists. */
    val channelCount: StateFlow<Int> = contentRepository.observeChannels()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val filmCount: StateFlow<Int> = contentRepository.observeFilms()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val seriesCount: StateFlow<Int> = contentRepository.observeSeries()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val favoriteCount: StateFlow<Int> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries()
    ) { channels, films, series ->
        // M-05: hidden favourites excluded, like FavorisViewModel.
        channels.count { it.isFavorite && !it.hidden } + films.count { it.isFavorite && !it.hidden } + series.count { it.isFavorite && !it.hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val playlistCount: StateFlow<Int> = playlistRepository.observePlaylists()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Kids mode hides the admin sidebar items (2026-11-22). */
    val kidsMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.kidsMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * TopBar latency pill (S14): round-trip time of an OkHttp HEAD to the
     * active playlist's server, re-measured every 30 s (same tick as the
     * clock). Null = no playlist, or no measurement yet / last attempt
     * failed — the UI shows "— ms" then.
     */
    private val latencyClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
    private val _latencyMs = MutableStateFlow<Int?>(null)
    val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                measureLatency()
                delay(30_000)
            }
        }
    }

    private suspend fun measureLatency() {
        val playlist = activePlaylist.value
        val baseUrl = when (playlist?.type) {
            PlaylistType.XTREAM -> playlist.serverUrl
            PlaylistType.STALKER -> playlist.portalUrl
            PlaylistType.M3U -> playlist.m3uUrl
            null -> null
        }.orEmpty()
        if (baseUrl.isBlank()) {
            _latencyMs.value = null
            return
        }
        try {
            val ms = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(baseUrl).head().build()
                val start = SystemClock.elapsedRealtime()
                latencyClient.newCall(request).execute().use { /* HEAD: headers only */ }
                SystemClock.elapsedRealtime() - start
            }
            _latencyMs.value = ms.toInt()
        } catch (_: Exception) {
            _latencyMs.value = null
        }
    }

    /** True when the last kids-mode exit PIN was wrong. */
    private val _kidsPinError = MutableStateFlow(false)
    val kidsPinError: StateFlow<Boolean> = _kidsPinError.asStateFlow()

    /** Exits kids mode when the parental PIN matches (or no PIN is set). */
    fun exitKidsMode(pin: String) = viewModelScope.launch {
        val settings = settingsRepository.settings.first()
        if (settings.pin.isEmpty() || settings.pin == pin) {
            _kidsPinError.value = false
            settingsRepository.update { it.copy(kidsMode = false) }
        } else {
            _kidsPinError.value = true
        }
    }
}
