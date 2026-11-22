package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.matchcenter.LiveMatch
import com.streammaster.tv.data.matchcenter.MatchCenterApi
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Match Center — live football scores, refreshed every 60s while open
 *  (2026-11-22). The loop lives only while the screen/dialog is open. */
@HiltViewModel
class MatchCenterViewModel @Inject constructor(
    private val api: MatchCenterApi,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _matches = MutableStateFlow<List<LiveMatch>>(emptyList())
    val matches: StateFlow<List<LiveMatch>> = _matches.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Goal alerts on/off (2026-11-22) — the toggle in this screen and in
     *  Settings write the same setting; the alert service follows it. */
    val goalAlerts: StateFlow<Boolean> = settingsRepository.settings
        .map { it.goalAlerts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleGoalAlerts() = viewModelScope.launch {
        settingsRepository.update { it.copy(goalAlerts = !it.goalAlerts) }
    }

    private var refreshJob: Job? = null

    init {
        start()
    }

    fun start() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            while (true) {
                refreshOnce()
                delay(60_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        val list = runCatching { api.fetchToday() }.getOrDefault(emptyList())
        _matches.value = list
        _loading.value = false
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}
