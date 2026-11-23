package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ManageKind { LIVE, FILMS, SERIES }

data class ManageDraftState(
    val bouquets: List<Bouquet> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val films: List<Film> = emptyList(),
    val series: List<Series> = emptyList(),
    val kind: ManageKind = ManageKind.LIVE,
    val selectedBouquetId: String? = null,
    val selectedItemIds: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val saved: Boolean = false
)

/**
 * Manage (Gérer la playlist): all edits happen on a draft; nothing is
 * persisted until Save -> ContentRepository.saveManageDraft (SPEC §5).
 * Rename / reorder stay single-item; hide/show/move work on the multi-selection.
 * After a bulk move the selection is cleared; after bulk hide/show it is kept
 * so the same group can then be moved (prototype behaviour).
 */
@HiltViewModel
class ManageViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _draft = MutableStateFlow(ManageDraftState())
    val draft: StateFlow<ManageDraftState> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                combine(
                    contentRepository.observeBouquets(BouquetKind.LIVE),
                    contentRepository.observeBouquets(BouquetKind.FILMS),
                    contentRepository.observeBouquets(BouquetKind.SERIES)
                ) { live, films, series -> Triple(live, films, series) },
                combine(
                    contentRepository.observeChannels(),
                    contentRepository.observeFilms(),
                    contentRepository.observeSeries()
                ) { channels, films, series -> Triple(channels, films, series) }
            ) { bouquets, rest ->
                val (live, films, series) = bouquets
                val (channels, filmList, seriesList) = rest
                val allBouquets = live + films + series
                _draft.update {
                    it.copy(
                        bouquets = allBouquets, channels = channels,
                        films = filmList, series = seriesList,
                        selectedBouquetId = it.selectedBouquetId
                            ?: allBouquets.firstOrNull { b -> b.kind == BouquetKind.LIVE }?.id
                    )
                }
            }.collect()
        }
    }

    fun setKind(kind: ManageKind) = _draft.update { it.copy(kind = kind, selectedItemIds = emptySet()) }
    fun selectBouquet(id: String) = _draft.update { it.copy(selectedBouquetId = id, selectedItemIds = emptySet()) }

    fun toggleSelect(id: String) = _draft.update {
        val set = it.selectedItemIds.toMutableSet()
        if (!set.add(id)) set.remove(id)
        it.copy(selectedItemIds = set, multiSelect = set.isNotEmpty())
    }

    fun selectAll(ids: List<String>) = _draft.update { it.copy(selectedItemIds = ids.toSet(), multiSelect = true) }
    fun deselectAll() = _draft.update { it.copy(selectedItemIds = emptySet(), multiSelect = false) }

    /** Hide/show one item, or the whole multi-selection when ids is empty (uses selection). */
    fun setHidden(ids: Set<String>, hidden: Boolean) {
        val targets = ids.ifEmpty { _draft.value.selectedItemIds }
        _draft.update { d ->
            d.copy(
                channels = d.channels.map { if (it.id in targets) it.copy(hidden = hidden) else it },
                bouquets = d.bouquets.map { if (it.id in targets) it.copy(hidden = hidden) else it },
                films = d.films.map { if (it.id in targets) it.copy(hidden = hidden) else it },
                series = d.series.map { if (it.id in targets) it.copy(hidden = hidden) else it }
            )
        }
        // Selection is KEPT after hide/show so the same group can be moved next.
    }

    fun setHiddenSingle(id: String, hidden: Boolean) = setHidden(setOf(id), hidden)

    fun moveSelectedToBouquet(bouquetId: String) {
        val ids = _draft.value.selectedItemIds
        _draft.update { d ->
            d.copy(
                channels = d.channels.map { if (it.id in ids) it.copy(bouquetId = bouquetId) else it },
                selectedItemIds = emptySet(), multiSelect = false // cleared after a bulk move
            )
        }
    }

    fun moveSingleToBouquet(channelId: String, bouquetId: String) {
        _draft.update { d -> d.copy(channels = d.channels.map { if (it.id == channelId) it.copy(bouquetId = bouquetId) else it }) }
    }

    fun reorderBouquet(bouquetId: String, delta: Int) {
        _draft.update { d ->
            val list = d.bouquets.sortedBy { it.order }.toMutableList()
            val idx = list.indexOfFirst { it.id == bouquetId }
            val target = (idx + delta).coerceIn(0, list.size - 1)
            if (idx < 0 || target == idx) return@update d
            val item = list.removeAt(idx); list.add(target, item)
            d.copy(bouquets = list.mapIndexed { i, b -> b.copy(order = i) })
        }
    }

    fun reorderChannel(channelId: String, delta: Int) {
        _draft.update { d ->
            val bouquetChannels = d.channels.filter { it.bouquetId == d.selectedBouquetId }
            val idx = bouquetChannels.indexOfFirst { it.id == channelId }
            val target = (idx + delta).coerceIn(0, bouquetChannels.size - 1)
            if (idx < 0 || target == idx) return@update d
            val reordered = bouquetChannels.toMutableList().apply { add(target, removeAt(idx)) }
            val order = reordered.map { it.id }
            // Channels carry no order field in CONTRACT §3; the draft order is
            // preserved by the list order passed to saveManageDraft.
            val rest = d.channels.filter { it.bouquetId != d.selectedBouquetId }
            val reorderedChannels = reordered + rest
            d.copy(channels = reorderedChannels)
        }
    }

    fun renameBouquet(bouquetId: String, newName: String) {
        _draft.update { d -> d.copy(bouquets = d.bouquets.map { if (it.id == bouquetId) it.copy(name = newName) else it }) }
    }

    fun renameChannel(channelId: String, newName: String) {
        _draft.update { d -> d.copy(channels = d.channels.map { if (it.id == channelId) it.copy(customName = newName) else it }) }
    }

    fun addBouquet(name: String) {
        if (name.isBlank()) return
        _draft.update { d ->
            val bouquet = Bouquet(
                id = UUID.randomUUID().toString(), name = name.trim(),
                kind = BouquetKind.LIVE, order = d.bouquets.size, hidden = false
            )
            d.copy(bouquets = d.bouquets + bouquet, selectedBouquetId = bouquet.id)
        }
    }

    /** Number assignment 1–100, unique across channels; null clears it. Returns the conflicting channel name, if any. */
    fun assignNumber(channelId: String, number: Int?): String? {
        if (number != null && number !in 1..100) return null
        val conflict = if (number != null) {
            _draft.value.channels.firstOrNull { it.customNum == number && it.id != channelId }
        } else null
        if (conflict != null) return conflict.customName ?: conflict.name
        _draft.update { d ->
            d.copy(channels = d.channels.map { if (it.id == channelId) it.copy(customNum = number) else it })
        }
        viewModelScope.launch { contentRepository.setCustomNumber(channelId, number) }
        return null
    }

    fun save() = viewModelScope.launch {
        val d = _draft.value
        contentRepository.saveManageDraft(d.bouquets, d.channels, d.films, d.series)
        _draft.update { it.copy(saved = true) }
    }

    fun clearSavedFlag() = _draft.update { it.copy(saved = false) }
}
