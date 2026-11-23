package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.HistoryEntry
import com.streammaster.tv.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One history row: the entry plus its sub-line detail, derived at
 *  display time from the catalog (R-S6, zero-diff C-5 — same rule as
 *  the model's `histSub`, which looks the item up live instead of
 *  storing the sub-line). No schema change: [HistoryEntry] is untouched
 *  and [detail] is null when the item left the catalog, in which case
 *  the screen falls back to the kind label alone. */
data class HistoryRow(
    val entry: HistoryEntry,
    /** Channel: "category • current programme"; film: "genre • year";
     *  series: "genre". Null when the item is not in the catalog. */
    val detail: String?
)

@HiltViewModel
class HistoriqueViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    val history: StateFlow<List<HistoryRow>> = combine(
        contentRepository.observeHistory(),
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries()
    ) { history, channels, films, series ->
        val channelById = channels.associateBy { it.id }
        val filmById = films.associateBy { it.id }
        val seriesById = series.associateBy { it.id }
        history.map { entry ->
            val detail = when (entry.kind) {
                ContentKind.LIVE -> channelById[entry.refId]?.let { channel ->
                    listOf(channel.category, channel.nowTitle)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                }?.takeIf { it.isNotEmpty() }
                ContentKind.FILM -> filmById[entry.refId]?.let { film ->
                    listOf(
                        film.genre,
                        film.year.takeIf { it > 0 }?.toString().orEmpty()
                    ).filter { it.isNotBlank() }.joinToString(" • ")
                }?.takeIf { it.isNotEmpty() }
                ContentKind.SERIES -> seriesById[entry.refId]?.genre
                    ?.takeIf { it.isNotBlank() }
            }
            HistoryRow(entry, detail)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() = viewModelScope.launch { contentRepository.clearHistory() }
}
