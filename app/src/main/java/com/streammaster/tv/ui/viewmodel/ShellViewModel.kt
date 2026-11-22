package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.matchcenter.GoalAlert
import com.streammaster.tv.data.matchcenter.GoalAlertBus
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.player.remote.RemoteBus
import com.streammaster.tv.player.remote.RemoteCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val remoteBus: RemoteBus,
    goalAlertBus: GoalAlertBus
) : ViewModel() {
    /** Phone remote navigation commands (2026-11-22), handled by Shell. */
    val remoteCommands: SharedFlow<RemoteCommand> = remoteBus.commands

    /** Goal alerts (2026-11-22): the Shell shows the popup over every screen. */
    val goalAlerts: SharedFlow<GoalAlert> = goalAlertBus.alerts
    val activePlaylist: StateFlow<Playlist?> = playlistRepository.observePlaylists()
        .map { list -> list.firstOrNull { it.isActive } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Kids mode hides the admin sidebar items (2026-11-22). */
    val kidsMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.kidsMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
