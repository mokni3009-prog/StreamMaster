package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.repository.ContentRepository
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
    private val settingsRepository: SettingsRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    val hasPlaylist: StateFlow<Boolean> = playlistRepository.observePlaylists()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockRequired: StateFlow<Boolean> = settingsRepository.settings
        .map { it.appLock && it.pin.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** L-06 — true once the real settings have arrived (the StateFlow
     *  above starts at the cold default `false`, so the screen cannot
     *  tell "no lock" from "not loaded yet" from lockRequired alone). */
    val settingsLoaded: StateFlow<Boolean> = settingsRepository.settings
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    /**
     * Where the app should go after the splash (§4.37 + §4.39, 2026-11-23).
     * Decided once at startup from the settings + the saved last channel:
     * - [StartupTarget.Onboarding] — first run (`AppSettings.firstRunDone
     *   == false`, CONTRACT: added by the settings builder): the UI must
     *   navigate to the "onboarding_wizard" route.
     * - [StartupTarget.ResumeChannel] — `autoResume` is on AND a last
     *   channel id was saved AND the channel still exists in the lists:
     *   the UI must navigate straight to the player
     *   (`Routes.player("live", channelId)`).
     * - [StartupTarget.Normal] — anything else (option off, no channel
     *   saved, or the saved channel was deleted / the subscription ended):
     *   the existing Splash -> Loading/Home or Login flow.
     *
     * Null until the decision is made. SplashScreen (other builder) must
     * collect this flow and navigate once it is non-null (after the PIN
     * unlock when the app lock is on, as today).
     */
    sealed interface StartupTarget {
        data object Normal : StartupTarget
        data object Onboarding : StartupTarget
        data class ResumeChannel(val channelId: String) : StartupTarget
    }

    private val _startupTarget = MutableStateFlow<StartupTarget?>(null)
    val startupTarget: StateFlow<StartupTarget?> = _startupTarget.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            // firstRunDone trap: the default is false, so a user who
            // installed before the wizard existed (playlists already
            // present) would be sent to Onboarding on every launch.
            // A returning user is never a first run: persist
            // firstRunDone = true BEFORE deciding, so the wizard only
            // ever shows on a genuinely fresh install.
            val hasPlaylists = runCatching {
                playlistRepository.observePlaylists().first().isNotEmpty()
            }.getOrDefault(false)
            val effectiveFirstRunDone = if (!settings.firstRunDone && hasPlaylists) {
                runCatching {
                    settingsRepository.update { it.copy(firstRunDone = true) }
                }
                true
            } else {
                settings.firstRunDone
            }
            // CONTRACT: AppSettings.firstRunDone (default false) and
            // AppSettings.lastChannelId (default null, written by
            // SettingsStore.setLastChannelId on every channel change) are
            // added by the settings builder.
            _startupTarget.value = when {
                !effectiveFirstRunDone -> StartupTarget.Onboarding
                settings.autoResume -> {
                    val lastId = settings.lastChannelId
                    val exists = lastId != null && runCatching {
                        contentRepository.getChannel(lastId)
                    }.getOrNull() != null
                    if (exists) StartupTarget.ResumeChannel(lastId!!) else StartupTarget.Normal
                }
                else -> StartupTarget.Normal
            }
        }
    }

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
