package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Film
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Contrôle parental (PC01) — locked-content editor, draft + save like the
 * prototype's parentalDraft.
 *
 * Locks are stored in AppSettings, exactly as SettingsRepositoryImpl
 * enforces them:
 * - lockedBouquets: map kind-name ("LIVE" | "FILMS" | "SERIES") -> bouquet
 *   IDS (isLocked() checks the id, and a live channel whose bouquetId is
 *   locked). Films/series have no bouquetId in the model, so for them only
 *   item locks apply to the items themselves; a locked film/series bouquet
 *   is still stored and shown.
 * - lockedItems: item ids (channel / film / series).
 *
 * Nothing is persisted until Save -> SettingsRepository.update.
 */
data class ParentalDraftState(
    val bouquets: Map<String, List<String>> = emptyMap(),
    val items: Set<String> = emptySet(),
    val loaded: Boolean = false,
    val saved: Boolean = false
)

@HiltViewModel
class ParentalViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.streammaster.tv.data.model.AppSettings())

    val liveBouquets: StateFlow<List<Bouquet>> = contentRepository.observeBouquets(BouquetKind.LIVE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filmBouquets: StateFlow<List<Bouquet>> = contentRepository.observeBouquets(BouquetKind.FILMS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val seriesBouquets: StateFlow<List<Bouquet>> = contentRepository.observeBouquets(BouquetKind.SERIES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val channels: StateFlow<List<Channel>> = contentRepository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val films: StateFlow<List<Film>> = contentRepository.observeFilms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val series: StateFlow<List<Series>> = contentRepository.observeSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draft = MutableStateFlow(ParentalDraftState())
    val draft: StateFlow<ParentalDraftState> = _draft.asStateFlow()

    private val _toastRes = MutableStateFlow<Int?>(null)
    val toastRes: StateFlow<Int?> = _toastRes.asStateFlow()

    /** Number of unsaved changes (map entries + item ids that differ). */
    val dirtyCount: StateFlow<Int> = combine(_draft, settings) { d, s ->
        if (!d.loaded) 0 else {
            val bouquetDiff = (d.bouquets.keys + s.lockedBouquets.keys).sumOf { key ->
                (d.bouquets[key].orEmpty().toSet() xor s.lockedBouquets[key].orEmpty().toSet()).size
            }
            val itemDiff = (d.items xor s.lockedItems.toSet()).size
            bouquetDiff + itemDiff
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                if (!_draft.value.loaded) {
                    _draft.value = ParentalDraftState(
                        bouquets = s.lockedBouquets,
                        items = s.lockedItems.toSet(),
                        loaded = true
                    )
                }
            }
        }
    }

    private infix fun <T> Set<T>.xor(other: Set<T>): Set<T> = (this - other) + (other - this)

    fun bouquetsFor(kind: ManageKind): List<Bouquet> = when (kind) {
        ManageKind.LIVE -> liveBouquets.value
        ManageKind.FILMS -> filmBouquets.value
        ManageKind.SERIES -> seriesBouquets.value
    }

    /** Locked state of a bouquet id in the draft. */
    fun isBouquetLocked(kind: ManageKind, bouquetId: String): Boolean =
        _draft.value.bouquets[kind.name].orEmpty().contains(bouquetId)

    /** "Partial": the bouquet itself is free but at least one of its items
     *  is locked (live only — films/series have no bouquet link). */
    fun isBouquetPartial(kind: ManageKind, bouquetId: String): Boolean {
        if (isBouquetLocked(kind, bouquetId)) return false
        if (kind != ManageKind.LIVE) return false
        val items = _draft.value.items
        return channels.value.any { it.bouquetId == bouquetId && it.id in items }
    }

    /** Locked state of an item: in lockedItems, or (live) in a locked bouquet. */
    fun isItemLocked(kind: ManageKind, itemId: String, bouquetId: String?): Boolean {
        if (itemId in _draft.value.items) return true
        if (kind == ManageKind.LIVE && bouquetId != null && isBouquetLocked(kind, bouquetId)) return true
        return false
    }

    /** OK on a bouquet row: toggle the whole bouquet lock. */
    fun toggleBouquet(kind: ManageKind, bouquetId: String) {
        val locked = isBouquetLocked(kind, bouquetId)
        if (locked) unlockBouquet(kind, bouquetId) else lockBouquet(kind, bouquetId)
    }

    fun lockBouquet(kind: ManageKind, bouquetId: String) {
        _draft.update { d ->
            val list = (d.bouquets[kind.name].orEmpty() + bouquetId).distinct()
            d.copy(bouquets = d.bouquets + (kind.name to list))
        }
        _toastRes.value = com.streammaster.tv.R.string.pc_locked_toast
    }

    fun unlockBouquet(kind: ManageKind, bouquetId: String) {
        _draft.update { d ->
            val list = d.bouquets[kind.name].orEmpty() - bouquetId
            d.copy(bouquets = d.bouquets + (kind.name to list))
        }
        _toastRes.value = com.streammaster.tv.R.string.pc_unlocked_toast
    }

    /** OK on an item row: toggle the single-item lock. */
    fun toggleItem(itemId: String) {
        val locked = itemId !in _draft.value.items
        _draft.update { d ->
            val set = d.items.toMutableSet()
            if (locked) set.add(itemId) else set.remove(itemId)
            d.copy(items = set)
        }
        _toastRes.value = if (locked) com.streammaster.tv.R.string.pc_locked_toast
        else com.streammaster.tv.R.string.pc_unlocked_toast
    }

    /** "Tout verrouiller (cet onglet)": lock every bouquet of the tab. */
    fun lockAll(kind: ManageKind) {
        val ids = bouquetsFor(kind).map { it.id }
        _draft.update { d -> d.copy(bouquets = d.bouquets + (kind.name to ids)) }
        _toastRes.value = com.streammaster.tv.R.string.pc_locked_toast
    }

    /** "Tout déverrouiller (cet onglet)": clear every lock of the tab
     *  (its bouquets + its items). */
    fun unlockAll(kind: ManageKind) {
        val bouquetIds = bouquetsFor(kind).map { it.id }.toSet()
        val itemIds = when (kind) {
            ManageKind.LIVE -> channels.value.map { it.id }.toSet()
            ManageKind.FILMS -> films.value.map { it.id }.toSet()
            ManageKind.SERIES -> series.value.map { it.id }.toSet()
        }
        _draft.update { d ->
            d.copy(
                bouquets = d.bouquets + (kind.name to emptyList()),
                items = d.items - itemIds - bouquetIds
            )
        }
        _toastRes.value = com.streammaster.tv.R.string.pc_unlocked_toast
    }

    fun save() = viewModelScope.launch {
        val d = _draft.value
        settingsRepository.update { s ->
            s.copy(
                lockedBouquets = d.bouquets.filterValues { it.isNotEmpty() },
                lockedItems = d.items.toList()
            )
        }
        _draft.update { it.copy(saved = true) }
    }

    fun clearSavedFlag() = _draft.update { it.copy(saved = false) }
    fun clearToast() { _toastRes.value = null }
}
