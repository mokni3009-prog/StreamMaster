package com.streammaster.tv.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.PlaybackItem
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.DownloadRepository
import com.streammaster.tv.data.repository.RecordingRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.player.DisplayMode
import com.streammaster.tv.player.EpisodeResumeStore
import com.streammaster.tv.player.OUTRO_FILM_S
import com.streammaster.tv.player.OUTRO_SERIES_S
import com.streammaster.tv.player.PlaybackState
import com.streammaster.tv.player.PlayerEvent
import com.streammaster.tv.player.PlayerManager
import com.streammaster.tv.player.QualityMode
import com.streammaster.tv.player.RecordingManager
import com.streammaster.tv.player.RecordingState
import com.streammaster.tv.player.remote.RemoteBus
import com.streammaster.tv.player.remote.RemoteCommand
import com.streammaster.tv.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Intro/outro seconds are the player agent's constants (PlayerManager.kt,
// demo values from the prototype, SPEC §8).
// TODO(server): intro/outro seconds should come from server metadata.

data class PlayerUiState(
    val kind: String = "live",
    val id: String = "",
    val channel: Channel? = null,
    val film: Film? = null,
    val series: Series? = null,
    val episode: Episode? = null,
    val controlsVisible: Boolean = true,
    val listsOpen: Boolean = false,
    val digitBuffer: String = "",
    val digitOsd: String? = null,
    val pinRequired: Boolean = false,
    val pinError: Boolean = false,
    val toastRes: Int? = null,
    val toastText: String? = null,
    val introSkipped: Boolean = false,
    val filmFinished: Boolean = false,
    val lastRoute: String = Routes.LIVE
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val settingsRepository: SettingsRepository,
    private val recordingRepository: RecordingRepository,
    private val downloadRepository: DownloadRepository,
    private val recordingManager: RecordingManager,
    private val remoteBus: RemoteBus,
    private val episodeResumeStore: EpisodeResumeStore,
    @ApplicationContext private val appContext: Context,
    val playerManager: PlayerManager
) : ViewModel() {
    val playbackState: StateFlow<PlaybackState> = playerManager.state
    val positionMs: StateFlow<Long> = playerManager.positionMs
    val durationMs: StateFlow<Long> = playerManager.durationMs
    /** Failure diagnosis (§4.36.7): plain text produced by the engine,
     *  shown by ErrorScreen (other builder). "" when no failure. */
    val lastErrorDiagnosis: StateFlow<String> = playerManager.lastErrorDiagnosis
    /** Instant DVR state (REC button + badge); Idle when nothing records. */
    val recordingState: StateFlow<RecordingState> = recordingManager.state
    /** Video frame rate reported by the engine (0 = unknown). */
    val videoFrameRate: StateFlow<Float> = playerManager.videoFrameRate
    /** Frame-rate switching setting (2026-11-22). */
    val frameRateSwitch: StateFlow<Boolean> = settingsRepository.settings
        .map { it.frameRateSwitch }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    /** Smart failover toggle (2026-11-22): opt-in, off by default. */
    val smartFailover: StateFlow<Boolean> = settingsRepository.settings
        .map { it.smartFailover }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** Match Center toggle (2026-11-22): opt-in, off by default. */
    val matchCenter: StateFlow<Boolean> = settingsRepository.settings
        .map { it.matchCenter }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    /** Phone remote commands (2026-11-22): emitted by the embedded HTTP
     *  server, collected by PlayerScreen. */
    val remoteCommands: SharedFlow<RemoteCommand> = remoteBus.commands
    /** Playback speeds offered in the player (2026-11-22). */
    val speedOptions: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    /** EPG (now + next 6h) for the current live channel (PL05/PL06/PL09):
     *  the screen derives the now/next program from this list with the
     *  wall clock, so it stays correct as time passes. Empty for VOD. */
    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgram>> = _epgPrograms.asStateFlow()
    /** All live channels (bouquet counters in the player lists, PL14). */
    val allChannels: StateFlow<List<Channel>> = contentRepository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var epgJob: Job? = null

    /** (Re)starts EPG collection for one live channel. */
    private fun observeEpgFor(channelId: String) {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            contentRepository.observeEpg(channelId, now, now + 6 * 60 * 60 * 1000L)
                .collect { _epgPrograms.value = it }
        }
    }
    private var hideJob: Job? = null
    private var digitJob: Job? = null
    private var previousChannelId: String? = null

    init {
        // Collect the engine's one-shot events (PlayerManager.events):
        // the engine emits Toast("Intro ignorée") / Toast("Film terminé")
        // and NextEpisode, but nothing collected them before, so engine
        // toasts never reached the UI. Intro / film-end map onto the
        // existing ui flags so the toast shows exactly once (the ViewModel's
        // own skip/finish paths set the same flags); anything else (sleep
        // fired, "Pas d'épisode suivant") becomes a raw-text toast.
        // NextEpisode only updates the screen's episode state — the engine
        // already plays the next episode itself.
        viewModelScope.launch {
            playerManager.events.collect { event ->
                when (event) {
                    is PlayerEvent.Toast -> when (event.message) {
                        "Intro ignorée" -> _ui.update { it.copy(introSkipped = true) }
                        "Film terminé" -> _ui.update { it.copy(filmFinished = true) }
                        else -> _ui.update { it.copy(toastText = event.message) }
                    }
                    is PlayerEvent.NextEpisode -> {
                        val episodes = contentRepository.getEpisodes(event.seriesId, event.season)
                        val episode = episodes.firstOrNull { it.number == event.episode }
                        if (episode != null) _ui.update { it.copy(episode = episode) }
                    }
                }
            }
        }
    }

    /** Back returns to the matching view standing on the same item (CONTRACT §6). */
    fun backRouteFor(kind: String): String = when (kind) {
        "live" -> Routes.LIVE
        "catchup" -> Routes.LIVE
        "film" -> Routes.FILMS
        "series" -> Routes.SERIES
        "recording" -> Routes.RECORDINGS
        "download" -> Routes.DOWNLOADS
        else -> Routes.HOME
    }

    fun load(kind: String, id: String) = viewModelScope.launch {
        _ui.update { it.copy(kind = kind, id = id, lastRoute = backRouteFor(kind)) }
        epgJob?.cancel()
        _epgPrograms.value = emptyList()
        when (kind) {
            "live" -> {
                val channel = contentRepository.getChannel(id) ?: return@launch
                _ui.update { it.copy(channel = channel) }
                if (settingsRepository.isLocked(ContentKind.LIVE, id)) {
                    _ui.update { it.copy(pinRequired = true) }
                } else playLive(channel)
            }
            "catchup" -> {
                // §4.43 — encoded id "catchup:<channelId>:<startMillis>:<durationMin>"
                // (built by EpgScreen's "Revoir"). The real channel supplies
                // the metadata (name, EPG, PIN check, history); the timeshift
                // URL comes from ContentRepository.getStreamUrls(id).
                val realId = id.removePrefix("catchup:").split(":").getOrNull(0)
                    ?.takeIf { it.isNotBlank() } ?: return@launch
                val channel = contentRepository.getChannel(realId) ?: return@launch
                _ui.update { it.copy(channel = channel) }
                if (settingsRepository.isLocked(ContentKind.LIVE, realId)) {
                    _ui.update { it.copy(pinRequired = true) }
                } else {
                    val catchupUrl = contentRepository.getStreamUrls(id).firstOrNull()
                    if (catchupUrl == null) {
                        _ui.update { it.copy(toastText = "Revoir indisponible pour cette chaîne") }
                    } else {
                        playCatchup(channel, id, catchupUrl)
                    }
                }
            }
            "film" -> {
                val film = contentRepository.getFilm(id) ?: return@launch
                _ui.update { it.copy(film = film) }
                if (settingsRepository.isLocked(ContentKind.FILM, id)) {
                    _ui.update { it.copy(pinRequired = true) }
                } else playFilm(film)
            }
            "series" -> {
                // DetailScreen passes "seriesId:S01E07" when the user taps
                // one episode (code = "S{season:02}E{number:02}", built in
                // XtreamApi; "E{number}" only when the code is blank), and
                // the plain series id for «Reprendre». Split on the first
                // ":" — a bare id keeps the old resume behaviour.
                val parts = id.split(":", limit = 2)
                val seriesId = parts[0]
                val episodeRef = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val series = contentRepository.getSeries(seriesId) ?: return@launch
                val episode = if (episodeRef != null) {
                    resolveTappedEpisode(series, episodeRef)
                } else {
                    // §4.1 — resume the episode the user actually stopped on
                    // (EpisodeResumeStore), not always S1E1; fall back to S1E1
                    // when the saved episode is gone.
                    val resume = runCatching { episodeResumeStore.get(seriesId) }.getOrNull()
                    resume?.let { r ->
                        contentRepository.getEpisodes(seriesId, r.season)
                            .firstOrNull { it.number == r.number }
                    } ?: contentRepository.getEpisodes(seriesId, 1).firstOrNull()
                }
                _ui.update { it.copy(series = series, episode = episode) }
                if (settingsRepository.isLocked(ContentKind.SERIES, seriesId)) {
                    _ui.update { it.copy(pinRequired = true) }
                } else if (episode != null) playEpisode(series, episode)
            }
            "recording" -> {
                // A recording is the user's own file: no PIN, played as VOD.
                val recording = recordingRepository.getRecording(id) ?: return@launch
                playerManager.play(PlaybackItem.RecordingItem(recording))
            }
            "download" -> {
                // An offline VOD file: no PIN, played as VOD.
                val download = downloadRepository.getDownload(id) ?: return@launch
                playerManager.play(PlaybackItem.DownloadItem(download))
            }
        }
        armControlsAutoHide()
    }

    private fun playLive(channel: Channel) {
        // L-03: _ui.channel is already the NEW channel by the time playLive
        // is called (playChannel/checkPin update _ui first), so the old
        // "previous" computation here always saw the new id. The previous
        // id is now captured in playChannel() before the _ui update.
        playerManager.play(PlaybackItem.Live(channel))
        observeEpgFor(channel.id)
        viewModelScope.launch { contentRepository.recordHistory(ContentKind.LIVE, channel.id, channel.name) }
    }

    /**
     * Catchup playback (§4.43): the engine resolves a Live item's URLs via
     * `contentRepository.getStreamUrls(channel.id)`, and that function only
     * returns the Xtream timeshift URL for the ENCODED id
     * ("catchup:<channelId>:<start>:<duration>") — so the played item
     * carries a copy of the real channel whose id IS the encoded id (and
     * whose streamUrl is the timeshift URL as fallback). `_ui.channel`
     * stays the real channel, and EPG/history use the real id, so the
     * screen, the programme panel and the history all show the channel —
     * only the played stream is the past program.
     */
    private fun playCatchup(channel: Channel, encodedId: String, catchupUrl: String) {
        playerManager.play(
            PlaybackItem.Live(channel.copy(id = encodedId, streamUrl = catchupUrl))
        )
        observeEpgFor(channel.id)
        viewModelScope.launch { contentRepository.recordHistory(ContentKind.LIVE, channel.id, channel.name) }
    }

    private fun playFilm(film: Film) {
        val resumeMs = if (film.recentProgress in 1..94) {
            film.durationMin * 60_000L * film.recentProgress / 100
        } else 0L
        playerManager.play(PlaybackItem.Movie(film), resumeMs)
        viewModelScope.launch { contentRepository.recordHistory(ContentKind.FILM, film.id, film.title) }
    }

    private fun playEpisode(series: Series, episode: Episode) {
        viewModelScope.launch {
            val resumeMs = episodeResumeMs(series, episode)
            playerManager.play(PlaybackItem.EpisodeItem(series, episode), resumeMs)
            contentRepository.recordHistory(ContentKind.SERIES, series.id, series.title)
        }
    }

    /**
     * Resolves the episode DetailScreen encoded in the id ("S01E07", or
     * "E7" when the episode code is blank). "SxxEyy" gives the season
     * directly; a bare "Eyy" has no season, so seasons are scanned in
     * order (bounded by the series' season count). Returns null when the
     * reference matches nothing — the caller then shows nothing rather
     * than playing the wrong episode.
     */
    private suspend fun resolveTappedEpisode(series: Series, ref: String): Episode? {
        val seasonEpisode = Regex("""^S(\d+)E(\d+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(ref.trim())
        if (seasonEpisode != null) {
            val season = seasonEpisode.groupValues[1].toIntOrNull() ?: return null
            val number = seasonEpisode.groupValues[2].toIntOrNull() ?: return null
            val episodes = runCatching {
                contentRepository.getEpisodes(series.id, season)
            }.getOrNull().orEmpty()
            return episodes.firstOrNull { it.code.equals(ref.trim(), ignoreCase = true) }
                ?: episodes.firstOrNull { it.number == number }
        }
        val episodeOnly = Regex("""^E(\d+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(ref.trim()) ?: return null
        val number = episodeOnly.groupValues[1].toIntOrNull() ?: return null
        for (season in 1..series.seasons.coerceAtLeast(1)) {
            val episodes = runCatching {
                contentRepository.getEpisodes(series.id, season)
            }.getOrNull().orEmpty()
            episodes.firstOrNull { it.number == number }?.let { return it }
        }
        return null
    }

    /**
     * Resume position for one episode (§4.1 / §4.40): the per-episode
     * position saved by the engine in [EpisodeResumeStore], 5–94% of the
     * episode duration only. The series-level `Series.recentProgress`
     * percent is deliberately NOT used here: it cannot say which episode
     * was playing, so applying it to a different episode would resume in
     * the wrong place.
     */
    private suspend fun episodeResumeMs(series: Series, episode: Episode): Long {
        val stored = runCatching { episodeResumeStore.get(series.id) }.getOrNull()
            ?: return 0L
        if (stored.season != episode.season || stored.number != episode.number) return 0L
        val durationMs = episode.durationMin * 60_000L
        if (durationMs <= 0) return 0L
        val progress = (stored.positionMs * 100 / durationMs).toInt()
        return if (progress in 5..94) stored.positionMs else 0L
    }

    /** Outro skip is driven by the screen observing position/duration.
     *  L-01: the ENGINE owns series auto-advance (checkOutro /
     *  handleVodEnded → advanceToNextEpisode + NextEpisode event) — the
     *  ViewModel must not advance too, or the race skips/replays an
     *  episode. Only the film branch stays here (it only mirrors the
     *  engine's own film-end handling and sets the filmFinished flag). */
    fun onPositionAdvanced(positionMs: Long, durationMs: Long) {
        val kind = _ui.value.kind
        val outroS = when (kind) { "film" -> OUTRO_FILM_S.toLong(); "series" -> OUTRO_SERIES_S.toLong(); else -> return }
        if (durationMs > 0 && positionMs >= durationMs - outroS * 1000L) {
            when (kind) {
                "film" -> {
                    playerManager.stop()
                    _ui.update { it.copy(filmFinished = true) }
                }
                "series" -> Unit // Engine advances (see above); do NOT call playNextEpisode here.
            }
        }
    }

    /** Manual "next episode" (also kept for the screen's next button).
     *  L-01 guard: if the engine already auto-advanced (its NextEpisode
     *  event is what updates _ui.episode), just sync the screen to the
     *  episode actually playing instead of advancing a second time. */
    fun playNextEpisode() = viewModelScope.launch {
        val series = _ui.value.series ?: return@launch
        val current = _ui.value.episode ?: return@launch
        val playing = playerManager.state.value.item as? PlaybackItem.EpisodeItem
        if (playing != null && playing.series.id == series.id &&
            (playing.episode.season != current.season || playing.episode.number != current.number)
        ) {
            _ui.update { it.copy(episode = playing.episode) }
            return@launch
        }
        val episodes = contentRepository.getEpisodes(series.id, current.season)
        val next = episodes.firstOrNull { it.number == current.number + 1 }
            ?: contentRepository.getEpisodes(series.id, current.season + 1).firstOrNull()
        if (next != null) {
            _ui.update { it.copy(episode = next) }
            playEpisode(series, next)
        } else {
            playerManager.stop()
            _ui.update { it.copy(toastText = null, toastRes = com.streammaster.tv.R.string.film_finished) }
        }
    }

    fun playChannel(channel: Channel) {
        // L-03: capture the previous channel BEFORE _ui switches to the new
        // one — playLive() can no longer see the old id.
        _ui.value.channel?.id?.takeIf { it != channel.id }?.let { previousChannelId = it }
        _ui.update { it.copy(channel = channel, kind = "live", id = channel.id) }
        playLive(channel)
    }

    fun checkPin(pin: String) = viewModelScope.launch {
        val expected = settingsRepository.settings.let {
            it.first().pin
        }
        if (pin == expected && expected.isNotEmpty()) {
            _ui.update { it.copy(pinRequired = false, pinError = false) }
            val s = _ui.value
            when (s.kind) {
                "live" -> s.channel?.let { playLive(it) }
                "catchup" -> s.channel?.let { channel ->
                    val catchupUrl = contentRepository.getStreamUrls(s.id).firstOrNull()
                    if (catchupUrl != null) playCatchup(channel, s.id, catchupUrl)
                }
                "film" -> s.film?.let { playFilm(it) }
                "series" -> s.series?.let { sr -> s.episode?.let { ep -> playEpisode(sr, ep) } }
            }
        } else {
            _ui.update { it.copy(pinError = true) }
        }
    }

    fun dismissPin() { _ui.update { it.copy(pinRequired = false) } }

    // --- Controls auto-hide (4s) ---
    fun armControlsAutoHide() {
        hideJob?.cancel()
        _ui.update { it.copy(controlsVisible = true) }
        hideJob = viewModelScope.launch {
            delay(4000)
            _ui.update { it.copy(controlsVisible = false) }
        }
    }

    fun showControls() = armControlsAutoHide()

    fun toggleLists() = _ui.update { it.copy(listsOpen = !it.listsOpen) }
    fun openLists() = _ui.update { it.copy(listsOpen = true) }
    fun closeLists() = _ui.update { it.copy(listsOpen = false) }

    /** OK on a channel while lists are open: play it, keep lists open; a second
     *  OK on the SAME (now playing) channel closes them. */
    fun onChannelChosenFromLists(channel: Channel): Boolean {
        val playingId = (playerManager.state.value.item as? PlaybackItem.Live)?.channel?.id
        return if (playingId == channel.id) {
            _ui.update { it.copy(listsOpen = false) }
            true
        } else {
            playChannel(channel)
            false
        }
    }

    /** Phone remote (2026-11-22): zap to the next / previous channel in
     *  the current bouquet order. */
    fun zapChannel(delta: Int) = viewModelScope.launch {
        val channels = contentRepository.observeChannels().first()
        if (channels.isEmpty()) return@launch
        val currentId = _ui.value.channel?.id ?: return@launch
        val idx = channels.indexOfFirst { it.id == currentId }
        if (idx < 0) return@launch
        playChannel(channels[(idx + delta).mod(channels.size)])
    }

    fun togglePlayPause() {
        if (playerManager.state.value.isPlaying) playerManager.pause() else playerManager.resume()
        armControlsAutoHide()
    }

    /** REC button (live only): start an instant recording of the current
     *  channel, or stop the running one. */
    fun toggleRecording() {
        if (recordingState.value is RecordingState.Active) {
            recordingManager.stop()
        } else if (_ui.value.kind == "live") {
            val channel = _ui.value.channel
            if (channel != null && !recordingManager.start(channel)) {
                _ui.update { it.copy(toastRes = com.streammaster.tv.R.string.recording_already) }
            }
        }
        armControlsAutoHide()
    }

    /** Smart failover toggle (2026-11-22): persisted in DataStore, stays
     *  on until the user turns it off from the same player button. */
    fun toggleSmartFailover() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(smartFailover = !it.smartFailover) }
        }
        armControlsAutoHide()
    }

    /** Match Center toggle (2026-11-22): persisted in DataStore, stays
     *  on until the user turns it off from the same player button. */
    fun toggleMatchCenter() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(matchCenter = !it.matchCenter) }
        }
        armControlsAutoHide()
    }

    fun skip(deltaMs: Long) { playerManager.skip(deltaMs); armControlsAutoHide() }
    fun goLiveEdge() { playerManager.seekToLiveEdge(); armControlsAutoHide() }
    fun toggleTimeshift() { playerManager.toggleTimeshift(); armControlsAutoHide() }
    fun setQuality(mode: QualityMode) { playerManager.setQuality(mode); armControlsAutoHide() }
    fun setAudioTrack(id: String?) { playerManager.setAudioTrack(id); armControlsAutoHide() }
    fun setSubtitleTrack(id: String?) { playerManager.setSubtitleTrack(id); armControlsAutoHide() }
    fun setDisplayMode(mode: DisplayMode) { playerManager.setDisplayMode(mode); armControlsAutoHide() }
    fun setSpeed(speed: Float) { playerManager.setPlaybackSpeed(speed); armControlsAutoHide() }

    /** Opens the current stream in an external player (VLC if set in
     *  Settings, else the system chooser) — 2026-11-22. */
    fun playExternally() = viewModelScope.launch {
        val s = _ui.value
        val url = when (s.kind) {
            "live" -> s.channel?.streamUrl
            "catchup" -> contentRepository.getStreamUrls(s.id).firstOrNull()
            "film" -> s.film?.streamUrl
            "series" -> s.episode?.streamUrl
            else -> null
        } ?: return@launch
        val settings = settingsRepository.settings.first()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pkg = settings.externalPlayer
            if (pkg.isNotBlank() && isPackageInstalled(pkg)) setPackage(pkg)
        }
        val ok = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        if (!ok) _ui.update { it.copy(toastRes = com.streammaster.tv.R.string.external_player_failed) }
    }

    private fun isPackageInstalled(pkg: String): Boolean =
        runCatching { appContext.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    fun enterPip() { playerManager.enterPip() }
    fun exitPip() { playerManager.exitPip() }
    fun toggleFavorite() = viewModelScope.launch {
        val s = _ui.value
        when (s.kind) {
            "live" -> s.channel?.let { contentRepository.setFavorite(ContentKind.LIVE, it.id, !it.isFavorite) }
            "film" -> s.film?.let { contentRepository.setFavorite(ContentKind.FILM, it.id, !it.isFavorite) }
            "series" -> s.series?.let { contentRepository.setFavorite(ContentKind.SERIES, it.id, !it.isFavorite) }
        }
    }

    /** Previous channel; pressing twice returns to the first channel. */
    fun previousChannel() = viewModelScope.launch {
        val channels = contentRepository.observeChannels().first()
        val currentId = _ui.value.channel?.id ?: return@launch
        val idx = channels.indexOfFirst { it.id == currentId }
        val target = when {
            previousChannelId != null -> channels.firstOrNull { it.id == previousChannelId }
            idx > 0 -> channels[idx - 1]
            else -> channels.firstOrNull()
        } ?: return@launch
        previousChannelId = currentId
        playChannel(target)
    }

    /** Digit entry: up to 2 digits, OSD "Chaîne N", resolves after 1.5s. */
    fun onDigit(digit: Char) {
        val buffer = (_ui.value.digitBuffer + digit).take(2)
        _ui.update { it.copy(digitBuffer = buffer, digitOsd = buffer) }
        digitJob?.cancel()
        digitJob = viewModelScope.launch {
            delay(1500)
            val channels = contentRepository.observeChannels().first()
            val n = buffer.toIntOrNull()
            val found = channels.firstOrNull { it.customNum == n }
                ?: channels.firstOrNull { it.num.trimStart('0').toIntOrNull() == n }
            if (found != null) playChannel(found)
            else _ui.update { it.copy(toastRes = com.streammaster.tv.R.string.channel_not_found) }
            _ui.update { it.copy(digitBuffer = "", digitOsd = null) }
        }
    }

    fun clearToast() = _ui.update { it.copy(toastRes = null, toastText = null) }
    fun clearIntroFlag() = _ui.update { it.copy(introSkipped = false) }
    fun clearFilmFinished() = _ui.update { it.copy(filmFinished = false) }

    fun stop() { playerManager.stop() }
}
