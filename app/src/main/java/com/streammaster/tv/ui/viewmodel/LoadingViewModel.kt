package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Loading: waits until content is present (demo data counts as ready), then the screen navigates home. */
@HiltViewModel
class LoadingViewModel @Inject constructor(
    contentRepository: ContentRepository
) : ViewModel() {
    val isReady: StateFlow<Boolean> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries()
    ) { channels, films, series -> channels.isNotEmpty() || films.isNotEmpty() || series.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}
