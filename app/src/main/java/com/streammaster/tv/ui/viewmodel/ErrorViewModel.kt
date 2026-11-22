package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.streammaster.tv.player.PlayerManager
import com.streammaster.tv.data.model.PlaybackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Error screen ViewModel: surfaces the PlayerManager error state and
 * retries the current item (same engine fallback chain is owned by agent 3).
 */
@HiltViewModel
class ErrorViewModel @Inject constructor(
    private val playerManager: PlayerManager
) : ViewModel() {
    val state get() = playerManager.state

    fun retry() {
        val item = playerManager.state.value.item ?: return
        when (item) {
            is PlaybackItem.Live -> playerManager.play(item)
            is PlaybackItem.Movie -> playerManager.play(item)
            is PlaybackItem.EpisodeItem -> playerManager.play(item)
            is PlaybackItem.RecordingItem -> playerManager.play(item)
            is PlaybackItem.DownloadItem -> playerManager.play(item)
        }
    }

    fun stop() = playerManager.stop()
}
