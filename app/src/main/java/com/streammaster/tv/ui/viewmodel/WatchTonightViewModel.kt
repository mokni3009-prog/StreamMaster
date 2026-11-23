package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One of the 3–4 picks offered for tonight. */
data class TonightPick(
    val kind: ContentKind,
    val id: String,
    val title: String,
    val subtitle: String,
    /** Duration in minutes; 0 = live (fits any available time). */
    val durationMin: Int
)

data class WatchTonightUiState(
    val availableMinutes: Int = 60,
    val picks: List<TonightPick> = emptyList(),
    val loading: Boolean = true
)

/**
 * "Quoi regarder ce soir ?" (§4.36): asks how much time the user has, then
 * offers 3–4 picks from live + VOD that fit that time and match the taste
 * (genres of recently watched titles, same signal as the Home
 * recommendations). A film/series is only offered when its duration fits
 * the available time; live always fits. Episode length is estimated at
 * 45 min (no per-episode duration is stored for series).
 */
@HiltViewModel
class WatchTonightViewModel @Inject constructor(
    contentRepository: ContentRepository
) : ViewModel() {

    private val _availableMinutes = MutableStateFlow(60)

    val state: StateFlow<WatchTonightUiState> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries(),
        contentRepository.observeHistory(),
        _availableMinutes
    ) { channels, films, series, history, available ->
        val watchedFilmGenres = history
            .filter { it.kind == ContentKind.FILM }
            .mapNotNull { entry -> films.firstOrNull { it.id == entry.refId }?.genre }
            .toSet()
        val watchedSeriesGenres = history
            .filter { it.kind == ContentKind.SERIES }
            .mapNotNull { entry -> series.firstOrNull { it.id == entry.refId }?.genre }
            .toSet()
        val watchedGenres = watchedFilmGenres + watchedSeriesGenres

        val livePicks = channels
            .filter { !it.hidden && it.nowTitle.isNotBlank() }
            .sortedBy { it.name }
            .take(2)
            .map {
                TonightPick(
                    kind = ContentKind.LIVE, id = it.id, title = it.name,
                    subtitle = it.nowTitle, durationMin = 0
                )
            }
        val filmPicks = films
            .filter { !it.hidden && it.recentProgress == 0 && it.durationMin in 1..available }
            .filter { watchedGenres.isEmpty() || it.genre in watchedGenres }
            .sortedByDescending { it.rating }
            .take(2)
            .map {
                TonightPick(
                    kind = ContentKind.FILM, id = it.id, title = it.title,
                    subtitle = "${it.genre} • ${it.durationMin} min", durationMin = it.durationMin
                )
            }
        val seriesPicks = if (available >= 45) {
            series
                .filter { !it.hidden && it.recentProgress == 0 }
                .filter { watchedGenres.isEmpty() || it.genre in watchedGenres }
                .sortedByDescending { it.rating }
                .take(1)
                .map {
                    TonightPick(
                        kind = ContentKind.SERIES, id = it.id, title = it.title,
                        subtitle = it.genre, durationMin = 45
                    )
                }
        } else emptyList()

        val picks = (livePicks + filmPicks + seriesPicks).take(4)
        // Top up with any fitting top-rated film when the taste filter left
        // fewer than 3 picks (e.g. no watch history yet).
        val toppedUp = if (picks.size < 3) {
            val extra = films
                .filter { !it.hidden && it.recentProgress == 0 && it.durationMin in 1..available }
                .filter { film -> picks.none { it.id == film.id } }
                .sortedByDescending { it.rating }
                .take(3 - picks.size)
                .map {
                    TonightPick(
                        kind = ContentKind.FILM, id = it.id, title = it.title,
                        subtitle = "${it.genre} • ${it.durationMin} min", durationMin = it.durationMin
                    )
                }
            (picks + extra).take(4)
        } else picks
        WatchTonightUiState(availableMinutes = available, picks = toppedUp, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchTonightUiState())

    fun setAvailableMinutes(minutes: Int) {
        _availableMinutes.update { minutes }
    }
}
