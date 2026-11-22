package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.UserProfile
import com.streammaster.tv.data.repository.ProfileRepository
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

/** User profiles — per-person history + watch-later (2026-11-22).
 *
 * v1 limits: favourites and "Reprendre" progress stay shared across
 * profiles; history and watch-later are per profile. */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val profiles: StateFlow<List<UserProfile>> = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileId: StateFlow<String> = settingsRepository.settings
        .map { it.activeProfileId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    /** True when the last PIN check failed (profile switch). */
    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    init {
        viewModelScope.launch {
            if (profileRepository.getProfile("default") == null) {
                profileRepository.saveProfile(
                    UserProfile("default", "Principal", 44, "", System.currentTimeMillis())
                )
            }
        }
    }

    fun addProfile(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val id = "p${System.currentTimeMillis()}"
        profileRepository.saveProfile(
            UserProfile(id, trimmed, (0..360).random(), "", System.currentTimeMillis())
        )
        switchProfile(id)
    }

    fun deleteProfile(id: String) = viewModelScope.launch {
        if (id == "default") return@launch
        profileRepository.deleteProfile(id)
        if (settingsRepository.settings.first().activeProfileId == id) switchProfile("default")
    }

    fun switchProfile(id: String) = viewModelScope.launch {
        settingsRepository.update { it.copy(activeProfileId = id) }
    }

    /** Switches to a PIN-protected profile only when the PIN matches. */
    fun switchProfileWithPin(id: String, pin: String) = viewModelScope.launch {
        val profile = profileRepository.getProfile(id)
        if (profile == null || (profile.pin.isNotEmpty() && profile.pin != pin)) {
            _pinError.value = true
            return@launch
        }
        _pinError.value = false
        switchProfile(id)
    }

    fun setProfilePin(id: String, pin: String) = viewModelScope.launch {
        val profile = profileRepository.getProfile(id) ?: return@launch
        profileRepository.saveProfile(profile.copy(pin = pin))
    }

    fun clearPinError() { _pinError.value = false }
}
