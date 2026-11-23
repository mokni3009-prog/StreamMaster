package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavorisUiState(
    val channels: List<Channel> = emptyList(),
    val films: List<Film> = emptyList(),
    val series: List<Series> = emptyList()
)

@HiltViewModel
class FavorisViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    // §4.40 — favourites from ALL playlists, not just the active one.
    val state: StateFlow<FavorisUiState> = combine(
        contentRepository.observeAllChannels(),
        contentRepository.observeAllFilms(),
        contentRepository.observeAllSeries()
    ) { channels, films, series ->
        FavorisUiState(
            channels = channels.filter { it.isFavorite && !it.hidden },
            films = films.filter { it.isFavorite && !it.hidden },
            series = series.filter { it.isFavorite && !it.hidden }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FavorisUiState())

    fun toggleFavorite(kind: ContentKind, id: String, favorite: Boolean) = viewModelScope.launch {
        contentRepository.setFavorite(kind, id, favorite)
    }
}
