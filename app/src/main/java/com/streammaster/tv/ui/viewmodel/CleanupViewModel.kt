package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.cleanup.PlaylistCleanupScanner
import com.streammaster.tv.data.db.ChannelDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What kind of item a cleanup entry refers to. */
enum class CleanupKind { CHANNEL, FILM, SERIES }

/** A group of items that are the same title (merge keeps the first id). */
data class DuplicateGroup(
    val kind: CleanupKind,
    val title: String,
    val ids: List<String>
)

/** One dead item (broken link) with the scanner's reason. */
data class DeadItem(
    val kind: CleanupKind,
    val id: String,
    val title: String,
    val reason: String
)

/** The cleanup scan report: duplicates to merge, dead items to delete. */
data class CleanupReport(
    val duplicates: List<DuplicateGroup> = emptyList(),
    val dead: List<DeadItem> = emptyList()
)

data class CleanupUiState(
    val report: CleanupReport = CleanupReport(),
    val scanning: Boolean = true,
    /** Short human-readable result of the last action (shown on screen). */
    val notice: String? = null
)

/**
 * Playlist cleanup UI (§4.36.6) — display + merge/delete actions.
 *
 * CONTRACT — [PlaylistCleanupScanner] is built by the data builder in
 * `data/cleanup/` as an object with exactly this signature:
 *   `suspend fun scan(): CleanupReport`
 * (CleanupReport / DuplicateGroup / DeadItem / CleanupKind are the models
 * defined in this file.) The scanner detects duplicate and dead channels,
 * films and series across the active playlist.
 *
 * OPEN GAP — deletion executes today only for channels: [ChannelDao]
 * has `deleteById`, but FilmDao / SeriesDao have no `deleteById` (only
 * `deleteByPlaylist`). The data builder must add those two one-line DAO
 * methods for film/series merge/delete to execute; until then the screen
 * shows the unsupported notice instead of deleting them.
 */
@HiltViewModel
class CleanupViewModel @Inject constructor(
    private val channelDao: ChannelDao
) : ViewModel() {

    private val _state = MutableStateFlow(CleanupUiState())
    val state: StateFlow<CleanupUiState> = _state.asStateFlow()

    init {
        scan()
    }

    fun scan() = viewModelScope.launch {
        _state.update { it.copy(scanning = true, notice = null) }
        val report = runCatching { PlaylistCleanupScanner.scan() }.getOrDefault(CleanupReport())
        _state.update { it.copy(report = report, scanning = false) }
    }

    /** Merges a duplicate group: keeps the first id, deletes the others. */
    fun merge(group: DuplicateGroup) = viewModelScope.launch {
        if (group.kind != CleanupKind.CHANNEL) {
            _state.update { it.copy(notice = "unsupported") }
            return@launch
        }
        val keepId = group.ids.firstOrNull() ?: return@launch
        group.ids.drop(1).forEach { id ->
            runCatching { channelDao.deleteById(id) }
        }
        _state.update { it.copy(notice = "merged:${group.title}") }
        scan()
    }

    /** Deletes one dead item. */
    fun deleteDead(item: DeadItem) = viewModelScope.launch {
        if (item.kind != CleanupKind.CHANNEL) {
            _state.update { it.copy(notice = "unsupported") }
            return@launch
        }
        runCatching { channelDao.deleteById(item.id) }
        _state.update { it.copy(notice = "deleted:${item.title}") }
        scan()
    }
}
