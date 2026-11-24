package com.streammaster.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.streammaster.tv.data.model.AppSettings
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private const val TAG = "StreamMaster"

/** Events emitted by [VlcEngine] (already marshalled onto the main thread). */
sealed interface VlcEvent {
    data object Playing : VlcEvent
    data class Buffering(val percent: Float) : VlcEvent
    data object Paused : VlcEvent
    data object Stopped : VlcEvent
    data object EndReached : VlcEvent
    data class Error(val message: String) : VlcEvent
    data class TimeChanged(val timeMs: Long) : VlcEvent
    data class LengthChanged(val lengthMs: Long) : VlcEvent
}

/**
 * libVLC wrapper (GUIDE §C).
 *
 * - C1: network/live caching 5000ms (set both in the LibVLC options and per-media).
 * - C2: HW decoding enabled per media (`setHWDecoderEnabled(true, false)`).
 * - C3/C4: `--vout=android_display --mediacodec-dr --spdif` (see [createLibVlc])
 *   + digital audio output enabled on the player.
 * - C6: `:rtsp-tcp` for RTSP URLs.
 * - C7: settings User-Agent (see [createLibVlc]).
 * - C8: `:http-continuous=1` for raw `.ts` streams ONLY (never HLS/files).
 * - C9: event-based recovery only — Buffering / EncounteredError / EndReached are
 *   forwarded to [PlayerManagerImpl]; the 12s stall timer lives in
 *   [StallDetector] and watches [timeMs] the same way it watches ExoPlayer.
 * - C12: `:deinterlace=auto` on every media.
 *
 * CRITICAL (C9): libVLC must NEVER be called from inside an event callback —
 * the listener only posts to [mainHandler] and all real work happens there.
 * All public methods must be called from the main thread.
 */
