package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.SearchResult
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.util.NaturalSearchParser
import com.streammaster.tv.data.util.SearchIntent
import com.streammaster.tv.player.remote.RemoteTextStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val filter: ContentKind? = null,
    val results: List<SearchResult> = emptyList(),
    val previousSearches: List<String> = emptyList(),
    val searching: Boolean = false,
    /** §4.36.2 — the parsed intent of the current query. The UI renders
     *  [SearchIntent.MatchesToday] from the Match Center feed (ESPN), not
     *  from [results] (which stays empty for it). */
    val intent: SearchIntent = SearchIntent.Keyword("")
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val remoteTextStore: RemoteTextStore
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        // Phone web remote (2026-11-22): text typed on the phone keyboard
        // is appended to the search query.
        viewModelScope.launch {
            remoteTextStore.text.collect { text ->
                if (text.isNotBlank()) setQuery(_state.value.query + text)
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            // §4.36.2 — rule-based intent layer first, keyword search last.
            val intent = NaturalSearchParser.parseIntent(query)
            val results = when (intent) {
                SearchIntent.MatchesToday -> emptyList()
                is SearchIntent.Films -> contentRepository
                    .searchFilmsByGenre(intent.genre, intent.newestFirst)
                    .map { SearchResult(ContentKind.FILM, it.id, it.customName ?: it.title, it.genre, it.hue) }
                is SearchIntent.Series -> contentRepository
                    .searchSeriesByGenre(intent.genre, intent.newestFirst)
                    .map { SearchResult(ContentKind.SERIES, it.id, it.customName ?: it.title, it.genre, it.hue) }
                is SearchIntent.Tonight -> {
                    val films = contentRepository.searchFilmsByGenre(intent.genre, true)
                        .map { SearchResult(ContentKind.FILM, it.id, it.customName ?: it.title, it.genre, it.hue) }
                    val series = contentRepository.searchSeriesByGenre(intent.genre, true)
                        .map { SearchResult(ContentKind.SERIES, it.id, it.customName ?: it.title, it.genre, it.hue) }
                    (films + series).take(50)
                }
                is SearchIntent.Keyword -> contentRepository.search(query, _state.value.filter)
            }
            _state.update { it.copy(results = results, searching = false, intent = intent) }
        }
    }

    fun setFilter(filter: ContentKind?) {
        _state.update { it.copy(filter = filter) }
        setQuery(_state.value.query)
    }

    init {
        viewModelScope.launch {
            _state.update { it.copy(previousSearches = contentRepository.getRecentSearches()) }
        }
    }

    fun submitSearch() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            contentRepository.recordSearch(q)
            _state.update { it.copy(previousSearches = contentRepository.getRecentSearches()) }
        }
    }

    fun runPreviousSearch(query: String) = setQuery(query)
}
