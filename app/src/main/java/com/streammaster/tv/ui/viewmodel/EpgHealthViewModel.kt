package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.db.EpgDao
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Why a channel has no guide (§4.41). */
enum class EpgHealthReason {
    HAS_GUIDE,
    /** No tvg-id on the channel: nothing to match the guide against. */
    NO_EPG_ID,
    /** M3U playlist with no XMLTV source configured: the provider gives no EPG. */
    PROVIDER_NO_EPG,
    /** An XMLTV URL is configured but the channel still has no programs
     *  (source down, or the XMLTV id differs from the channel id). */
    XMLTV_DOWN_OR_MISMATCH,
    /** Xtream/Stalker provider EPG returned nothing for this channel. */
    PROVIDER_NO_GUIDE
}

data class EpgChannelHealth(
    val channelId: String,
    val channelName: String,
    val hasGuide: Boolean,
    val reason: EpgHealthReason
)

data class EpgHealthUiState(
    val channels: List<EpgChannelHealth> = emptyList(),
    val xmltvConfigured: Boolean = false,
    val loading: Boolean = true
) {
    val withGuide: Int get() = channels.count { it.hasGuide }
    val withoutGuide: Int get() = channels.count { !it.hasGuide }
}

/**
 * EPG health (§4.41): which channels have a guide and which do not, with a
 * probable cause and a fix suggestion per channel. The cause is a heuristic
 * from what the app can observe (tvg-id present? XMLTV configured?
 * playlist type?) — it is labelled as such on screen, not a certainty.
 */
@HiltViewModel
class EpgHealthViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val playlistRepository: PlaylistRepository,
    private val epgDao: EpgDao
) : ViewModel() {

    private val _state = MutableStateFlow(EpgHealthUiState())
    val state: StateFlow<EpgHealthUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val result = withContext(Dispatchers.IO) {
            val channels = runCatching { contentRepository.observeChannels().first() }.getOrDefault(emptyList())
            val playlist = runCatching { playlistRepository.getActive() }.getOrNull()
            val xmltvConfigured = !playlist?.xmltvUrl.isNullOrBlank()
            val now = System.currentTimeMillis()
            val from = now - 2 * 3600_000L
            val to = now + 24 * 3600_000L
            val rows = channels.map { channel ->
                val programs = runCatching { epgDao.getPrograms(channel.id, from, to) }.getOrDefault(emptyList())
                if (programs.isNotEmpty()) {
                    EpgChannelHealth(channel.id, channel.name, true, EpgHealthReason.HAS_GUIDE)
                } else {
                    val reason = when {
                        channel.epgId.isNullOrBlank() -> EpgHealthReason.NO_EPG_ID
                        playlist?.type == PlaylistType.M3U && !xmltvConfigured ->
                            EpgHealthReason.PROVIDER_NO_EPG
                        xmltvConfigured -> EpgHealthReason.XMLTV_DOWN_OR_MISMATCH
                        else -> EpgHealthReason.PROVIDER_NO_GUIDE
                    }
                    EpgChannelHealth(channel.id, channel.name, false, reason)
                }
            }
            EpgHealthUiState(channels = rows, xmltvConfigured = xmltvConfigured, loading = false)
        }
        _state.value = result
    }
}
