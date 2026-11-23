package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Loading: waits until content is present, then the screen navigates home.
 * [stepsReady] drives the prototype's 3-step sync UI (screen-sync):
 * 1. live channels & EPG, 2. VOD films, 3. series & episodes.
 */
@HiltViewModel
class LoadingViewModel @Inject constructor(
    contentRepository: ContentRepository,
    playlistRepository: PlaylistRepository
) : ViewModel() {
    private val contentReady: StateFlow<Triple<Boolean, Boolean, Boolean>> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries()
    ) { channels, films, series ->
        Triple(channels.isNotEmpty(), films.isNotEmpty(), series.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(false, false, false))

    val isReady: StateFlow<Boolean> = contentReady
        .map { (channels, films, series) -> channels || films || series }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val stepsReady: StateFlow<Triple<Boolean, Boolean, Boolean>> = contentReady

    val playlistName: StateFlow<String> = playlistRepository.observePlaylists()
        .map { list -> (list.firstOrNull { it.isActive } ?: list.firstOrNull())?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
}
