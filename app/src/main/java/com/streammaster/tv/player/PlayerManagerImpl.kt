package com.streammaster.tv.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.StuckPlayerException
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.streammaster.tv.R
import com.streammaster.tv.data.db.ChannelDao
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.PlaybackItem
import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.RecordingStatus
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/** Same-engine retries before trying the next mirror (backoff 1s/2s/4s/8s). */
private val RETRY_BACKOFF_MS = longArrayOf(1000L, 2000L, 4000L, 8000L)
private const val TICKER_INTERVAL_MS = 500L
private const val BITRATE_SAVE_INTERVAL_MS = 30_000L

/** Predictive stall avoidance (§4.36.5): step the quality down when the
 *  bandwidth has fallen 3 samples in a row AND the buffer is below this. */
private const val PREDICTIVE_BUFFER_THRESHOLD_MS = 8_000L
/** At most one predictive step-down per 30s (no ABR oscillation). */
private const val PREDICTIVE_COOLDOWN_MS = 30_000L
/** Loudness normalisation (§4.36): fixed target gain in millibels (+3 dB —
 *  a conservative boost for quiet channels; tunable, see
 *  applyLoudnessEnhancer). */
private const val LOUDNESS_TARGET_GAIN_MB = 300

private enum class ErrorKind { SOURCE, RENDERER, UNEXPECTED }

