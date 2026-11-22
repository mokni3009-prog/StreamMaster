package com.streammaster.tv.player

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultBandwidthMeter
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.util.DeviceCaps
import org.videolan.libvlc.LibVLC

/**
 * Factories required by CONTRACT §9 / `di/PlayerModule.kt` (agent 4).
 *
 * Signatures are exactly:
 * - `fun createExoPlayer(context: Context, settings: AppSettings): ExoPlayer?`
 * - `fun createLibVlc(context: Context, settings: AppSettings): LibVLC`
 *
 * The extra trailing parameters all have defaults, so both functions remain
 * callable with exactly those two arguments (which is how PlayerModule calls
 * them). The overloads exist so [PlayerManagerImpl] can inject its shared
 * [NetworkStack] and the last measured bitrate instead of rebuilding them.
 *
 * ExoPlayer configuration (GUIDE §B):
 * - B1/B5: AdaptiveTrackSelection, bandwidth fraction 0.7, live-edge upshift
 *   guard 0.75 (the Media3 default — kept explicit).
 * - B2: max video size / bitrate caps; on low-RAM devices the cap is 720p/5 Mbps.
 * - B3: initial bitrate seeded from the last measured bandwidth (when the caller
 *   supplies it; the 2-arg call uses the Media3 default).
 * - B6: DefaultLoadControl min 25s / max 60s / buffer-for-playback 2.5s /
 *   after-rebuffer 5s, prioritize-time-over-size.
 * - B7: live offset is set per MediaItem in [NetworkStack.buildMediaItem].
 * - B10/B11: DefaultRenderersFactory with decoder fallback, tunneled playback on
 *   TV (via track-selection parameters), decoder pre-warming.
 * - B13/A5: Dolby passthrough / audio offload enabled via
 *   TrackSelectionParameters.AudioOffloadPreferences (DefaultAudioSink opens an
 *   AudioTrack in passthrough mode when AudioCapabilities report the codec).
 * - B18: wake mode is C.WAKE_MODE_LOCAL by default since Media3 1.9 — no code.
 * - B24: dynamic scheduling is the default in 1.11.0 — no code.
 * - B33: stuck detection tightened for live — 12s buffering / 10s playing without
 *   progress raises StuckPlayerException, handled in onPlayerError.
 */
@OptIn(UnstableApi::class)
fun createExoPlayer(context: Context, settings: AppSettings): ExoPlayer? =
    createExoPlayer(context, settings, NetworkStack.create(context, settings), null)

@OptIn(UnstableApi::class)
fun createExoPlayer(
    context: Context,
    settings: AppSettings,
    networkStack: NetworkStack,
    initialBitrateBps: Long?
): ExoPlayer? {
    // Media3 minSdk is 23 (app minSdk is 23 too) — this is just a safety guard.
    if (Build.VERSION.SDK_INT < 23) return null

    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true) // A8: software fallback if HW decoder fails.
        .experimentalSetEnableMediaCodecVideoRendererPrewarming(true) // B11.

    val adaptiveFactory = AdaptiveTrackSelection.Factory()
        // Max speed (2026-11-22): use 100% of the measured bandwidth and
        // upshift sooner; otherwise ~70% with the live-edge guard (B1/B5).
        .setBandwidthFraction(if (settings.maxSpeed) 1.0f else 0.7f) // B1.
        .setBufferedFractionToLiveEdgeForQualityIncrease(if (settings.maxSpeed) 0.5f else 0.75f) // B5.

    val trackSelector = DefaultTrackSelector(context, adaptiveFactory)
    trackSelector.parameters = buildTrackSelectionParameters(context, settings.maxSpeed)

    // Max speed (2026-11-22): start from the last measured bitrate, or 8 Mbps
    // on a fresh install so the first seconds are already top quality.
    val initialBitrate = when {
        initialBitrateBps != null -> initialBitrateBps
        settings.maxSpeed -> 8_000_000L
        else -> BitrateStore.DEFAULT_INITIAL_BITRATE
    }
    val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(initialBitrate) // B3: seed from the previous session (1.9.0 API; the 1.11.0 supplier variant does the same).
        .build()

    // Max speed (2026-11-22): a bigger prefetch buffer (30s min / 90s max)
    // so speed dips never stall playback; otherwise 25s / 60s (B6).
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ if (settings.maxSpeed) 30_000 else 25_000,
            /* maxBufferMs = */ if (settings.maxSpeed) 90_000 else 60_000,
            /* bufferForPlaybackMs = */ 2_500,
            /* bufferForPlaybackAfterRebufferMs = */ 5_000
        )
        .setPrioritizeTimeOverSizeThresholds(true) // B6: time over size for live.
        .build()

    return ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
        .setBandwidthMeter(bandwidthMeter)
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .setStuckBufferingDetectionTimeoutMs(12_000) // B33, tightened for live.
        .setStuckPlayingDetectionTimeoutMs(10_000)
        // Note: no setMediaSourceFactory here — the MediaSource is built per item
        // in PlayerManagerImpl via NetworkStack (cache only for VOD, CMCD on
        // HLS/DASH, RTSP factory for rtsp:// URLs).
        .build()
}

/**
 * Track-selection caps (B2) + tunneled playback (B10) + audio offload (B13/A5).
 * Max speed (2026-11-22): force the highest rendition the device can decode
 * instead of letting ABR step down. The low-RAM cap stays even in max-speed
 * mode — it is a decode limit of the device, not a network limit.
 */
@OptIn(UnstableApi::class)
internal fun buildTrackSelectionParameters(
    context: Context,
    maxSpeed: Boolean = false
): DefaultTrackSelector.Parameters {
    val builder = DefaultTrackSelector.Parameters.Builder(context)
        .setForceHighestSupportedBitrate(maxSpeed)
        .setTunnelingEnabled(true) // B10: tunneled playback on Android TV.
        .setAudioOffloadPreferences(
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                )
                .setIsGaplessSupportRequired(false)
                .setIsSpeedChangeSupportRequired(false)
                .build()
        )
    if (DeviceCaps.isLowRam(context)) {
        // Low-RAM mode (SPEC §10): cap the ABR ladder so a weak box never picks
        // a quality it cannot decode smoothly.
        builder.setMaxVideoSize(1280, 720)
        builder.setMaxVideoBitrate(5_000_000)
    }
    return builder.build()
}

/**
 * libVLC factory (GUIDE §C).
 *
 * Options (C1/C3/C4/C7): android_display video output + mediacodec direct
 * rendering (keeps HDR metadata), SPDIF passthrough for Dolby/DTS, 5s network
 * and live caching, and the settings User-Agent (some IPTV servers 403 the
 * default VLC UA).
 */
fun createLibVlc(context: Context, settings: AppSettings): LibVLC {
    val ua = NetworkStack.resolveUserAgentStatic(settings)
    val options = arrayListOf(
        "--vout=android_display",
        "--mediacodec-dr",
        "--spdif",
        "--network-caching=5000",
        "--live-caching=5000",
        "--http-user-agent=$ua"
    )
    return LibVLC(context, options)
}
