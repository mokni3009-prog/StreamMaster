package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Splash: if a playlist already exists the user goes straight to Loading/Home, else Login.
 *  App lock (2026-11-22): when enabled with a PIN set, the PIN dialog must
 *  be passed before the app opens. */
@HiltViewModel
class SplashViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val hasPlaylist: StateFlow<Boolean> = playlistRepository.observePlaylists()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockRequired: StateFlow<Boolean> = settingsRepository.settings
        .map { it.appLock && it.pin.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    fun checkPin(pin: String) = viewModelScope.launch {
        val settings = settingsRepository.settings.first()
        if (settings.pin.isNotEmpty() && settings.pin == pin) {
            _pinError.value = false
            _unlocked.value = true
        } else {
            _pinError.value = true
        }
    }
}
