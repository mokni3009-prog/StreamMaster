package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.matchcenter.LiveMatch
import com.streammaster.tv.data.matchcenter.MatchCenterApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live-scores ticker feed (§4.55): today's matches currently in play,
 * from the same MatchCenterApi (ESPN) as Match Center (§4.33) — no new
 * source, no manual Match Center dialog involved. Refreshes every 60 s
 * while the player is open, same cadence as MatchCenterViewModel.
 * Only `state == "in"` matches are exposed; anything unparsable or a
 * failed poll simply keeps the previous list (never throws).
 */
@HiltViewModel
class LiveScoresTickerViewModel @Inject constructor(
    private val api: MatchCenterApi
) : ViewModel() {

    private val _liveMatches = MutableStateFlow<List<LiveMatch>>(emptyList())
    val liveMatches: StateFlow<List<LiveMatch>> = _liveMatches.asStateFlow()

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
        _liveMatches.value = list.filter { it.state == "in" }
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }
}
