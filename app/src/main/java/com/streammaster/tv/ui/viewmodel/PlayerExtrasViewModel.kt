package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Player extras (§4.34 / §4.54 / §4.56) — small settings-backed values
 * owned by the player UI, kept out of PlayerViewModel (owned by the
 * player agent). All fields below already exist in AppSettings with
 * DataStore persistence (landed by the settings builder); the settings
 * rows themselves are also the settings builder's (not this file).
 */
@HiltViewModel
class PlayerExtrasViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Do-not-disturb (§4.54): while on, no popup/notification appears
     *  over playback except the goal alert (§4.33, exempt). */
    val dndEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.dndEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Maximum speed (§4.34): the live-speed badge is shown only while
     *  this is on — the badge is not shown just because a label exists. */
    val maxSpeed: StateFlow<Boolean> = settingsRepository.settings
        .map { it.maxSpeed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Subtitle styling (§4.56): size as a percentage (75/100/125/150),
     *  colour as a hex string, position ("bottom" | "top"). */
    val subtitleSize: StateFlow<Int> = settingsRepository.settings
        .map { it.subtitleSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)
    val subtitleColor: StateFlow<String> = settingsRepository.settings
        .map { it.subtitleColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#FFFFFF")
    val subtitlePosition: StateFlow<String> = settingsRepository.settings
        .map { it.subtitlePosition }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "bottom")

    fun toggleDnd() = viewModelScope.launch {
        settingsRepository.update { it.copy(dndEnabled = !it.dndEnabled) }
    }
}
