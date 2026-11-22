package com.streammaster.tv.player

import androidx.media3.exoplayer.ExoPlayer
import com.streammaster.tv.data.model.PlaybackItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

// TODO(server): intro/outro seconds should come from server metadata.
// These are the demo constants approved in SPEC §4.1; the server must eventually
// provide per-title intro/outro offsets.
const val INTRO_FILM_S = 60
const val INTRO_SERIES_S = 90
const val OUTRO_FILM_S = 120
const val OUTRO_SERIES_S = 90

/** Which engine is currently driving playback. */
enum class Engine { EXOPLAYER, VLC }

enum class QualityMode { AUTO, Q480, Q720, Q1080, Q4K }

enum class DisplayMode { FILL, ORIGINAL, ZOOM }

data class PlaybackState(
    val item: PlaybackItem? = null,
    val engine: Engine = Engine.EXOPLAYER,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isTimeshift: Boolean = false,
    val atLiveEdge: Boolean = true,
    val isPip: Boolean = false,
    val sleepRemainingMin: Int = 0,
    val audioTrackLabel: String = "Français 5.1",
    val subtitleLabel: String = "Aucun",
    val qualityLabel: String = "Auto",
    val displayMode: DisplayMode = DisplayMode.FILL,
    val speedLabel: String = "1x",
    /** Live network speed, e.g. "12.5 Mb/s" (max-speed mode, 2026-11-22);
     *  "" while nothing has been measured yet. */
    val networkSpeedLabel: String = "",
    /** True while playing a backup (mirror) URL after the primary failed. */
    val usingBackup: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String = ""
)

data class TrackOption(val id: String, val label: String)

/**
 * One-shot player events (toasts, navigation requests) emitted by the engine.
 *
 * NOTE (deviation from CONTRACT §5): this `events` flow and the [PlayerEvent] type are
 * an addition by the player agent (task instruction). The contract's PlayerManager had
 * no channel for the engine to surface toasts ("Intro ignorée", "Film terminé", sleep
 * timer fired) or the instant next-episode handover. The UI (agent 2) should collect
 * [PlayerManager.events] and show toasts / react to [PlayerEvent.NextEpisode].
 */
sealed interface PlayerEvent {
    data class Toast(val message: String) : PlayerEvent
    data class NextEpisode(val seriesId: String, val season: Int, val episode: Int) : PlayerEvent
}

interface PlayerManager {
    val state: StateFlow<PlaybackState>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    /** Added by the player agent — see [PlayerEvent]. Not in CONTRACT §5. */
    val events: SharedFlow<PlayerEvent>

    /** Current video frame rate (0 when unknown) — used for frame-rate
     *  switching (2026-11-22). */
    val videoFrameRate: StateFlow<Float>

    /** Non-null on API >= 23 (Media3 minSdk is 23). */
    val exoPlayer: ExoPlayer?

    /** Non-null when VLC is the active engine. */
    val vlcPlayer: MediaPlayer?

    /**
     * Set by the UI when the VLC video surface is created.
     *
     * NOTE (deviation from CONTRACT §5): the contract declares this as a `val`, but
     * also says it is "set by UI" — that is only possible as a `var`, so it is a `var`
     * here. The concrete type is libVLC's `org.videolan.libvlc.util.VLCVideoLayout`.
     */
    var vlcVideoLayout: VLCVideoLayout?

    fun play(item: PlaybackItem, resumePositionMs: Long = 0L)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(ms: Long)
    fun skip(deltaMs: Long)
    fun seekToLiveEdge()
    fun setQuality(mode: QualityMode)
    fun setAudioTrack(id: String?)
    fun setSubtitleTrack(id: String?)
    fun setDisplayMode(mode: DisplayMode)
    fun setPlaybackSpeed(speed: Float)
    fun toggleTimeshift()
    fun enterPip()
    fun exitPip()
    fun setSleepTimer(minutes: Int)
    fun clearSleepTimer()
    fun audioTracks(): List<TrackOption>
    fun subtitleTracks(): List<TrackOption>
    fun qualityOptions(): List<TrackOption>
    fun release()
}
