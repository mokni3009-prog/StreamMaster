package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.smartlists.SmartListsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One auto-generated smart list (§4.51): e.g. "Films d'action 2025" or
 * "Séries récentes". The items keep their own types so Home can open each
 * one with its normal route (player for a channel, detail for VOD).
 */
data class SmartList(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val films: List<Film> = emptyList(),
    val series: List<Series> = emptyList(),
    val channels: List<Channel> = emptyList()
)

/**
 * Smart lists (§4.51) — display side only.
 *
 * CONTRACT — [SmartListsProvider] is built by the data builder in
 * `data/smartlists/` as an object with exactly this signature:
 *   `fun getSmartLists(channels: List<Channel>, films: List<Film>,
 *                       series: List<Series>): List<SmartList>`
 * (SmartList is the model defined in this file.) Generation is pure and
 * synchronous: genre / year / recency groupings, no manual ordering.
 */
@HiltViewModel
class SmartListsViewModel @Inject constructor(
    contentRepository: ContentRepository
) : ViewModel() {

    val lists: StateFlow<List<SmartList>> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries()
    ) { channels, films, series ->
        SmartListsProvider.getSmartLists(channels, films, series)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
