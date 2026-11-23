package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.R
import com.streammaster.tv.data.db.MyPlaylistBouquet
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.MyPlaylistRepositoryImpl
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * «My Playlist» (§4.62) — UI state for the virtual playlist #11.
 *
 * The ViewModel injects the concrete [MyPlaylistRepositoryImpl] directly
 * (it only needs StreamMasterDatabase, already provided by AppModule),
 * so no AppModule change is needed to use it.
 *
 * One instance per screen (hiltViewModel() is scoped to the destination's
 * back-stack entry): Live, Films, Series and Manage each keep their own
 * instance, so selection state never leaks between screens.
 */
@HiltViewModel
class MyPlaylistViewModel @Inject constructor(
    private val myPlaylistRepository: MyPlaylistRepositoryImpl,
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {

    // ─── My Playlist bouquets, per kind ──────────────────────────────────
    val liveBouquets: StateFlow<List<MyPlaylistBouquet>> =
        myPlaylistRepository.observeBouquets(BouquetKind.LIVE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filmBouquets: StateFlow<List<MyPlaylistBouquet>> =
        myPlaylistRepository.observeBouquets(BouquetKind.FILMS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val seriesBouquets: StateFlow<List<MyPlaylistBouquet>> =
        myPlaylistRepository.observeBouquets(BouquetKind.SERIES)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bouquetsFor(kind: BouquetKind): StateFlow<List<MyPlaylistBouquet>> = when (kind) {
        BouquetKind.LIVE -> liveBouquets
        BouquetKind.FILMS -> filmBouquets
        BouquetKind.SERIES -> seriesBouquets
    }

    // ─── Resolved My Playlist content (references resolved against the
    // source subscriptions; dead references resolve to nothing) ──────────
    val myChannels: StateFlow<List<Channel>> =
        myPlaylistRepository.observeChannels(null)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val myFilms: StateFlow<List<Film>> =
        myPlaylistRepository.observeFilms(null)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mySeries: StateFlow<List<Series>> =
        myPlaylistRepository.observeSeries(null)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** itemId -> bouquetId, so screens can filter resolved content per bouquet. */
    private fun bouquetMap(kind: ContentKind): StateFlow<Map<String, Long>> =
        myPlaylistRepository.observeAllItems()
            .map { items ->
                items.filter {
                    when (kind) {
                        ContentKind.LIVE -> it.itemType == "CHANNEL" || it.itemType == "LIVE"
                        ContentKind.FILM -> it.itemType == "FILM"
                        ContentKind.SERIES -> it.itemType == "SERIES"
                    }
                }.associate { it.itemId to it.bouquetId }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val myChannelBouquet: StateFlow<Map<String, Long>> = bouquetMap(ContentKind.LIVE)
    val myFilmBouquet: StateFlow<Map<String, Long>> = bouquetMap(ContentKind.FILM)
    val mySeriesBouquet: StateFlow<Map<String, Long>> = bouquetMap(ContentKind.SERIES)

    // ─── Active (real) subscription ──────────────────────────────────────
    /** The active real subscription (My Playlist is virtual and never active). */
    val activePlaylist: StateFlow<Playlist?> =
        playlistRepository.observePlaylists()
            .map { list -> list.firstOrNull { it.isActive } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The active subscription's bouquets, per kind (for the second named section). */
    val activeLiveBouquets: StateFlow<List<Bouquet>> =
        contentRepository.observeBouquets(BouquetKind.LIVE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeFilmBouquets: StateFlow<List<Bouquet>> =
        contentRepository.observeBouquets(BouquetKind.FILMS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeSeriesBouquets: StateFlow<List<Bouquet>> =
        contentRepository.observeBouquets(BouquetKind.SERIES)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All channels of the active subscription (focus restore: channel -> bouquet). */
    val allActiveChannels: StateFlow<List<Channel>> =
        contentRepository.observeChannels()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── One-shot messages (shown as a toast by the screen) ─────────────
    private val _messageRes = MutableStateFlow<Int?>(null)
    val messageRes: StateFlow<Int?> = _messageRes.asStateFlow()
    fun clearMessage() = _messageRes.update { null }

    // ─── Bouquet management ──────────────────────────────────────────────
    fun createBouquet(name: String, kind: BouquetKind) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        myPlaylistRepository.createBouquet(name, kind)
    }

    fun renameBouquet(id: Long, name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        myPlaylistRepository.renameBouquet(id, name)
    }

    fun deleteBouquet(id: Long) = viewModelScope.launch {
        myPlaylistRepository.deleteBouquet(id)
    }

    // ─── Adding content (references — the original stays in its subscription) ──
    /**
     * Adds the given source items to a My Playlist bouquet.
     * [sourcePlaylistId] is the subscription the items come from (the
     * active subscription in Manage); playback later uses that
     * subscription. Shows a toast: added, or already-in-bouquet when
     * nothing new was added.
     */
    fun addToBouquet(
        bouquetId: Long,
        kind: ContentKind,
        sourcePlaylistId: String,
        itemIds: List<String>
    ) = viewModelScope.launch {
        val added = myPlaylistRepository.addItems(bouquetId, sourcePlaylistId, kind, itemIds)
        _messageRes.value =
            if (added > 0) R.string.my_playlist_added else R.string.my_playlist_already_added
    }

    /** Creates a bouquet and adds the items to it in one step (Manage dialog). */
    fun createBouquetAndAdd(
        name: String,
        kind: BouquetKind,
        sourcePlaylistId: String,
        itemType: ContentKind,
        itemIds: List<String>
    ) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val bouquet = myPlaylistRepository.createBouquet(name, kind)
        val added = myPlaylistRepository.addItems(bouquet.id, sourcePlaylistId, itemType, itemIds)
        _messageRes.value =
            if (added > 0) R.string.my_playlist_added else R.string.my_playlist_already_added
    }

    fun removeItemByRef(bouquetId: Long, kind: ContentKind, itemId: String) =
        viewModelScope.launch {
            myPlaylistRepository.removeItemByRef(bouquetId, kind, itemId)
        }

    /** «Favori» toggle for a channel browsed from My Playlist (same source item). */
    fun toggleChannelFavorite(channel: Channel) = viewModelScope.launch {
        contentRepository.setFavorite(ContentKind.LIVE, channel.id, !channel.isFavorite)
    }
}
