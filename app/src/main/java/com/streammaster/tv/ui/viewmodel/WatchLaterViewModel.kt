package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.WatchLaterItem
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.repository.WatchLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "À voir plus tard" — the active profile's watch-later list (2026-11-22). */
@HiltViewModel
class WatchLaterViewModel @Inject constructor(
    private val watchLaterRepository: WatchLaterRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val items: StateFlow<List<WatchLaterItem>> = settingsRepository.settings
        .flatMapLatest { settings -> watchLaterRepository.observeWatchLater(settings.activeProfileId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(item: WatchLaterItem) = viewModelScope.launch {
        watchLaterRepository.remove(item.kind, item.refId, item.profileId)
    }
}