/**
 * Player engine (CONTRACT §5 + GUIDE §B/§C/§D).
 *
 * - Engine selection: settings.engine ("exo" | "vlc" | "auto"; auto = ExoPlayer
 *   first). ExoPlayer is primary on API >= 23; VLC is primary — and the only
 *   engine — on API 21–22 (Media3 minSdk is 23).
 * - Fallback chain (D1): retry same engine with backoff (1s/2s/4s/8s) → next
 *   mirror URL (ContentRepository.getStreamUrls) → switch engine → error
 *   screen. A 12s stall (no playhead progress while playing/buffering) feeds
 *   the same chain, as does StuckPlayerException.
 * - onPlayerError (B32): TYPE_SOURCE → retry/mirror/VLC; TYPE_RENDERER →
 *   decoder fallback (already on, B10/A8) then VLC; TYPE_UNEXPECTED → log +
 *   switch engine.
 * - Intro/outro skip (SPEC §4.1): 60s/90s intro, 120s/90s outro — see
 *   [INTRO_FILM_S] etc. The next episode starts instantly, with no countdown.
 * - Continue-watching + history (SPEC §4.1), sleep timer, PiP state,
 *   timeshift, QoE logging (D9).
 *
 * Display mode (FILL/ORIGINAL/ZOOM) is stored in state only: the resize mode
 * is a PlayerView concern, so the UI (agent 2) applies
 * `state.displayMode` to its PlayerView / VLC layout.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository,
    private val channelDao: ChannelDao,
    private val settingsRepository: SettingsRepository,
    private val networkStack: NetworkStack,
    private val qoeLogger: QoeLogger,
    private val bitrateStore: BitrateStore,
    private val preloadHelper: PreloadHelper,
    // Point 2 — playback engine (2026-11-23). SettingsStore is injected
    // directly for the §4.37 last-channel write in play() below.
    private val settingsStore: SettingsStore,
    private val channelQualityStore: ChannelQualityStore,
    private val episodeResumeStore: EpisodeResumeStore,
    private val timeshiftBuffer: TimeshiftBuffer
) : PlayerManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _videoFrameRate = MutableStateFlow(0f)
    override val videoFrameRate: StateFlow<Float> = _videoFrameRate.asStateFlow()

    /** Failure diagnosis (§4.36.7): plain text + suggested fix, shown by
     *  ErrorScreen (other builder). Set on terminal failure, cleared on
     *  every new play(). */
    private val _lastErrorDiagnosis = MutableStateFlow("")
    override val lastErrorDiagnosis: StateFlow<String> = _lastErrorDiagnosis.asStateFlow()

    /** Current playback speed (0.5–2x), re-applied on every prepare. */
    private var currentSpeed: Float = 1f

    /** Current quality mode, re-applied when max speed flips (2026-11-22). */
    private var currentQualityMode: QualityMode = QualityMode.AUTO

    /** True when the current quality was set by the predictive step-down
     *  (§4.36.5) rather than by the user — the predictor may step down
     *  again from Q1080/Q720, but never overrides a manual choice. */
    private var qualitySetByPredictor: Boolean = false

    /** Loudness normaliser (§4.36, ExoPlayer only — see
     *  applyLoudnessEnhancer). */
    private var loudnessEnhancer: LoudnessEnhancer? = null

    /** Auto Arabic subtitles (§4.61): one attempt per play session, and
     *  never after the user picked a subtitle track manually. */
    private var arabicSubsAppliedForSession: Boolean = false
    private var userChoseSubtitle: Boolean = false

    /** Predictive stall avoidance (§4.36.5): recent bandwidth estimates. */
    private val bandwidthSamples = ArrayDeque<Long>()
    private var lastPredictiveStepDownMs: Long = 0L

    /** Timeshift (§4.45): the live item being buffered, and whether the
     *  player is currently playing the temp timeshift file (a RecordingItem)
     *  instead of the live stream. */
    private var timeshiftLiveItem: PlaybackItem.Live? = null
    private var timeshiftStartedAtMs: Long = 0L
    private var playingTimeshiftFile: Boolean = false

    /** Reason of the failure currently being recovered / just failed —
     *  feeds the diagnosis in showError(). */
    private var lastFailureReason: String = ""

    /** Throttle for the live network-speed label (1 update / second). */
    private var lastSpeedLabelMs: Long = 0L

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    // ---- Engines -------------------------------------------------------------

    private var exo: ExoPlayer? = null
    private var vlcEngine: VlcEngine? = null
    private var initialBitrateBps: Long = BitrateStore.DEFAULT_INITIAL_BITRATE

    override val exoPlayer: ExoPlayer? get() = exo
    override val vlcPlayer: MediaPlayer? get() = vlcEngine?.player

    override var vlcVideoLayout: VLCVideoLayout? = null
        set(value) {
            field = value
            vlcEngine?.attachLayout(value)
        }

    // ---- Playback bookkeeping -------------------------------------------------

    private var currentSettings: AppSettings = AppSettings()
    private var currentItem: PlaybackItem? = null
    private var currentUrls: List<String> = emptyList()
    private var mirrorIndex: Int = 0
    private var retryCount: Int = 0
    /** Per-play() guard so the broken-flag clear below writes at most once
     *  per playback session (onIsPlayingChanged / VLC Playing fire often). */
    private var brokenFlagClearedForSession: Boolean = false
    private var engineSwitched: Boolean = false
    private var activeEngine: Engine = Engine.EXOPLAYER
    private var playSessionId: Int = 0
    private var outroHandled: Boolean = false
    /** L-11: the very first STATE_BUFFERING of a session is the initial
     *  pre-roll, not a rebuffer — only log rebuffers after first READY. */
    private var seenFirstReady: Boolean = false
    private var timeshiftAnchorMs: Long = 0L
    private var lastBandwidthEstimateBps: Long = 0L
    private var lastBitrateSaveMillis: Long = 0L
    private var lastVideoBitrateLabel: String = ""

    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepEndMillis: Long = 0L

    private val stallDetector = StallDetector(scope) {
        val session = playSessionId
        val item = currentItem
        qoeLogger.logStall(item, activeEngine)
        Log.w(TAG, "Stall detected (no progress for 12s) on $activeEngine")
        handleFailure(session, "stall: no progress for 12s")
    }

    // ---- ExoPlayer listeners ---------------------------------------------------

    private val exoListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(isBuffering = true) }
                    if (seenFirstReady) qoeLogger.logRebufferStart(currentItem, activeEngine)
                }
                Player.STATE_READY -> {
                    seenFirstReady = true
                    if (_state.value.isBuffering) {
                        qoeLogger.logRebufferEnd(currentItem, activeEngine)
                    }
                    _state.update { it.copy(isBuffering = false) }
                }
                Player.STATE_ENDED -> {
                    _state.update { it.copy(isBuffering = false, isPlaying = false) }
                    if (currentItem !is PlaybackItem.Live) {
                        handleVodEnded()
                    } else {
                        handleFailure(playSessionId, "live stream ended")
                    }
                }
                Player.STATE_IDLE -> {
                    _state.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) clearBrokenFlagOnce()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "ExoPlayer error: ${error.errorCodeName} (${error.errorCode})", error)
            // L-05: StuckPlayerException is final and extends
            // IllegalStateException, NOT PlaybackException — onPlayerError
            // can never receive it directly, so the old `error is
            // StuckPlayerException` check was always false. Media3 wraps
            // it as the cause of the PlaybackException instead: walk the
            // cause chain.
            val stuckCause = generateSequence(error as Throwable) { it.cause }
                .any { it is StuckPlayerException }
            if (stuckCause) {
                qoeLogger.logStall(currentItem, activeEngine)
                handleFailure(playSessionId, "StuckPlayerException")
                return
            }
            when (classifyError(error)) {
                ErrorKind.RENDERER -> {
                    // Decoder fallback already ran inside the player (B10/A8);
                    // go straight to the other engine.
                    handleFailure(playSessionId, "renderer: ${error.errorCodeName}", forceEngineSwitch = true, errorCode = error.errorCode)
                }
                ErrorKind.SOURCE -> handleFailure(playSessionId, "source: ${error.errorCodeName}", errorCode = error.errorCode)
                ErrorKind.UNEXPECTED -> {
                    qoeLogger.logError(currentItem, activeEngine, "unexpected: ${error.errorCodeName}")
                    handleFailure(playSessionId, "unexpected: ${error.errorCodeName}", forceEngineSwitch = true, errorCode = error.errorCode)
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTrackLabels(tracks)
            exoPlayer?.videoFormat?.frameRate?.takeIf { it > 0f }?.let { _videoFrameRate.value = it }
            // §4.61 — an Arabic subtitle track, when present, is enabled
            // automatically for films/series (once per session, unless the
            // user picked a subtitle manually).
            maybeEnableArabicSubtitles()
        }
    }

    private val exoAnalyticsListener = object : AnalyticsListener {
        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
        ) {
            lastBandwidthEstimateBps = bitrateEstimate
            // Max speed (2026-11-22): live network speed in the player badges,
            // throttled to one state update per second.
            val now = System.currentTimeMillis()
            if (now - lastSpeedLabelMs >= 1_000) {
                lastSpeedLabelMs = now
                val label = formatMbps(bitrateEstimate)
                if (label != _state.value.networkSpeedLabel) {
                    _state.update { it.copy(networkSpeedLabel = label) }
                }
            }
            // §4.36.5 — predictive stall avoidance: step the quality down
            // BEFORE the buffer runs dry (logcat only, no UI change).
            onPredictiveBandwidth(bitrateEstimate)
        }

        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData
        ) {
            if (mediaLoadData.trackType != C.TRACK_TYPE_VIDEO) return
            val format: Format = mediaLoadData.trackFormat ?: return
            val label = formatLabel(format)
            if (lastVideoBitrateLabel.isNotEmpty() && label != lastVideoBitrateLabel) {
                qoeLogger.logAbrSwitch(currentItem, activeEngine, lastVideoBitrateLabel, label)
            }
            lastVideoBitrateLabel = label
            // Frame-rate switching (2026-11-22): report the video fps.
            if (format.frameRate > 0f) _videoFrameRate.value = format.frameRate
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: java.io.IOException,
            wasCanceled: Boolean
        ) {
            if (!wasCanceled) {
                qoeLogger.logError(currentItem, activeEngine, "load: ${error.message}")
            }
        }
    }

    init {
        scope.launch {
            settingsRepository.settings.collect {
                val maxSpeedChanged = it.maxSpeed != currentSettings.maxSpeed
                currentSettings = it
                // Max speed (2026-11-22): re-apply the rendition choice at once
                // when the setting flips (the factory-level knobs — bandwidth
                // fraction, buffers — apply from the next player creation).
                if (maxSpeedChanged) setQuality(currentQualityMode)
            }
        }
        scope.launch {
            bitrateStore.getLastMeasuredBitrate()?.let { initialBitrateBps = it }
        }
        // Eager ExoPlayer creation so the UI can bind it as soon as the player
        // screen opens (non-null on API >= 23, CONTRACT §5). MediaSources are
        // still built per item, with the settings in force at play time.
        ensureExoPlayer()
    }

    // ---- Public API ------------------------------------------------------------

    override fun play(item: PlaybackItem, resumePositionMs: Long) {
        // Save the previous VOD position before switching away.
        saveResumeFor(currentItem)
        // A new item drops any timeshift buffer — except the timeshift
        // file itself (§4.45: the temp file must survive the play() that
        // starts it; it is deleted by seekToLiveEdge / stop instead).
        val isTimeshiftFile = item is PlaybackItem.RecordingItem &&
            item.recording.id.startsWith("timeshift-")
        if (!isTimeshiftFile) {
            playingTimeshiftFile = false
            timeshiftLiveItem = null
            timeshiftBuffer.discard()
        }
        stopInternal()
        PlaybackService.start(context)
        playSessionId++
        val session = playSessionId
        currentItem = item
        retryCount = 0
        brokenFlagClearedForSession = false
        mirrorIndex = 0
        engineSwitched = false
        outroHandled = false
        seenFirstReady = false
        arabicSubsAppliedForSession = false
        userChoseSubtitle = false
        bandwidthSamples.clear()
        lastFailureReason = ""
        _lastErrorDiagnosis.value = ""
        networkStack.clearMirror()
        _state.update {
            it.copy(
                item = item, isPlaying = false, isBuffering = true,
                isTimeshift = false, atLiveEdge = true, usingBackup = false,
                showError = false, errorMessage = ""
            )
        }
        _positionMs.value = 0L
        _durationMs.value = 0L

        scope.launch {
            if (session != playSessionId) return@launch
            val urls = resolveUrls(item)
            if (session != playSessionId) return@launch
            if (urls.isEmpty()) {
                lastFailureReason = "no stream url"
                updateDiagnosis(lastFailureReason, null)
                val message = if (item is PlaybackItem.Live) {
                    "Impossible de lire cette chaîne"
                } else {
                    "Impossible de lire ce contenu"
                }
                showError(message)
                return@launch
            }
            currentUrls = urls
            // §4.37 — remember the last live channel permanently, on every
            // channel change. Catchup plays carry an encoded id
            // ("catchup:<id>:...") — never persist that as the last channel
            // (L-06), or resume-on-boot would look up a channel that does
            // not exist and lose the real last channel.
            if (item is PlaybackItem.Live && !isCatchupChannelId(item.channel.id)) {
                scope.launch(Dispatchers.IO) {
                    runCatching { settingsStore.setLastChannelId(item.channel.id) }
                        .onFailure { Log.w(TAG, "setLastChannelId failed", it) }
                }
            }
            activeEngine = resolveEngine(currentSettings)
            _state.update { it.copy(engine = activeEngine) }
            if (item is PlaybackItem.Live) {
                // §4.36 — per-channel smart quality: apply this channel's
                // stored mode AFTER the engine is resolved (applyQualityMode
                // only touches ExoPlayer params when ExoPlayer is active);
                // no entry = AUTO = highest quality possible (the existing
                // maxSpeed behaviour). Never persisted here.
                val savedQuality = runCatching {
                    channelQualityStore.getQuality(item.channel.id)
                }.getOrNull()
                qualitySetByPredictor = false
                applyQualityMode(savedQuality ?: QualityMode.AUTO, persist = false)
            }
            recordHistory(item)
            qoeLogger.logPlaybackStart(item, activeEngine)
            if (sleepJob == null && currentSettings.sleepMinutes > 0) {
                setSleepTimer(currentSettings.sleepMinutes)
            }
            val startPosition = introAdjustedStart(item, resumePositionMs)
            prepareCurrent(session, startPosition)
            startTicker()
            stallDetector.start(
                positionProvider = { currentPositionMs() },
                activeProvider = { _state.value.isPlaying || _state.value.isBuffering }
            )
            preloadNeighbours(item)
        }
    }

    override fun pause() {
        val item = currentItem ?: return
        saveResumeFor(item)
        when (activeEngine) {
            Engine.EXOPLAYER -> exo?.pause()
            Engine.VLC -> vlcEngine?.pause()
        }
        if (item is PlaybackItem.Live) {
            // Pausing a live stream freezes the playhead: that IS timeshift.
            // §4.45: also start capturing the stream to the temp timeshift
            // file, so the paused-away minutes are really watchable.
            timeshiftAnchorMs = currentPositionMs()
            _state.update { it.copy(isTimeshift = true, atLiveEdge = false) }
            beginTimeshiftRecording(item)
        }
        _state.update { it.copy(isPlaying = false) }
    }

    override fun resume() {
        // §4.45: resuming from a paused live channel plays the temp
        // timeshift file from the pause point (when it captured anything);
        // the freeze-only fallback resumes inside the DVR window instead.
        if (_state.value.isTimeshift && timeshiftBuffer.active) {
            finishTimeshiftAndPlayFile()
            return
        }
        if (_state.value.isTimeshift && currentItem is PlaybackItem.Live) {
            resumeFromTimeshiftAnchor()
            return
        }
        when (activeEngine) {
            Engine.EXOPLAYER -> exo?.play()
            Engine.VLC -> vlcEngine?.resume()
        }
        _state.update { it.copy(isPlaying = true) }
    }

    override fun stop() {
        val item = currentItem
        saveResumeFor(item)
        stopInternal()
        clearSleepTimer()
        // Leaving the player drops the timeshift buffer (§4.45).
        playingTimeshiftFile = false
        timeshiftLiveItem = null
        timeshiftBuffer.discard()
        PlaybackService.stop(context)
        preloadHelper.clear()
        _state.update {
            PlaybackState(engine = it.engine, displayMode = it.displayMode)
        }
        currentItem = null
        currentUrls = emptyList()
    }

    override fun seekTo(ms: Long) {
        val item = currentItem ?: return
        if (item is PlaybackItem.Live) {
            seekLive(ms)
        } else {
            val duration = currentDurationMs()
            val target = ms.coerceIn(0L, if (duration > 0) duration else ms)
            seekEngines(target)
            stallDetector.reset()
        }
    }

    override fun skip(deltaMs: Long) {
        val item = currentItem ?: return
        if (item is PlaybackItem.Live) {
            seekLive(currentPositionMs() + deltaMs)
        } else {
            seekTo(currentPositionMs() + deltaMs)
        }
    }

    override fun seekToLiveEdge() {
        // §4.45 — "Direct" while watching the temp timeshift file: back to
        // the live channel and delete the temp file.
        if (playingTimeshiftFile) {
            val live = timeshiftLiveItem
            playingTimeshiftFile = false
            timeshiftLiveItem = null
            timeshiftBuffer.discard()
            _state.update { it.copy(isTimeshift = false, atLiveEdge = true) }
            if (live != null) {
                play(live)
                return
            }
        }
        val item = currentItem ?: return
        if (item !is PlaybackItem.Live) return
        // Leaving timeshift (freeze or recording) drops the temp buffer.
        timeshiftLiveItem = null
        timeshiftBuffer.discard()
        _state.update { it.copy(isTimeshift = false, atLiveEdge = true) }
        when (activeEngine) {
            Engine.EXOPLAYER -> {
                exo?.seekToDefaultPosition() // B35: jump to the live edge.
                exo?.play()
            }
            Engine.VLC -> {
                // VLC has no live-edge API (C13): re-open the stream fresh.
                val url = currentUrls.getOrNull(mirrorIndex) ?: return
                vlcEngine?.play(url)
            }
        }
        stallDetector.reset()
    }

    /**
     * Manual quality choice (player menu): applies the mode and, for a live
     * channel, persists it for THAT channel only (§4.36 — per-channel smart
     * quality). VOD choices are session-only.
     */
    override fun setQuality(mode: QualityMode) {
        applyQualityMode(mode, persist = true)
    }

    /**
     * Applies [mode] to the engine. When [persist] is true the choice is a
     * user decision: it clears the predictor flag and is stored for the
     * current live channel only. Predictive step-downs (§4.36.5) and the
     * per-channel restore at play() pass persist=false so a temporary
     * downgrade never becomes the channel's stored quality.
     */
    private fun applyQualityMode(mode: QualityMode, persist: Boolean) {
        currentQualityMode = mode
        if (persist) qualitySetByPredictor = false
        val label = when (mode) {
            QualityMode.AUTO -> "Auto"
            QualityMode.Q480 -> "480p"
            QualityMode.Q720 -> "720p"
            QualityMode.Q1080 -> "1080p"
            QualityMode.Q4K -> "4K"
        }
        _state.update { it.copy(qualityLabel = label) }
        val exo = exo ?: return
        if (activeEngine != Engine.EXOPLAYER) return // VLC ABR is internal (C10).
        val base = buildTrackSelectionParameters(context, currentSettings.maxSpeed)
        val builder = base.buildUpon()
        when (mode) {
            // Max speed (2026-11-22): AUTO + max speed = highest rendition
            // forced by the base parameters above; a manual mode still wins.
            QualityMode.AUTO -> Unit // Base caps only (low-RAM cap if any).
            QualityMode.Q480 -> builder.setMaxVideoSize(854, 480).setMaxVideoBitrate(2_000_000)
            QualityMode.Q720 -> builder.setMaxVideoSize(1280, 720).setMaxVideoBitrate(5_000_000)
            QualityMode.Q1080 -> builder.setMaxVideoSize(1920, 1080).setMaxVideoBitrate(8_000_000)
            QualityMode.Q4K -> builder.setMaxVideoSize(3840, 2160).setMaxVideoBitrate(25_000_000)
        }
        exo.trackSelectionParameters = builder.build()
        if (persist) {
            val live = currentItem as? PlaybackItem.Live ?: return
            scope.launch {
                runCatching { channelQualityStore.setQuality(live.channel.id, mode) }
                    .onFailure { Log.w(TAG, "Saving per-channel quality failed", it) }
            }
        }
    }

    /** Formats a bitrate as "12.5 Mb/s" for the live speed badge. */
    private fun formatMbps(bps: Long): String =
        if (bps <= 0L) "" else "%.1f Mb/s".format(java.util.Locale.US, bps / 1_000_000.0)

    override fun setAudioTrack(id: String?) {
        val exo = exo
        if (activeEngine == Engine.EXOPLAYER && exo != null) {
            val builder = exo.trackSelectionParameters.buildUpon()
            val parsed = id?.let { parseTrackId(it, 'a') }
            if (parsed == null) {
                builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            } else {
                val (groupIndex, trackIndex) = parsed
                val group = exo.currentTracks.groups.getOrNull(groupIndex) ?: return
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            }
            exo.trackSelectionParameters = builder.build()
            refreshTrackLabels(exo.currentTracks)
        } else {
            if (vlcEngine?.setAudioTrack(id) == true) {
                val label = vlcEngine?.audioTracks()
                    ?.firstOrNull { it.id == id }?.label ?: _state.value.audioTrackLabel
                _state.update { it.copy(audioTrackLabel = label) }
            }
        }
    }

    override fun setSubtitleTrack(id: String?) {
        // A manual subtitle choice disables the §4.61 auto-Arabic pick for
        // this session (the auto path uses setSubtitleTrackInternal).
        userChoseSubtitle = true
        setSubtitleTrackInternal(id)
    }

    /** Subtitle selection without marking a manual user choice (§4.61). */
    private fun setSubtitleTrackInternal(id: String?) {
        val exo = exo
        if (activeEngine == Engine.EXOPLAYER && exo != null) {
            val builder = exo.trackSelectionParameters.buildUpon()
            if (id == null) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                val parsed = parseTrackId(id, 's')
                if (parsed != null) {
                    val (groupIndex, trackIndex) = parsed
                    val group = exo.currentTracks.groups.getOrNull(groupIndex) ?: return
                    builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                }
            }
            exo.trackSelectionParameters = builder.build()
            refreshTrackLabels(exo.currentTracks)
        } else {
            if (vlcEngine?.setSubtitleTrack(id) == true) {
                val label = if (id == null) "Aucun" else vlcEngine?.subtitleTracks()
                    ?.firstOrNull { it.id == id }?.label ?: _state.value.subtitleLabel
                _state.update { it.copy(subtitleLabel = label) }
            }
        }
    }

    override fun setDisplayMode(mode: DisplayMode) {
        // Stored in state; the UI applies the resize mode to its PlayerView /
        // VLC layout (agent 2). The engine itself keeps running untouched.
        _state.update { it.copy(displayMode = mode) }
    }

    /** Playback speed (2026-11-22): ExoPlayer PlaybackParameters, VLC setRate. */
    override fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2f)
        applySpeed()
        val label = if (currentSpeed == 1f) "1x"
        else "${String.format(Locale.US, "%g", currentSpeed)}x"
        _state.update { it.copy(speedLabel = label) }
    }

    private fun applySpeed() {
        exo?.setPlaybackParameters(PlaybackParameters(currentSpeed))
        runCatching { vlcEngine?.player?.setRate(currentSpeed) }
    }

    override fun toggleTimeshift() {
        val item = currentItem ?: return
        if (item !is PlaybackItem.Live) return
        if (!_state.value.isTimeshift) {
            // A6/B36: freeze the playhead; the stream keeps advancing behind it.
            // §4.45: also start capturing the stream to the temp file.
            timeshiftAnchorMs = currentPositionMs()
            when (activeEngine) {
                Engine.EXOPLAYER -> exo?.pause()
                Engine.VLC -> vlcEngine?.pause()
            }
            _state.update { it.copy(isTimeshift = true, isPlaying = false, atLiveEdge = false) }
            beginTimeshiftRecording(item)
        } else if (timeshiftBuffer.active) {
            // §4.45: resume by playing the temp file from the pause point.
            finishTimeshiftAndPlayFile()
        } else {
            // Freeze-only fallback (recording could not start): resume
            // inside the DVR window from the frozen position.
            resumeFromTimeshiftAnchor()
        }
    }

    override fun enterPip() {
        // A7: PiP is an Activity concern — the player and its surface are NOT
        // released, so playback keeps running in the floating window.
        _state.update { it.copy(isPip = true) }
    }

    override fun exitPip() {
        _state.update { it.copy(isPip = false) }
    }

    override fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _state.update { it.copy(sleepRemainingMin = 0) }
            return
        }
        sleepEndMillis = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepRemainingMin = minutes) }
        sleepJob = scope.launch {
            while (isActive) {
                delay(15_000)
                val remainingMs = sleepEndMillis - System.currentTimeMillis()
                if (remainingMs <= 0) break
                val remainingMin = ((remainingMs + 59_999) / 60_000).toInt()
                if (remainingMin != _state.value.sleepRemainingMin) {
                    _state.update { it.copy(sleepRemainingMin = remainingMin) }
                }
            }
            if (isActive) {
                _events.emit(PlayerEvent.Toast("Minuteur de sommeil terminé"))
                stop()
            }
        }
    }

    override fun clearSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndMillis = 0L
        _state.update { it.copy(sleepRemainingMin = 0) }
    }

    override fun audioTracks(): List<TrackOption> {
        val exo = exo
        return if (activeEngine == Engine.EXOPLAYER && exo != null) {
            tracksOfType(exo.currentTracks, C.TRACK_TYPE_AUDIO, 'a')
        } else {
            vlcEngine?.audioTracks().orEmpty()
        }
    }

    override fun subtitleTracks(): List<TrackOption> {
        val exo = exo
        return if (activeEngine == Engine.EXOPLAYER && exo != null) {
            tracksOfType(exo.currentTracks, C.TRACK_TYPE_TEXT, 's')
        } else {
            vlcEngine?.subtitleTracks().orEmpty()
        }
    }

    override fun qualityOptions(): List<TrackOption> = listOf(
        TrackOption("auto", "Auto"),
        TrackOption("480", "480p"),
        TrackOption("720", "720p"),
        TrackOption("1080", "1080p"),
        TrackOption("4k", "4K")
    )

    override fun release() {
        stopInternal()
        clearSleepTimer()
        // Leaving the player for good drops the timeshift buffer (§4.45).
        playingTimeshiftFile = false
        timeshiftLiveItem = null
        timeshiftBuffer.discard()
        PlaybackService.stop(context)
        preloadHelper.release()
        try {
            exo?.removeListener(exoListener)
            exo?.removeAnalyticsListener(exoAnalyticsListener)
        } catch (e: Exception) {
            Log.w(TAG, "ExoPlayer listener removal failed", e)
        }
        try {
            exo?.release()
        } catch (e: Exception) {
            Log.w(TAG, "ExoPlayer release failed", e)
        }
        exo = null
        try {
            vlcEngine?.release()
        } catch (e: Exception) {
            Log.w(TAG, "VLC release failed", e)
        }
        vlcEngine = null
        scope.cancel()
    }

    // ---- Engine construction ---------------------------------------------------

    private fun ensureExoPlayer(): ExoPlayer? {
        if (exo != null) return exo
        if (Build.VERSION.SDK_INT < 23) return null // Media3 minSdk is 23.
        val player = createExoPlayer(context, currentSettings, networkStack, initialBitrateBps)
            ?: return null
        player.addListener(exoListener)
        player.addAnalyticsListener(exoAnalyticsListener)
        exo = player
        return player
    }

    private fun ensureVlcEngine(): VlcEngine {
        var engine = vlcEngine
        if (engine == null) {
            engine = VlcEngine(context, currentSettings, ::handleVlcEvent)
            vlcEngine = engine
            vlcVideoLayout?.let { engine.attachLayout(it) }
        }
        return engine
    }

    private fun resolveEngine(settings: AppSettings): Engine {
        if (Build.VERSION.SDK_INT < 23) return Engine.VLC
        return when (settings.engine) {
            "vlc" -> Engine.VLC
            "auto" -> Engine.EXOPLAYER // Auto = ExoPlayer first, VLC on failure.
            else -> Engine.EXOPLAYER // "exo" (default) and anything unknown.
        }
    }

    // ---- Preparation / recovery --------------------------------------------------

    private suspend fun resolveUrls(item: PlaybackItem): List<String> = when (item) {
        is PlaybackItem.Live -> {
            // D2: primary + backup URLs from the data layer (backupUrls live in
            // ChannelEntity; exposed via getStreamUrls — CONTRACT §5 note).
            val urls = try {
                contentRepository.getStreamUrls(item.channel.id)
            } catch (e: Exception) {
                Log.w(TAG, "getStreamUrls failed", e)
                emptyList()
            }
            if (urls.isNotEmpty()) urls else listOf(item.channel.streamUrl)
        }
        is PlaybackItem.Movie -> listOf(item.film.streamUrl)
        // A recording is a local .ts file: ExoPlayer/VLC play it directly
        // (no network stack, no live configuration — it is VOD).
        is PlaybackItem.RecordingItem -> {
            // SAF folder recordings are content:// URIs; the app-private
            // folder recordings are plain file paths.
            val path = item.recording.filePath
            if (path.startsWith("content://")) listOf(path)
            else listOf(Uri.fromFile(File(path)).toString())
        }
        is PlaybackItem.DownloadItem -> {
            // Offline VOD file in the app-private downloads/ folder.
            listOf(Uri.fromFile(File(item.download.filePath)).toString())
        }
        is PlaybackItem.EpisodeItem -> {
            // Episode now carries its resolved stream URL (XtreamApi -> Episode
            // -> Room): prefer it, then resolve the exact episode by
            // (seriesId, code) — the S01E01 code alone is shared by every
            // series. A code that already looks like a URL is used directly.
            val direct = item.episode.streamUrl?.takeIf { it.isNotBlank() }
            if (direct != null) listOf(direct)
            else {
                val urls = try {
                    contentRepository.getEpisodeStreamUrls(item.episode.seriesId, item.episode.code)
                } catch (e: Exception) {
                    Log.w(TAG, "getEpisodeStreamUrls failed", e)
                    emptyList()
                }
                if (urls.isNotEmpty()) urls
                else if (item.episode.code.startsWith("http") ||
                    item.episode.code.startsWith("rtsp")
                ) {
                    listOf(item.episode.code)
                } else {
                    emptyList()
                }
            }
        }
    }

    private suspend fun prepareCurrent(session: Int, startPositionMs: Long) {
        if (session != playSessionId) return
        val url = currentUrls.getOrNull(mirrorIndex) ?: run {
            showError("Impossible de lire cette chaîne")
            return
        }
        // A fresh prepare restarts the stall watchdog: a rebuffering player
        // must not be declared stalled while it is (re)connecting.
        stallDetector.reset()
        // Live always (re)starts at the live edge; only VOD resumes mid-stream.
        // (An engine switch also drops any timeshift position — documented.)
        val start = if (currentItem is PlaybackItem.Live) 0L else startPositionMs
        when (activeEngine) {
            Engine.EXOPLAYER -> prepareExo(session, url, start)
            Engine.VLC -> prepareVlc(session, url, start)
        }
    }

    private suspend fun prepareExo(session: Int, url: String, startPositionMs: Long) {
        val player = ensureExoPlayer()
        if (player == null) {
            // API < 23 (should not happen — resolveEngine already picks VLC).
            activeEngine = Engine.VLC
            _state.update { it.copy(engine = Engine.VLC) }
            prepareVlc(session, url, startPositionMs)
            return
        }
        val item = currentItem ?: return
        val isLive = item is PlaybackItem.Live
        val mediaItem = networkStack.buildMediaItem(url, mediaIdOf(item), isLive)
        val source = if (url.startsWith("rtsp://", ignoreCase = true)) {
            // B30: RTSP gets its own factory (TCP fallback / force-TCP).
            val forceTcp = try {
                bitrateStore.isForceRtspTcp()
            } catch (e: Exception) {
                false
            }
            networkStack.rtspMediaSourceFactory(currentSettings, forceTcp)
                .createMediaSource(mediaItem)
        } else {
            preloadHelper.getMediaSource(mediaItem)
                ?: networkStack.mediaSourceFactory(currentSettings, !isLive)
                    .createMediaSource(mediaItem)
        }
        try {
            player.stop()
            player.setMediaSource(source)
            player.prepare()
            if (startPositionMs > 0) player.seekTo(startPositionMs)
            player.playWhenReady = true
            applySpeed()
            // §4.36 — loudness normalisation on the ExoPlayer audio session.
            applyLoudnessEnhancer()
        } catch (e: Exception) {
            Log.w(TAG, "ExoPlayer prepare failed", e)
            handleFailure(session, "prepare failed: ${e.message}")
        }
    }

    private fun prepareVlc(session: Int, url: String, startPositionMs: Long) {
        if (session != playSessionId) return
        val engine = ensureVlcEngine()
        try {
            engine.play(url, startPositionMs)
            applySpeed()
            // §4.61 (VLC): subtitle tracks appear asynchronously and VLC
            // exposes no tracks-changed event — one delayed auto-Arabic
            // attempt a few seconds after prepare (best effort; the
            // TimeChanged attempt below is the second chance).
            scope.launch {
                delay(3000)
                if (session == playSessionId) maybeEnableArabicSubtitles()
            }
        } catch (e: Exception) {
            Log.w(TAG, "VLC prepare failed", e)
            handleFailure(session, "vlc prepare failed: ${e.message}")
        }
    }

    /**
     * The recovery chain (D1). Called from onPlayerError, the stall detector and
     * VLC events. Every step re-checks [playSessionId] so a stale retry can never
     * hijack a newer playback.
     */
    private fun handleFailure(session: Int, reason: String, forceEngineSwitch: Boolean = false, errorCode: Int? = null) {
        if (session != playSessionId || currentItem == null) return
        val item = currentItem ?: return
        lastFailureReason = reason
        updateDiagnosis(reason, errorCode)
        scope.launch {
            if (session != playSessionId) return@launch
            qoeLogger.logError(item, activeEngine, reason)
            when {
                !forceEngineSwitch && retryCount < RETRY_BACKOFF_MS.size -> {
                    val delayMs = RETRY_BACKOFF_MS[retryCount]
                    retryCount++
                    Log.i(TAG, "Playback failure ($reason): same-engine retry $retryCount in ${delayMs}ms")
                    delay(delayMs)
                    if (session != playSessionId) return@launch
                    prepareCurrent(session, currentPositionMs())
                }
                !forceEngineSwitch && currentSettings.smartFailover && mirrorIndex + 1 < currentUrls.size -> {
                    mirrorIndex++
                    retryCount = 0
                    val mirror = currentUrls[mirrorIndex]
                    networkStack.setMirror(currentUrls.first(), mirror)
                    // Smart failover (2026-11-22): opt-in via the player
                    // toggle (off by default). The primary URL failed, so
                    // the player silently continues on a backup source
                    // (same channel from another playlist). Tell the user.
                    _state.update { it.copy(usingBackup = true) }
                    scope.launch { _events.emit(PlayerEvent.Toast("Source de secours")) }
                    // S-01: never log a full mirror URL — Xtream URLs carry
                    // the username/password in the path. Host only.
                    Log.i(TAG, "Playback failure ($reason): switching to mirror ${hostOf(mirror)}")
                    delay(500)
                    if (session != playSessionId) return@launch
                    prepareCurrent(session, currentPositionMs())
                }
                !engineSwitched -> {
                    engineSwitched = true
                    retryCount = 0
                    mirrorIndex = 0
                    networkStack.clearMirror()
                    _state.update { it.copy(usingBackup = false) }
                    val from = activeEngine
                    val to = if (from == Engine.EXOPLAYER) Engine.VLC else Engine.EXOPLAYER
                    if (to == Engine.EXOPLAYER && Build.VERSION.SDK_INT < 23) {
                        showError("Impossible de lire cette chaîne")
                        return@launch
                    }
                    qoeLogger.logEngineSwitch(item, from, to, reason)
                    Log.i(TAG, "Playback failure ($reason): switching engine $from -> $to")
                    // Capture the VOD resume point BEFORE stopping: stop()
                    // resets the ExoPlayer playhead to 0.
                    val resumeAt = currentPositionMs()
                    stopEnginesOnly()
                    activeEngine = to
                    _state.update { it.copy(engine = to, isBuffering = true) }
                    // Re-apply the current quality mode on the new engine
                    // (the per-channel restore at play() only touches the
                    // engine that was active then).
                    applyQualityMode(currentQualityMode, persist = false)
                    delay(300)
                    if (session != playSessionId) return@launch
                    prepareCurrent(session, resumeAt)
                }
                else -> showError("Impossible de lire cette chaîne")
            }
        }
    }

    private fun showError(message: String) {
        // Terminal failure (every failure path funnels here — see callers):
        // the recovery chain is exhausted, so the live channel is broken.
        // §4.36.7: keep the diagnosis of the failure that led here so
        // ErrorScreen can explain it (set by handleFailure / play()).
        if (_lastErrorDiagnosis.value.isBlank()) {
            updateDiagnosis(lastFailureReason, null)
        }
        markCurrentChannelBroken()
        stallDetector.stop()
        tickerJob?.cancel()
        tickerJob = null
        releaseLoudnessEnhancer()
        stopEnginesOnly()
        // R-02: play() started the foreground PlaybackService before the
        // URLs were resolved — a terminal failure must stop it (and its
        // WakeLock/WifiLock) instead of leaving it running behind the
        // error screen until the user exits by hand.
        PlaybackService.stop(context)
        _state.update { it.copy(showError = true, errorMessage = message, isPlaying = false, isBuffering = false) }
    }

    /**
     * SIGNAL FAIBLE badge (LiveScreen ChannelCard, gated on Channel.isBroken).
     * The flag lives in the channels table (no new column / migration: it is
     * part of the schema since v1) and reaches the UI through the existing
     * path — ChannelEntity.toModel() -> ContentRepositoryImpl.observeChannels()
     * (Room Flow) -> LiveViewModel -> LiveScreen — so a DAO write refreshes
     * the badge with no UI change. Writes run on Dispatchers.IO and never
     * fail playback: any error is only logged. VOD items are untouched
     * (their isBroken is a sync-time flag, not a playback verdict).
     */
    private fun markCurrentChannelBroken() {
        val live = currentItem as? PlaybackItem.Live ?: return
        // L-06: a catchup play carries an encoded id — never flag it.
        if (isCatchupChannelId(live.channel.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                channelDao.setBroken(live.channel.id, true)
            } catch (e: Exception) {
                Log.w(TAG, "setBroken(true) failed for ${live.channel.id}", e)
            }
        }
    }

    /** Clear the flag on the first successful playback of a session, so the
     *  badge does not stick after the channel works again. Guarded by
     *  [brokenFlagClearedForSession] (reset in play()): at most one write
     *  per session, however often Playing fires (pause/resume, rebuffer). */
    private fun clearBrokenFlagOnce() {
        if (brokenFlagClearedForSession) return
        val live = currentItem as? PlaybackItem.Live ?: return
        // L-06: a catchup play carries an encoded id — nothing to clear.
        if (isCatchupChannelId(live.channel.id)) return
        brokenFlagClearedForSession = true
        scope.launch(Dispatchers.IO) {
            try {
                channelDao.setBroken(live.channel.id, false)
            } catch (e: Exception) {
                Log.w(TAG, "setBroken(false) failed for ${live.channel.id}", e)
            }
        }
    }

    // ---- VLC events --------------------------------------------------------------

    private fun handleVlcEvent(event: VlcEvent) {
        when (event) {
            is VlcEvent.Playing -> {
                seenFirstReady = true // L-11: same first-buffering guard as Exo.
                _state.update { it.copy(isPlaying = true, isBuffering = false) }
                clearBrokenFlagOnce()
            }
            is VlcEvent.Buffering -> {
                val buffering = event.percent < 100f
                if (buffering && seenFirstReady) qoeLogger.logRebufferStart(currentItem, activeEngine)
                else if (!buffering) qoeLogger.logRebufferEnd(currentItem, activeEngine)
                _state.update { it.copy(isBuffering = buffering) }
            }
            is VlcEvent.Paused -> _state.update { it.copy(isPlaying = false) }
            is VlcEvent.Stopped -> _state.update { it.copy(isPlaying = false) }
            is VlcEvent.EndReached -> {
                _state.update { it.copy(isPlaying = false) }
                if (currentItem is PlaybackItem.Live) {
                    handleFailure(playSessionId, "VLC EndReached on live")
                } else {
                    handleVodEnded()
                }
            }
            is VlcEvent.Error -> handleFailure(playSessionId, event.message)
            is VlcEvent.TimeChanged -> {
                _positionMs.value = event.timeMs
                // §4.61 (VLC): subtitle tracks arrive with playback — try
                // the auto-Arabic pick once (guarded by the session flag).
                if (!arabicSubsAppliedForSession) maybeEnableArabicSubtitles()
            }
            is VlcEvent.LengthChanged -> _durationMs.value = event.lengthMs
        }
    }

    // ---- Intro / outro ------------------------------------------------------------

    private fun introSeconds(item: PlaybackItem): Int = when (item) {
        is PlaybackItem.Movie -> INTRO_FILM_S
        is PlaybackItem.EpisodeItem -> INTRO_SERIES_S
        is PlaybackItem.Live -> 0
        is PlaybackItem.RecordingItem -> 0
        is PlaybackItem.DownloadItem -> 0
    }

    private fun outroSeconds(item: PlaybackItem): Int = when (item) {
        is PlaybackItem.Movie -> OUTRO_FILM_S
        is PlaybackItem.EpisodeItem -> OUTRO_SERIES_S
        is PlaybackItem.Live -> 0
        is PlaybackItem.RecordingItem -> 0
        is PlaybackItem.DownloadItem -> 0
    }

    /** On start: VOD position below the intro jumps straight past it (+ toast). */
    private fun introAdjustedStart(item: PlaybackItem, resumePositionMs: Long): Long {
        val introMs = introSeconds(item) * 1000L
        if (introMs <= 0 || resumePositionMs >= introMs) return resumePositionMs
        scope.launch { _events.emit(PlayerEvent.Toast("Intro ignorée")) }
        return introMs
    }

    /** During playback: reaching the outro jumps to 99.6% then ends / next episode. */
    private fun checkOutro(positionMs: Long, durationMs: Long) {
        val item = currentItem ?: return
        if (outroHandled || durationMs <= 0 || item is PlaybackItem.Live) return
        val outroMs = outroSeconds(item) * 1000L
        if (positionMs < durationMs - outroMs) return
        outroHandled = true
        val endPosition = (durationMs * 0.996).toLong()
        seekEngines(endPosition)
        scope.launch {
            when (item) {
                is PlaybackItem.Movie -> {
                    stop()
                    _events.emit(PlayerEvent.Toast("Film terminé"))
                }
                is PlaybackItem.EpisodeItem -> advanceToNextEpisode(item)
                else -> Unit
            }
        }
    }

    /** Natural end of a VOD item (STATE_ENDED / VLC EndReached). */
    private fun handleVodEnded() {
        val item = currentItem ?: return
        if (item is PlaybackItem.Live) return
        if (outroHandled) return
        outroHandled = true
        scope.launch {
            when (item) {
                is PlaybackItem.Movie -> {
                    stop()
                    _events.emit(PlayerEvent.Toast("Film terminé"))
                }
                is PlaybackItem.EpisodeItem -> advanceToNextEpisode(item)
                else -> Unit
            }
        }
    }

    private suspend fun advanceToNextEpisode(item: PlaybackItem.EpisodeItem) {
        // L-04: same behaviour as the manual "next" in PlayerViewModel —
        // next episode in the same season, else the first episode of the
        // next season, so auto and manual advance never disagree.
        // L-01: the engine is the SOLE owner of auto-advance (it plays the
        // next episode itself and emits NextEpisode for the screen); the
        // ViewModel no longer advances on position changes.
        val episodes = try {
            contentRepository.getEpisodes(item.series.id, item.episode.season)
        } catch (e: Exception) {
            Log.w(TAG, "getEpisodes failed", e)
            emptyList()
        }
        val next = episodes.filter { it.number > item.episode.number }
            .minByOrNull { it.number }
            ?: run {
                runCatching {
                    contentRepository.getEpisodes(item.series.id, item.episode.season + 1)
                }.getOrNull().orEmpty().minByOrNull { it.number }
            }
        if (next != null) {
            _events.emit(PlayerEvent.NextEpisode(item.series.id, next.season, next.number))
            play(PlaybackItem.EpisodeItem(item.series, next))
        } else {
            stop()
            _events.emit(PlayerEvent.Toast("Pas d'épisode suivant"))
        }
    }

    // ---- Continue-watching / history ------------------------------------------------

    private fun saveResumeFor(item: PlaybackItem?) {
        if (item == null || item is PlaybackItem.Live) return
        val duration = currentDurationMs()
        val position = currentPositionMs()
        if (duration <= 0 || position <= 0) return
        val progress = ((position * 100) / duration).toInt().coerceIn(0, 100)
        val (kind, id) = when (item) {
            is PlaybackItem.Movie -> ContentKind.FILM to item.film.id
            is PlaybackItem.EpisodeItem -> ContentKind.SERIES to item.series.id
            else -> return
        }
        scope.launch {
            try {
                if (progress in 5..95) {
                    val remainingMin = ((duration - position) / 60_000).toInt().coerceAtLeast(0)
                    contentRepository.recordResume(kind, id, progress, "Reste $remainingMin min")
                } else if (progress >= 95) {
                    // >=95% clears the resume entry (data agent treats 100 as done).
                    contentRepository.recordResume(kind, id, 100, "")
                }
            } catch (e: Exception) {
                Log.w(TAG, "recordResume failed", e)
            }
        }
        // §4.1 — per-episode progress (EpisodeResumeStore): the series-level
        // percent above cannot say WHICH episode was playing, so the engine
        // also persists the episode + exact position. >=95% clears it.
        if (item is PlaybackItem.EpisodeItem) {
            val episode = item.episode
            scope.launch {
                runCatching {
                    if (progress in 5..94) {
                        episodeResumeStore.save(item.series.id, episode.season, episode.number, position)
                    } else if (progress >= 95) {
                        episodeResumeStore.clear(item.series.id)
                    }
                }.onFailure { Log.w(TAG, "Episode resume save failed", it) }
            }
        }
    }

    private fun recordHistory(item: PlaybackItem) {
        // Recordings and offline downloads are not history entries (they
        // are the user's own files, listed on their own screens).
        if (item is PlaybackItem.RecordingItem) return
        if (item is PlaybackItem.DownloadItem) return
        val (kind, id, title) = when (item) {
            is PlaybackItem.Live -> Triple(ContentKind.LIVE, item.channel.id, item.channel.name)
            is PlaybackItem.Movie -> Triple(ContentKind.FILM, item.film.id, item.film.title)
            is PlaybackItem.EpisodeItem -> Triple(
                ContentKind.SERIES, item.series.id,
                "${item.series.title} — S${item.episode.season}E${item.episode.number}"
            )
            is PlaybackItem.RecordingItem, is PlaybackItem.DownloadItem -> return
        }
        scope.launch {
            try {
                contentRepository.recordHistory(kind, id, title)
            } catch (e: Exception) {
                Log.w(TAG, "recordHistory failed", e)
            }
        }
    }

    // ---- Position ticker --------------------------------------------------------------

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICKER_INTERVAL_MS)
                val position = currentPositionMs()
                val duration = currentDurationMs()
                if (position >= 0) _positionMs.value = position
                if (duration > 0) _durationMs.value = duration
                checkOutro(position, duration)
                val item = currentItem
                if (item is PlaybackItem.Live) {
                    val atEdge = !_state.value.isTimeshift
                    if (atEdge != _state.value.atLiveEdge) {
                        _state.update { it.copy(atLiveEdge = atEdge) }
                    }
                } else if (!playingTimeshiftFile && !_state.value.atLiveEdge) {
                    // While the temp timeshift file is playing, the player
                    // is deliberately behind the live edge — do not flip
                    // it back (§4.45).
                    _state.update { it.copy(atLiveEdge = true) }
                }
                // B3: persist the last measured bandwidth for the next session.
                val now = System.currentTimeMillis()
                if (lastBandwidthEstimateBps > 0 &&
                    now - lastBitrateSaveMillis >= BITRATE_SAVE_INTERVAL_MS
                ) {
                    lastBitrateSaveMillis = now
                    launch(Dispatchers.IO) {
                        try {
                            bitrateStore.saveMeasuredBitrate(lastBandwidthEstimateBps)
                        } catch (e: Exception) {
                            Log.w(TAG, "saveMeasuredBitrate failed", e)
                        }
                    }
                }
            }
        }
    }

    // ---- Live seeking / timeshift -------------------------------------------------------

    private fun seekLive(targetMs: Long) {
        val edge = liveEdgeMs()
        if (targetMs >= edge - 2000) {
            // Seeking forward to (or past) the edge: back to the live edge.
            seekToLiveEdge()
            return
        }
        // Seeking back from the edge: enter timeshift (A6/B36).
        _state.update { it.copy(isTimeshift = true, atLiveEdge = false) }
        timeshiftAnchorMs = targetMs.coerceAtLeast(0L)
        seekEngines(timeshiftAnchorMs)
        when (activeEngine) {
            Engine.EXOPLAYER -> exo?.play()
            Engine.VLC -> vlcEngine?.resume()
        }
        stallDetector.reset()
    }

    /** Best estimate of the live edge: ExoPlayer's buffered position, else now. */
    private fun liveEdgeMs(): Long = when (activeEngine) {
        Engine.EXOPLAYER -> {
            val player = exo
            if (player == null) currentPositionMs()
            else maxOf(player.bufferedPosition, player.currentPosition)
        }
        // VLC exposes no live-edge position (C13): forward seeks go to the edge.
        Engine.VLC -> currentPositionMs()
    }

    private fun seekEngines(ms: Long) {
        when (activeEngine) {
            Engine.EXOPLAYER -> exo?.seekTo(ms)
            Engine.VLC -> vlcEngine?.seekTo(ms)
        }
    }

    // ---- Helpers --------------------------------------------------------------------------

    private fun currentPositionMs(): Long = when (activeEngine) {
        Engine.EXOPLAYER -> exo?.currentPosition ?: -1L
        Engine.VLC -> vlcEngine?.timeMs ?: -1L
    }

    private fun currentDurationMs(): Long = when (activeEngine) {
        Engine.EXOPLAYER -> exo?.duration?.takeIf { it != C.TIME_UNSET } ?: -1L
        Engine.VLC -> vlcEngine?.lengthMs ?: -1L
    }

    private fun mediaIdOf(item: PlaybackItem): String = when (item) {
        is PlaybackItem.Live -> item.channel.id
        is PlaybackItem.Movie -> item.film.id
        is PlaybackItem.EpisodeItem ->
            "${item.series.id}-s${item.episode.season}e${item.episode.number}"
        is PlaybackItem.RecordingItem -> "rec-${item.recording.id}"
        is PlaybackItem.DownloadItem -> "dl-${item.download.id}"
    }

    /** Catchup plays carry an encoded channel id ("catchup:<id>:..."). */
    private fun isCatchupChannelId(id: String): Boolean = id.startsWith("catchup:")

    /** Host part of a URL only — for logs (never log a full stream URL). */
    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "mirror"

    private fun stopEnginesOnly() {
        // The loudness effect belongs to the ExoPlayer audio session: drop
        // it whenever the engines stop (a fresh one is attached on the
        // next Exo prepare; VLC never gets one).
        releaseLoudnessEnhancer()
        try {
            exo?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "ExoPlayer stop failed", e)
        }
        try {
            vlcEngine?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "VLC stop failed", e)
        }
    }

    private fun stopInternal() {
        stallDetector.stop()
        tickerJob?.cancel()
        tickerJob = null
        releaseLoudnessEnhancer()
        stopEnginesOnly()
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    private fun classifyError(error: PlaybackException): ErrorKind = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> ErrorKind.RENDERER
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> ErrorKind.SOURCE
        else -> ErrorKind.UNEXPECTED
    }

    private fun formatLabel(format: Format): String {
        val bitrate = format.bitrate
        if (bitrate > 0) return "${bitrate / 1000} kbps"
        if (format.width > 0 && format.height > 0) return "${format.height}p"
        return format.label ?: "Auto"
    }

    private fun refreshTrackLabels(tracks: Tracks) {
        val audioGroup = tracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_AUDIO && it.isSelected
        }
        val subGroup = tracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_TEXT && it.isSelected
        }
        val audioLabel = audioGroup?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) }
                ?.let { format -> format.label ?: format.language?.uppercase() ?: "Audio" }
        } ?: _state.value.audioTrackLabel
        val subLabel = subGroup?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                ?.let { group.getTrackFormat(it) }
                ?.let { it.label ?: it.language ?: "Sous-titre" }
        } ?: "Aucun"
        _state.update { it.copy(audioTrackLabel = audioLabel, subtitleLabel = subLabel) }
    }

    /**
     * Track ids are "a{absoluteGroupIndex}t{trackIndex}" / "s{...}t{...}".
     * Absolute group indices (not per-type) so setAudioTrack/setSubtitleTrack
     * can look the group up again in [Player.currentTracks].
     */
    private fun tracksOfType(tracks: Tracks, type: Int, prefix: Char): List<TrackOption> {
        val options = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val label = format.label
                    ?: format.language?.uppercase()
                    ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${options.size + 1}"
                    else "Sous-titre ${options.size + 1}"
                options += TrackOption("$prefix${groupIndex}t$trackIndex", label)
            }
        }
        return options
    }

    private fun parseTrackId(id: String, prefix: Char): Pair<Int, Int>? {
        val regex = Regex("^" + Regex.escape(prefix.toString()) + """(\d+)t(\d+)$""")
        val match = regex.matchEntire(id) ?: return null
        return match.groupValues[1].toIntOrNull()?.let { group ->
            match.groupValues[2].toIntOrNull()?.let { track -> group to track }
        }
    }

    /** Preloads the neighbour channels (B21/D6) so the next zap starts warm. */
    private fun preloadNeighbours(item: PlaybackItem) {
        if (item !is PlaybackItem.Live) return
        scope.launch {
            try {
                val channels = contentRepository.observeChannels().first()
                val index = channels.indexOfFirst { it.id == item.channel.id }
                if (index < 0) return@launch
                val window = (maxOf(0, index - 1)..minOf(channels.lastIndex, index + 1))
                    .map { channels[it] }
                val mediaItems = window.map { channel ->
                    networkStack.buildMediaItem(channel.streamUrl, channel.id, true)
                }
                val currentIndex = window.indexOfFirst { it.id == item.channel.id }
                preloadHelper.preload(mediaItems, currentIndex, currentSettings, false)
            } catch (e: Exception) {
                Log.w(TAG, "Neighbour preload failed (best-effort)", e)
            }
        }
    }

    // ---- Point 2 helpers (2026-11-23) ------------------------------------------

    // -- §4.36.7 failure diagnosis (logic only; ErrorScreen displays it) --

    private fun updateDiagnosis(reason: String, errorCode: Int?) {
        _lastErrorDiagnosis.value = buildDiagnosis(reason, errorCode)
    }

    private fun buildDiagnosis(reason: String, errorCode: Int?): String {
        val r = reason.lowercase(Locale.US)
        val res = when {
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                errorCode == PlaybackException.ERROR_CODE_TIMEOUT ||
                "timeout" in r || "timed out" in r -> R.string.diag_timeout
            errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                "404" in r || "file not found" in r || "no stream url" in r ->
                R.string.diag_dead_link
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                "connection failed" in r || "unable to connect" in r ||
                "network connection failed" in r -> R.string.diag_weak_network
            errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                "bad http status" in r || "http 5" in r -> R.string.diag_server_down
            errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                "behind live window" in r -> R.string.diag_behind_live
            errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
                "renderer" in r || "decoder" in r -> R.string.diag_decoder
            "stall" in r || "stuck" in r -> R.string.diag_stalled
            "encrypted" in r -> R.string.diag_dead_link
            else -> R.string.diag_unknown
        }
        return runCatching { context.getString(res) }.getOrElse {
            "Lecture impossible. Vérifiez votre connexion ou essayez une autre chaîne."
        }
    }

    // -- §4.61 auto Arabic subtitles --

    private fun isArabicTrack(language: String?, label: String?): Boolean {
        if (language != null && language.lowercase(Locale.US).startsWith("ar")) return true
        val l = label ?: return false
        if (l.contains(Regex("[\\u0600-\\u06FF]"))) return true
        val lower = l.lowercase(Locale.US)
        return "arab" in lower || "عربي" in l
    }

    /**
     * Enables the Arabic subtitle track automatically for films/series when
     * one exists (§4.61), unless the user turned the option off
     * (`AppSettings.autoArabicSubs`, default true) or picked a subtitle manually
     * this session. One attempt per play session (tracks load async, so the Exo
     * onTracksChanged path, the VLC TimeChanged path and the delayed VLC
     * attempt all funnel here and the flag stops repeats).
     */
    private fun maybeEnableArabicSubtitles() {
        if (arabicSubsAppliedForSession || userChoseSubtitle) return
        val item = currentItem
        if (item !is PlaybackItem.Movie && item !is PlaybackItem.EpisodeItem) return
        if (!currentSettings.autoArabicSubs) {
            arabicSubsAppliedForSession = true
            return
        }
        if (activeEngine == Engine.EXOPLAYER) {
            val player = exo ?: return
            val tracks = player.currentTracks
            for ((groupIndex, group) in tracks.groups.withIndex()) {
                if (group.type != C.TRACK_TYPE_TEXT) continue
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    if (isArabicTrack(format.language, format.label)) {
                        arabicSubsAppliedForSession = true
                        setSubtitleTrackInternal("s${groupIndex}t$trackIndex")
                        Log.i(TAG, "Auto Arabic subtitles enabled (${format.language ?: format.label})")
                        return
                    }
                }
            }
            // No Arabic track in this (possibly early) tracks snapshot:
            // mark applied so a second onTracksChanged does not loop; a
            // later user action can still pick subtitles manually.
            arabicSubsAppliedForSession = true
        } else {
            val subs = vlcEngine?.subtitleTracks().orEmpty()
            val arabic = subs.firstOrNull { isArabicTrack(null, it.label) }
            arabicSubsAppliedForSession = true
            if (arabic != null) {
                setSubtitleTrackInternal(arabic.id)
                Log.i(TAG, "Auto Arabic subtitles enabled (VLC: ${arabic.label})")
            }
        }
    }

    // -- §4.36 loudness normalisation (ExoPlayer only) --

    /**
     * Enables a [LoudnessEnhancer] on the ExoPlayer audio session so quiet
     * channels are lifted toward a fixed target gain (§4.36 — unify the
     * volume between channels). Read from
     * `AppSettings.volumeNormalization` (default true). Best effort: any failure (no audio session
     * yet, offloaded/passthrough audio where effects do not apply, device
     * without the effect) only logs and leaves playback untouched.
     *
     * VLC: libVLC exposes no loudness API through MediaPlayer, so VLC is
     * NOT normalised here. The VLC-side equivalent is the `--audio-filter=
     * normvol` libVLC option in `createLibVlc` (PlayerFactory.kt — owned
     * by the factory builder, not this file); see the report.
     */
    private fun applyLoudnessEnhancer() {
        releaseLoudnessEnhancer()
        if (!currentSettings.volumeNormalization) return
        if (activeEngine != Engine.EXOPLAYER) return
        val sessionId = exo?.audioSessionId ?: 0
        if (sessionId == 0 || sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(LOUDNESS_TARGET_GAIN_MB)
                enabled = true
            }
            Log.i(TAG, "LoudnessEnhancer enabled (session=$sessionId, target=${LOUDNESS_TARGET_GAIN_MB}mB)")
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable (session=$sessionId)", e)
            loudnessEnhancer = null
        }
    }

    private fun releaseLoudnessEnhancer() {
        val enhancer = loudnessEnhancer ?: return
        loudnessEnhancer = null
        try {
            enhancer.enabled = false
            enhancer.release()
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer release failed", e)
        }
    }

    // -- §4.36.5 predictive stall avoidance --

    /**
     * Called on every bandwidth estimate. When the estimate has fallen 3
     * samples in a row (latest <= 80% of the first of the three) AND the
     * ExoPlayer buffer is below [PREDICTIVE_BUFFER_THRESHOLD_MS], the
     * quality steps down ONE notch BEFORE the buffer runs dry — a stall
     * that never happens needs no recovery. Logcat only, no UI change.
     * Only when the quality is AUTO or was already stepped down by the
     * predictor: a manual user choice is never overridden. At most one
     * step per [PREDICTIVE_COOLDOWN_MS].
     */
    private fun onPredictiveBandwidth(estimateBps: Long) {
        if (activeEngine != Engine.EXOPLAYER) return
        if (currentQualityMode != QualityMode.AUTO && !qualitySetByPredictor) return
        if (currentItem == null) return
        if (estimateBps <= 0) return
        bandwidthSamples.addLast(estimateBps)
        while (bandwidthSamples.size > 4) bandwidthSamples.removeFirst()
        if (bandwidthSamples.size < 3) return
        val s = bandwidthSamples.toList()
        val n = s.size
        val first = s[n - 3]
        val second = s[n - 2]
        val latest = s[n - 1]
        if (!(latest < second && second < first)) return
        if (latest > (first * 0.8).toLong()) return
        val player = exo ?: return
        val bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
        if (bufferedMs >= PREDICTIVE_BUFFER_THRESHOLD_MS) return
        val now = System.currentTimeMillis()
        if (now - lastPredictiveStepDownMs < PREDICTIVE_COOLDOWN_MS) return
        val next = when (currentQualityMode) {
            QualityMode.AUTO -> QualityMode.Q1080
            QualityMode.Q1080 -> QualityMode.Q720
            QualityMode.Q720 -> QualityMode.Q480
            else -> return
        }
        val fromLabel = _state.value.qualityLabel
        lastPredictiveStepDownMs = now
        bandwidthSamples.clear()
        applyQualityMode(next, persist = false)
        qualitySetByPredictor = true
        Log.i(
            TAG,
            "Predictive ABR: bandwidth falling ${first}->${second}->${latest} bps, " +
                "buffer ${bufferedMs}ms — stepping down $fromLabel -> ${_state.value.qualityLabel} before stall"
        )
        qoeLogger.logAbrSwitch(currentItem, activeEngine, fromLabel, _state.value.qualityLabel)
    }

    // -- §4.45 timeshift with on-device storage --

    /** Starts capturing the live stream to the temp timeshift file. */
    private fun beginTimeshiftRecording(item: PlaybackItem.Live) {
        if (timeshiftBuffer.active) return
        if (playingTimeshiftFile) return
        val url = currentUrls.getOrNull(mirrorIndex) ?: item.channel.streamUrl
        timeshiftLiveItem = item
        timeshiftStartedAtMs = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { timeshiftBuffer.start(url) }.getOrDefault(false)
            if (ok) {
                Log.i(TAG, "Timeshift recording started for ${item.channel.id}")
            } else {
                Log.w(TAG, "Timeshift recording could not start; freeze fallback only")
            }
        }
    }

    /**
     * Stops the capture and plays the temp file from its start (= the pause
     * point) as a RecordingItem. When nothing usable was captured
     * (encrypted HLS, single-connection provider, fetch failure) the file
     * is empty and the player returns to the live edge instead.
     */
    private fun finishTimeshiftAndPlayFile() {
        val live = timeshiftLiveItem ?: return
        scope.launch {
            val file = runCatching { timeshiftBuffer.stop() }.getOrNull()
            if (file == null || !file.exists() || file.length() == 0L) {
                Log.w(TAG, "Timeshift buffer empty; returning to live edge")
                timeshiftLiveItem = null
                seekToLiveEdge()
                return@launch
            }
            val recording = Recording(
                id = "timeshift-${live.channel.id}-$timeshiftStartedAtMs",
                channelId = live.channel.id,
                channelName = live.channel.name,
                title = "${live.channel.name} — Timeshift",
                startMillis = timeshiftStartedAtMs,
                durationMs = 0L,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                status = RecordingStatus.DONE
            )
            playingTimeshiftFile = true
            play(PlaybackItem.RecordingItem(recording))
            _state.update { it.copy(isTimeshift = true, atLiveEdge = false) }
        }
    }

    /** Freeze-only fallback: resume inside the DVR window from the anchor. */
    private fun resumeFromTimeshiftAnchor() {
        when (activeEngine) {
            Engine.EXOPLAYER -> {
                exo?.seekTo(timeshiftAnchorMs)
                exo?.play()
            }
            Engine.VLC -> vlcEngine?.resume()
        }
        _state.update { it.copy(isPlaying = true) }
    }
}