class VlcEngine(
    private val context: Context,
    private val settings: AppSettings,
    private val onEvent: (VlcEvent) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var pendingSeekMs: Long = 0L

    val player: MediaPlayer? get() = mediaPlayer
    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val timeMs: Long get() = mediaPlayer?.time ?: -1L
    val lengthMs: Long get() = mediaPlayer?.length ?: -1L

    private fun ensureCreated() {
        if (libVlc != null) return
        val vlc = createLibVlc(context, settings)
        libVlc = vlc
        val player = MediaPlayer(vlc)
        mediaPlayer = player
        try {
            player.setAudioDigitalOutputEnabled(true) // C4: Dolby/DTS passthrough.
        } catch (e: Exception) {
            Log.w(TAG, "VLC digital audio output not available", e)
        }
        // C9: the callback only posts; handleEvent() runs on the main handler.
        player.setEventListener { event ->
            mainHandler.post { handleEvent(event) }
        }
        videoLayout?.let { layout ->
            try {
                player.attachViews(layout, null, true, false)
            } catch (e: Exception) {
                Log.w(TAG, "VLC attachViews failed", e)
            }
        }
    }

    /** Called by the UI (via PlayerManager.vlcVideoLayout) when the surface exists. */
    fun attachLayout(layout: VLCVideoLayout?) {
        videoLayout = layout
        val player = mediaPlayer ?: return
        if (layout == null) {
            try {
                player.detachViews()
            } catch (e: Exception) {
                Log.w(TAG, "VLC detachViews failed", e)
            }
            return
        }
        try {
            player.attachViews(layout, null, true, false)
        } catch (e: Exception) {
            Log.w(TAG, "VLC attachViews failed", e)
        }
    }

    fun play(url: String, startPositionMs: Long = 0L) {
        ensureCreated()
        val player = mediaPlayer ?: return
        val vlc = libVlc ?: return
        try {
            player.stop()
        } catch (e: Exception) {
            Log.w(TAG, "VLC stop before play failed", e)
        }
        pendingSeekMs = startPositionMs
        val media = Media(vlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false) // C2: hardware decoding.
        if (url.startsWith("rtsp://", ignoreCase = true)) {
            media.addOption(":rtsp-tcp") // C6: RTP over TCP when UDP is blocked.
        }
        media.addOption(":deinterlace=auto") // C12.
        media.addOption(":network-caching=5000") // C1.
        if (isRawTsUrl(url)) {
            media.addOption(":http-continuous=1") // C8: raw .ts only.
        }
        player.media = media
        media.release()
        player.play()
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.w(TAG, "VLC pause failed", e)
        }
    }

    fun resume() {
        try {
            mediaPlayer?.play()
        } catch (e: Exception) {
            Log.w(TAG, "VLC resume failed", e)
        }
    }

    fun stop() {
        pendingSeekMs = 0L
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "VLC stop failed", e)
        }
    }

    fun seekTo(ms: Long) {
        try {
            mediaPlayer?.time = ms
        } catch (e: Exception) {
            Log.w(TAG, "VLC seek failed", e)
        }
    }

    // L-09: libVLC identifies tracks by TrackDescription.id — the ids are
    // unique but NOT guaranteed to count from 0 or contiguously, so the
    // list index is the wrong key. The id is also what setAudioTrack /
    // setSpuTrack expect (libvlc_*_set_track takes the track id).
    fun audioTracks(): List<TrackOption> = try {
        mediaPlayer?.audioTracks
            ?.map { track -> TrackOption("vlc-a${track.id}", track.name ?: "Audio ${track.id}") }
            .orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "VLC audio tracks unavailable", e)
        emptyList()
    }

    fun subtitleTracks(): List<TrackOption> = try {
        mediaPlayer?.spuTracks
            ?.map { track -> TrackOption("vlc-s${track.id}", track.name ?: "Sous-titre ${track.id}") }
            .orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "VLC subtitle tracks unavailable", e)
        emptyList()
    }

    fun setAudioTrack(id: String?): Boolean {
        val player = mediaPlayer ?: return false
        // L-09: null disables the audio track in libVLC (-1), it used to
        // return false and leave the track playing.
        if (id == null) {
            return try {
                player.setAudioTrack(-1)
            } catch (e: Exception) {
                Log.w(TAG, "VLC disable audio failed", e)
                false
            }
        }
        val trackId = id.removePrefix("vlc-a").toIntOrNull() ?: return false
        return try {
            player.setAudioTrack(trackId)
        } catch (e: Exception) {
            Log.w(TAG, "VLC setAudioTrack failed", e)
            false
        }
    }

    fun setSubtitleTrack(id: String?): Boolean {
        val player = mediaPlayer ?: return false
        if (id == null) {
            // -1 disables subtitles in libVLC.
            return try {
                player.setSpuTrack(-1)
            } catch (e: Exception) {
                Log.w(TAG, "VLC disable subtitles failed", e)
                false
            }
        }
        val trackId = id.removePrefix("vlc-s").toIntOrNull() ?: return false
        return try {
            player.setSpuTrack(trackId)
        } catch (e: Exception) {
            Log.w(TAG, "VLC setSpuTrack failed", e)
            false
        }
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "VLC stop on release failed", e)
        }
        try {
            mediaPlayer?.detachViews()
        } catch (e: Exception) {
            Log.w(TAG, "VLC detachViews on release failed", e)
        }
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "VLC player release failed", e)
        }
        mediaPlayer = null
        try {
            libVlc?.release()
        } catch (e: Exception) {
            Log.w(TAG, "LibVLC release failed", e)
        }
        libVlc = null
        videoLayout = null
    }

    private fun handleEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                if (pendingSeekMs > 0) {
                    val seek = pendingSeekMs
                    pendingSeekMs = 0L
                    try {
                        mediaPlayer?.time = seek
                    } catch (e: Exception) {
                        Log.w(TAG, "VLC pending seek failed", e)
                    }
                }
                onEvent(VlcEvent.Playing)
            }
            MediaPlayer.Event.Buffering -> onEvent(VlcEvent.Buffering(event.buffering))
            MediaPlayer.Event.Paused -> onEvent(VlcEvent.Paused)
            MediaPlayer.Event.Stopped -> onEvent(VlcEvent.Stopped)
            MediaPlayer.Event.EndReached -> onEvent(VlcEvent.EndReached)
            MediaPlayer.Event.EncounteredError -> onEvent(
                VlcEvent.Error("VLC encountered an error")
            )
            MediaPlayer.Event.TimeChanged -> onEvent(VlcEvent.TimeChanged(event.timeChanged))
            MediaPlayer.Event.LengthChanged -> onEvent(VlcEvent.LengthChanged(event.lengthChanged))
        }
    }

    private fun isRawTsUrl(url: String): Boolean {
        val lower = url.lowercase().substringBefore('?')
        return lower.endsWith(".ts") && !lower.contains(".m3u8")
    }
}
