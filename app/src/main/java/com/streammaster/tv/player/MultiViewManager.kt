package com.streammaster.tv.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"
const val MULTIVIEW_SLOT_COUNT = 4

data class MultiViewSlot(
    val index: Int,
    val channel: Channel?,
    val player: ExoPlayer?
)

/**
 * MultiView (2026-11-22): up to 4 live channels at once, each with its own
 * ExoPlayer instance (built with the same PlayerFactory as the main player).
 * Only the focused tile has sound; the others are muted. Players are
 * released by [stopAll] when the MultiView screen is left.
 *
 * Note: MultiView players use the default network settings (not the custom
 * DNS/User-Agent of the main player) and each filled tile counts as an extra
 * provider connection — one per tile.
 */
@Singleton
class MultiViewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _slots = MutableStateFlow<List<MultiViewSlot>>(
        List(MULTIVIEW_SLOT_COUNT) { MultiViewSlot(it, null, null) }
    )
    val slots: StateFlow<List<MultiViewSlot>> = _slots.asStateFlow()

    private var focusedIndex: Int = 0

    fun setChannel(index: Int, channel: Channel) {
        scope.launch {
            val current = _slots.value.toMutableList()
            val old = current.getOrNull(index) ?: return@launch
            if (old.channel?.id == channel.id) return@launch
            runCatching { old.player?.release() }
            val settings = settingsRepository.settings.first()
            val player = runCatching { createExoPlayer(context, settings) }.getOrNull()
            if (player == null) {
                Log.w(TAG, "MultiView: could not create player (API < 23?)")
                return@launch
            }
            player.volume = if (index == focusedIndex) 1f else 0f
            val url = runCatching {
                contentRepository.getStreamUrls(channel.id).firstOrNull()
            }.getOrNull() ?: channel.streamUrl
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
            current[index] = MultiViewSlot(index, channel, player)
            _slots.value = current
        }
    }

    fun clearChannel(index: Int) {
        val current = _slots.value.toMutableList()
        val old = current.getOrNull(index) ?: return
        runCatching { old.player?.release() }
        current[index] = MultiViewSlot(index, null, null)
        _slots.value = current
    }

    fun setFocused(index: Int) {
        focusedIndex = index
        _slots.value.forEach { slot ->
            slot.player?.volume = if (slot.index == index) 1f else 0f
        }
    }

    fun stopAll() {
        _slots.value.forEach { runCatching { it.player?.release() } }
        _slots.value = List(MULTIVIEW_SLOT_COUNT) { MultiViewSlot(it, null, null) }
        focusedIndex = 0
    }
}
