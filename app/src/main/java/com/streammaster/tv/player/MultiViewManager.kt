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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
    private val settingsRepository: SettingsRepository,
    // R-05: the shared singleton NetworkStack — the 2-arg createExoPlayer()
    // built a brand-new CronetEngine + OkHttpClient per tile.
    private val networkStack: NetworkStack
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _slots = MutableStateFlow<List<MultiViewSlot>>(
        List(MULTIVIEW_SLOT_COUNT) { MultiViewSlot(it, null, null) }
    )
    val slots: StateFlow<List<MultiViewSlot>> = _slots.asStateFlow()

    private var focusedIndex: Int = 0

    // R-06: one job per tile — a new setChannel() for a tile cancels the
    // previous build, and stopAll()/clearChannel() cancel pending builds so
    // no player can be created (and leak, still playing) after the stop.
    private val slotJobs = Array<Job?>(MULTIVIEW_SLOT_COUNT) { null }

    fun setChannel(index: Int, channel: Channel) {
        if (index !in 0 until MULTIVIEW_SLOT_COUNT) return
        slotJobs[index]?.cancel()
        slotJobs[index] = null
        val current = _slots.value.toMutableList()
        val old = current.getOrNull(index) ?: return
        if (old.channel?.id == channel.id) return
        // Synchronous swap: release the old player NOW (before any
        // suspension point) so two concurrent calls can never release the
        // same player twice / leak the first of two new players.
        runCatching { old.player?.release() }
        current[index] = MultiViewSlot(index, channel, null)
        _slots.value = current
        slotJobs[index] = scope.launch {
            val settings = settingsRepository.settings.first()
            val player = runCatching {
                createExoPlayer(context, settings, networkStack, null)
            }.getOrNull()
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
            // Publish only if this build is still the live one for the
            // tile (not superseded by a newer setChannel/clear/stopAll).
            if (!isActive) {
                runCatching { player.release() }
                return@launch
            }
            val latest = _slots.value.toMutableList()
            val slot = latest.getOrNull(index) ?: run {
                runCatching { player.release() }
                return@launch
            }
            if (slot.channel?.id != channel.id) {
                runCatching { player.release() }
                return@launch
            }
            runCatching { slot.player?.release() }
            latest[index] = MultiViewSlot(index, channel, player)
            _slots.value = latest
        }
    }

    fun clearChannel(index: Int) {
        if (index !in 0 until MULTIVIEW_SLOT_COUNT) return
        slotJobs[index]?.cancel()
        slotJobs[index] = null
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
        slotJobs.forEach { it?.cancel() }
        slotJobs.fill(null)
        _slots.value.forEach { runCatching { it.player?.release() } }
        _slots.value = List(MULTIVIEW_SLOT_COUNT) { MultiViewSlot(it, null, null) }
        focusedIndex = 0
    }
}
