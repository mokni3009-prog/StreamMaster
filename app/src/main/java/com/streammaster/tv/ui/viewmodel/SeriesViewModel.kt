package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.KidsFilter
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SeriesSort { DEFAULT, RATED, SEASONS, AZ }

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val _sort = MutableStateFlow(SeriesSort.DEFAULT)
    val sort: StateFlow<SeriesSort> = _sort.asStateFlow()

    val series: StateFlow<List<Series>> = combine(
        contentRepository.observeSeries(), settingsRepository.settings, _sort
    ) { list, settings, sort ->
        // Kids mode (2026-11-22): content-based classification (genre +
        // description). The rating argument stays null: Series.rating is
        // the 0-10 provider score, not an age classification.
        val kidFiltered = if (settings.kidsMode) list.filter { KidsFilter.isKidContent(it.genre, it.description) } else list
        val visible = kidFiltered.filter { !it.hidden }
        when (sort) {
            SeriesSort.DEFAULT -> visible
            SeriesSort.RATED -> visible.sortedByDescending { it.rating }
            SeriesSort.SEASONS -> visible.sortedByDescending { it.seasons }
            SeriesSort.AZ -> visible.sortedBy { it.title }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(sort: SeriesSort) { _sort.value = sort }
    fun toggleFavorite(series: Series) = viewModelScope.launch {
        contentRepository.setFavorite(ContentKind.SERIES, series.id, !series.isFavorite)
    }
}
