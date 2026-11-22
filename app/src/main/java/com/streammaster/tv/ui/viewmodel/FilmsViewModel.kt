package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.KidsFilter
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

enum class FilmSort { DEFAULT, RECENT, RATED, AZ }

@HiltViewModel
class FilmsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val _sort = MutableStateFlow(FilmSort.DEFAULT)
    val sort: StateFlow<FilmSort> = _sort.asStateFlow()

    val films: StateFlow<List<Film>> = combine(
        contentRepository.observeFilms(), settingsRepository.settings, _sort
    ) { films, settings, sort ->
        // Kids mode (2026-11-22): only kid-named genres.
        val kidFiltered = if (settings.kidsMode) films.filter { KidsFilter.isKidCategory(it.genre) } else films
        val visible = kidFiltered.filter { !it.hidden }
        when (sort) {
            FilmSort.DEFAULT -> visible
            FilmSort.RECENT -> visible.sortedByDescending { it.year }
            FilmSort.RATED -> visible.sortedByDescending { it.rating }
            FilmSort.AZ -> visible.sortedBy { it.title }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort(sort: FilmSort) { _sort.value = sort }
    fun toggleFavorite(film: Film) = viewModelScope.launch {
        contentRepository.setFavorite(ContentKind.FILM, film.id, !film.isFavorite)
    }
}
