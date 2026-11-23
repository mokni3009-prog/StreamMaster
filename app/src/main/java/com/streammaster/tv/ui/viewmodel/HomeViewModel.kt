package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val expiryDate: String = "",
    val expiryDaysLeft: Int? = null,
    val channelCount: Int = 0,
    val filmCount: Int = 0,
    val seriesCount: Int = 0,
    val playlistCount: Int = 0,
    /** 4th stat on Home (prototype "Mes Favoris"): favourites across all kinds. */
    val favoriteCount: Int = 0,
    val recentFilms: List<Film> = emptyList(),
    val recentSeries: List<Series> = emptyList(),
    val popularFilms: List<Film> = emptyList(),
    val trendingSeries: List<Series> = emptyList(),
    val recommendedFilms: List<Film> = emptyList(),
    val recommendedSeries: List<Series> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    contentRepository: ContentRepository,
    playlistRepository: PlaylistRepository
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries(),
        playlistRepository.observePlaylists(),
        contentRepository.observeHistory()
    ) { channels: List<Channel>, films: List<Film>, series: List<Series>, playlists, history ->
        val active = playlists.firstOrNull { it.isActive } ?: playlists.firstOrNull()
        HomeUiState(
            expiryDate = active?.expiryDate ?: "",
            expiryDaysLeft = daysLeft(active?.expiryDate ?: ""),
            channelCount = channels.size, filmCount = films.size, seriesCount = series.size,
            playlistCount = playlists.size,
            favoriteCount = channels.count { it.isFavorite } + films.count { it.isFavorite } + series.count { it.isFavorite },
            // Dynamic "Récemment vus": progress recorded at pause/stop, cleared at >=95%.
            // Hidden items never appear on Home (same as Live/Epg/Favoris).
            recentFilms = films.filter { !it.hidden && it.recentProgress in 1..94 },
            recentSeries = series.filter { !it.hidden && it.recentProgress in 1..94 },
            popularFilms = films.filter { !it.hidden }.sortedByDescending { it.rating }.take(8),
            trendingSeries = series.filter { !it.hidden }.sortedByDescending { it.rating }.take(8),
            // Recommendations (2026-11-22): same genres as the last watched
            // titles, excluding already-watched ones.
            recommendedFilms = recommendFilms(films, history),
            recommendedSeries = recommendSeries(series, history)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** Unwatched films whose genre matches a recently watched film. */
    private fun recommendFilms(
        films: List<Film>,
        history: List<com.streammaster.tv.data.model.HistoryEntry>
    ): List<Film> {
        val watchedIds = history.filter { it.kind == com.streammaster.tv.data.model.ContentKind.FILM }
            .map { it.refId }.toSet()
        val watchedGenres = history.filter { it.kind == com.streammaster.tv.data.model.ContentKind.FILM }
            .mapNotNull { entry -> films.firstOrNull { it.id == entry.refId }?.genre }
            .toSet()
        if (watchedGenres.isEmpty()) return emptyList()
        return films.filter { !it.hidden && it.id !in watchedIds && it.genre in watchedGenres }
            .sortedByDescending { it.rating }.take(8)
    }

    /** Unwatched series whose genre matches a recently watched series. */
    private fun recommendSeries(
        series: List<Series>,
        history: List<com.streammaster.tv.data.model.HistoryEntry>
    ): List<Series> {
        val watchedIds = history.filter { it.kind == com.streammaster.tv.data.model.ContentKind.SERIES }
            .map { it.refId }.toSet()
        val watchedGenres = history.filter { it.kind == com.streammaster.tv.data.model.ContentKind.SERIES }
            .mapNotNull { entry -> series.firstOrNull { it.id == entry.refId }?.genre }
            .toSet()
        if (watchedGenres.isEmpty()) return emptyList()
        return series.filter { !it.hidden && it.id !in watchedIds && it.genre in watchedGenres }
            .sortedByDescending { it.rating }.take(8)
    }

    /** Days until the subscription expires (null when unknown). Accepts a
     *  unix timestamp (seconds or millis) or a yyyy-MM-dd / dd/MM/yyyy date. */
    private fun daysLeft(expiryDate: String): Int? {
        val trimmed = expiryDate.trim()
        if (trimmed.isBlank()) return null
        val millis = trimmed.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000 else it }
            ?: runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(trimmed)?.time }.getOrNull()
            ?: runCatching { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).parse(trimmed)?.time }.getOrNull()
            ?: return null
        return ((millis - System.currentTimeMillis()) / 86_400_000L).toInt()
    }
}
