package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.KidsFilter
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveUiState(
    val bouquets: List<Bouquet> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val selectedBouquetId: String? = null,
    val selectedChannel: Channel? = null,
    val nowProgram: EpgProgram? = null,
    val nextPrograms: List<EpgProgram> = emptyList()
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val _selectedBouquetId = MutableStateFlow<String?>(null)
    private val _selectedChannelId = MutableStateFlow<String?>(null)
    /** «Toutes» pill (model renderLiveCats): all bouquets at once. */
    private val _showAllBouquets = MutableStateFlow(false)
    val selectedBouquetId: StateFlow<String?> = _selectedBouquetId.asStateFlow()
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    val state: StateFlow<LiveUiState> = combine(
        combine(
            contentRepository.observeBouquets(BouquetKind.LIVE),
            contentRepository.observeChannels(),
            settingsRepository.settings
        ) { bouquets, channels, settings -> Triple(bouquets, channels, settings) },
        combine(
            _selectedBouquetId, _selectedChannelId, _showAllBouquets
        ) { bouquetId, channelId, showAll -> Triple(bouquetId, channelId, showAll) }
    ) { (bouquets, channels, settings), (bouquetId, channelId, showAll) ->
        // Kids mode (2026-11-22): bouquets still filter by NAME (a bouquet
        // has no description); channels filter by CONTENT — category +
        // description via KidsFilter.isKidContent.
        val kidFilteredBouquets = if (settings.kidsMode) bouquets.filter { KidsFilter.isKidCategory(it.name) } else bouquets
        val kidFilteredChannels = if (settings.kidsMode) channels.filter { KidsFilter.isKidContent(it.category, it.description) } else channels
        val visibleBouquets = kidFilteredBouquets.filter { !it.hidden }
        val bouquet = if (showAll) null else visibleBouquets.firstOrNull { it.id == bouquetId } ?: visibleBouquets.firstOrNull()
        val bouquetChannels = if (showAll) kidFilteredChannels.filter { !it.hidden }
            else kidFilteredChannels.filter { it.bouquetId == bouquet?.id && !it.hidden }
        val channel = bouquetChannels.firstOrNull { it.id == channelId } ?: bouquetChannels.firstOrNull()
        LiveUiState(
            bouquets = visibleBouquets, channels = bouquetChannels,
            selectedBouquetId = bouquet?.id, selectedChannel = channel
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState())

    /** EPG (now + next) for the currently selected channel. */
    val epg: StateFlow<List<EpgProgram>> = _selectedChannelId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else contentRepository.observeEpg(id, System.currentTimeMillis(), System.currentTimeMillis() + 6 * 60 * 60 * 1000L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectBouquet(id: String) {
        _showAllBouquets.value = false
        _selectedBouquetId.value = id
        _selectedChannelId.value = null
    }

    /** «Toutes» pill: show every (visible) channel, no bouquet filter. */
    fun selectAll() {
        _showAllBouquets.value = true
        _selectedBouquetId.value = null
        _selectedChannelId.value = null
    }

    /** Viewport «Favori» button (model toggleCurrentChannelFav). */
    fun toggleFavorite() = viewModelScope.launch {
        val channel = state.value.selectedChannel ?: return@launch
        contentRepository.setFavorite(ContentKind.LIVE, channel.id, !channel.isFavorite)
    }

    fun selectChannel(id: String) { _selectedChannelId.value = id }

    /** Digit entry: custom number first, then the built-in server number. */
    suspend fun resolveChannelNumber(digits: String): Channel? {
        val n = digits.toIntOrNull() ?: return null
        val list = contentRepository.observeChannels().first()
        return list.firstOrNull { it.customNum == n }
            ?: list.firstOrNull { it.num.trimStart('0').toIntOrNull() == n }
    }
}
