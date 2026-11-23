package com.streammaster.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.PlaybackItem
import com.streammaster.tv.player.DisplayMode
import com.streammaster.tv.player.Engine
import com.streammaster.tv.player.QualityMode
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.PinDialog
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.player.remote.RemoteCommand
import com.streammaster.tv.ui.viewmodel.LiveViewModel
import com.streammaster.tv.ui.viewmodel.MatchCenterViewModel
import com.streammaster.tv.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * PlayerScreen — fullscreen (CONTRACT §7), laid out like the model
 * (stream-master.html, .fs-player): top header (logo + channel + badges +
 * clock), big center play button, program labels + progress bar + next
 * line, three control groups, slide-up bouquet/channel lists, digit OSD,
 * loading screen, watermark and an error dialog over the player.
 * Controls auto-hide after 4s; OK while hidden opens two slide-up lists
 * (bouquets -> channels); one OK plays keeping the lists open, a second OK
 * on the SAME playing channel closes them; Back closes the lists first.
 * Back (with lists closed) returns to the matching view standing on the
 * same item (live->Live, film->Films, series->Series).
 */
@Composable
fun PlayerScreen(
    navController: NavHostController,
    kind: String,
    id: String,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    // Initial focus: the play/pause control, (re-)requested whenever the
    // controls overlay appears, so the D-pad moves from the first press.
    val firstControlFocus = remember { FocusRequester() }
    LaunchedEffect(ui.controlsVisible) {
        if (ui.controlsVisible) firstControlFocus.requestFocus()
    }
    // Lists focus: when the slide-up bouquet/channel lists open, focus
    // moves into the first bouquet; when they close, focus returns to the
    // play/pause control (when the controls are visible).
    val listsFirstFocus = remember { FocusRequester() }
    LaunchedEffect(ui.listsOpen) {
        if (ui.listsOpen) {
            listsFirstFocus.requestFocus()
        } else if (ui.controlsVisible) {
            firstControlFocus.requestFocus()
        }
    }
    // PiP focus: when the PiP window appears, focus moves to its
    // Agrandir button — otherwise the PiP bar is unreachable by D-pad.
    // Leaving PiP returns focus to the controls when they are visible;
    // otherwise the next arrow press shows them (key handler) and the
    // controls effect requests focus then.
    val pipFocus = remember { FocusRequester() }
    LaunchedEffect(playback.isPip) {
        if (playback.isPip) pipFocus.requestFocus()
        else if (ui.controlsVisible) firstControlFocus.requestFocus()
    }
    val position by viewModel.positionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    val epgPrograms by viewModel.epgPrograms.collectAsState()
    val allChannels by viewModel.allChannels.collectAsState()
    val context = LocalContext.current
    var selector by remember { mutableStateOf<String?>(null) } // audio | subs | quality | display
    var scoresOpen by remember { mutableStateOf(false) } // Match Center dialog (live only)
    // Match Center toggle (2026-11-22): opt-in, persists until the user
    // turns it off from the same Scores button in the player.
    val matchCenterOn by viewModel.matchCenter.collectAsState()
    LaunchedEffect(matchCenterOn) { if (!matchCenterOn) scoresOpen = false }
    var clock by remember { mutableStateOf("") }
    var listBouquetId by remember { mutableStateOf<String?>(null) }
    // Programme panel (PL09) and volume OSD (PL08) state.
    var epgPanelOpen by remember { mutableStateOf(false) }
    var volumeOsd by remember { mutableStateOf(false) }
    // Scores dialog focus: the Refresh button.
    val scoresFocus = remember { FocusRequester() }
    LaunchedEffect(scoresOpen) {
        if (scoresOpen) scoresFocus.requestFocus()
    }
    // Programme panel focus: the Fermer button (its only focusable).
    val programmeFocus = remember { FocusRequester() }
    LaunchedEffect(epgPanelOpen) {
        if (epgPanelOpen) programmeFocus.requestFocus()
    }
    var volumeLevel by remember { mutableStateOf(0) }
    var volumeMax by remember { mutableStateOf(15) }

    LaunchedEffect(kind, id) { viewModel.load(kind, id) }
    LaunchedEffect(position, duration) { viewModel.onPositionAdvanced(position, duration) }
    fun exitPlayer() {
        viewModel.stop()
        navController.previousBackStackEntry?.savedStateHandle?.set("focusedId", id)
        navController.popBackStack()
    }

    // EPG now/next for the live channel, re-derived every second from the
    // clock ticker (PL05/PL06).
    val nowMs = remember(clock) { System.currentTimeMillis() }
    val nowProgram = remember(epgPrograms, nowMs) {
        epgPrograms.firstOrNull { nowMs in it.startMillis until it.endMillis }
    }
    val nextProgram = remember(epgPrograms, nowMs, nowProgram) {
        val after = nowProgram?.endMillis ?: nowMs
        epgPrograms.filter { it.startMillis >= after }.minByOrNull { it.startMillis }
    }
    // REC elapsed time, live (recomposed by the clock ticker).
    val rec = recState as? com.streammaster.tv.player.RecordingState.Active
    val recElapsed: String? = rec?.let {
        val elapsedS = ((System.currentTimeMillis() - it.startedAtMillis) / 1000).toInt()
        "%02d:%02d".format(elapsedS / 60, elapsedS % 60)
    }

    fun audioManager(): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun openVolume() {
        val am = audioManager()
        volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeLevel = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeOsd = true
        viewModel.showControls()
    }

    fun adjustVolume(delta: Int) {
        val am = audioManager()
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
        volumeLevel = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        viewModel.showControls()
    }

    LaunchedEffect(volumeOsd) {
        if (volumeOsd) {
            delay(3000)
            volumeOsd = false
        }
    }

    // Phone web remote (2026-11-22): commands from the phone page.
    LaunchedEffect(Unit) {
        viewModel.remoteCommands.collect { cmd ->
            when (cmd) {
                RemoteCommand.TogglePlayPause -> viewModel.togglePlayPause()
                RemoteCommand.ChannelUp -> viewModel.zapChannel(1)
                RemoteCommand.ChannelDown -> viewModel.zapChannel(-1)
                RemoteCommand.GoLive -> viewModel.goLiveEdge()
                RemoteCommand.ToggleRecording -> viewModel.toggleRecording()
                RemoteCommand.OpenLists -> viewModel.openLists()
                is RemoteCommand.Digit -> viewModel.onDigit(cmd.digit)
                RemoteCommand.Rewind -> viewModel.skip(-10_000)
                RemoteCommand.Forward -> viewModel.skip(10_000)
                RemoteCommand.StopPlayback -> exitPlayer()
                RemoteCommand.PrevChannel -> viewModel.previousChannel()
                RemoteCommand.ShowInfo -> viewModel.showControls()
                RemoteCommand.OpenAudio -> selector = "audio"
                RemoteCommand.OpenSubtitles -> selector = "subs"
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) { clock = fmt.format(Date()); delay(1000) }
    }
    // Frame-rate switching (2026-11-22): match the TV refresh rate to the
    // video frame rate while playing; restore the default on exit.
    val frameRate by viewModel.videoFrameRate.collectAsState()
    val frameRateSwitch by viewModel.frameRateSwitch.collectAsState()
    DisposableEffect(frameRate, frameRateSwitch) {
        val activity = context as? Activity
        if (activity != null && frameRateSwitch && frameRate > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            val mode = display?.supportedModes?.minByOrNull { abs(it.refreshRate - frameRate) }
            if (mode != null && mode.modeId != 0) {
                val attrs = activity.window.attributes
                attrs.preferredDisplayModeId = mode.modeId
                activity.window.attributes = attrs
            }
        }
        onDispose {
            val activity = context as? Activity
            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val attrs = activity.window.attributes
                attrs.preferredDisplayModeId = 0
                activity.window.attributes = attrs
            }
        }
    }
    LaunchedEffect(ui.toastRes) {
        ui.toastRes?.let {
            showToast(context, context.getString(it))
            viewModel.clearToast()
        }
    }
    LaunchedEffect(ui.toastText) {
        ui.toastText?.let {
            showToast(context, it)
            viewModel.clearToast()
        }
    }
    LaunchedEffect(ui.pinError) {
        if (ui.pinError) {
            showToast(context, context.getString(R.string.pin_wrong))
        }
    }
    LaunchedEffect(ui.introSkipped) {
        if (ui.introSkipped) {
            showToast(context, context.getString(R.string.intro_skipped))
            viewModel.clearIntroFlag()
        }
    }
    LaunchedEffect(ui.filmFinished) {
        if (ui.filmFinished) {
            showToast(context, context.getString(R.string.film_finished))
            viewModel.clearFilmFinished()
            navController.popBackStack()
        }
    }
    LaunchedEffect(playback.showError) {
        if (playback.showError) viewModel.armControlsAutoHide()
    }

    fun onBackPressed(): Boolean {
        return when {
            epgPanelOpen -> { epgPanelOpen = false; true }
            volumeOsd -> { volumeOsd = false; true }
            ui.listsOpen -> { viewModel.closeLists(); true }
            else -> { exitPlayer(); true }
        }
    }

    ThemedScreen {
        Box(
            modifier = Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> onBackPressed()
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!ui.controlsVisible && !ui.listsOpen) {
                                viewModel.openLists(); true
                            } else false
                        }
                        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2,
                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
                        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
                        KeyEvent.KEYCODE_9 -> {
                            val digit = ('0' + (event.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_0))
                            viewModel.onDigit(digit)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!ui.controlsVisible) { viewModel.showControls(); true } else false
                        }
                        else -> false
                    }
                }
                // The video surface itself must never take focus: it is a
                // full-screen clickable, so without this the D-pad can land
                // on it invisibly. Tap still shows the controls.
                .focusProperties { canFocus = false }
                .clickable { viewModel.showControls() }
        ) {
            if (playback.isPip) {
                // PiP (PL12): 480x270 window bottom-right; the bar sits at
                // its bottom with the program title + Agrandir/Fermer.
                val pipTitle = nowProgram?.title ?: when (val item = playback.item) {
                    is PlaybackItem.Live -> item.channel.customName ?: item.channel.name
                    is PlaybackItem.Movie -> item.film.title
                    is PlaybackItem.EpisodeItem -> "${item.series.title} — ${item.episode.title}"
                    is PlaybackItem.RecordingItem -> item.recording.title
                    is PlaybackItem.DownloadItem -> item.download.title
                    null -> ""
                }
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
                    Box(
                        modifier = Modifier.size(480.dp, 270.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                            .border(2.dp, BorderStrong, RoundedCornerShape(18.dp))
                    ) {
                        VideoSurface(viewModel)
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = pipTitle,
                                modifier = Modifier.weight(1f),
                                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            PipBarButton(text = stringResource(R.string.enlarge), modifier = Modifier.focusRequester(pipFocus)) { viewModel.exitPip() }
                            PipBarButton(text = stringResource(R.string.close)) { exitPlayer() }
                        }
                    }
                }
            } else {
                VideoSurface(viewModel, Modifier.fillMaxSize())
            }

            // Watermark (PL01): visible with the controls UI.
            if (ui.controlsVisible && !playback.isPip) {
                Text(
                    text = "STREAM MASTER",
                    fontSize = 24.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.16f),
                    letterSpacing = 8.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 46.dp, bottom = 150.dp)
                )
            }

            // Loading screen (PL02): gold spinner + secure-connection text
            // + sliding bar while buffering, instead of the small
            // "Buffering" text badge. Controls paint above it.
            if (playback.isBuffering && !playback.showError && !playback.isPip) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xF5140E04)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Gold, strokeWidth = 6.dp,
                            modifier = Modifier.size(74.dp)
                        )
                        Text(
                            text = stringResource(R.string.player_loading),
                            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                            color = GoldWhite, letterSpacing = 1.sp
                        )
                        PlayerLoadingBar()
                    }
                }
            }

            // Digit-entry OSD (PL15): dark box, strong 2px border, 16dp
            // radius, 24sp — top center.
            ui.digitOsd?.let { osd ->
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE0080501))
                        .border(2.dp, BorderStrong, RoundedCornerShape(16.dp))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.channel_osd, osd),
                        fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldWhite
                    )
                }
            }

            // Error dialog over the player (PL18 + K09).
            if (playback.showError) {
                ErrorScreen(
                    message = playback.errorMessage,
                    onRetry = { playback.item?.let { viewModel.playerManager.play(it) } },
                    onBack = { exitPlayer() }
                )
            }

            // Status badges (PL16): top-right while the controls are
            // hidden (when visible they live in the header instead).
            if (!ui.controlsVisible && !playback.isPip) {
                StatusPills(
                    recElapsed = recElapsed,
                    isTimeshift = playback.isTimeshift,
                    sleepRemainingMin = playback.sleepRemainingMin,
                    usingBackup = playback.usingBackup,
                    networkSpeedLabel = playback.networkSpeedLabel,
                    modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                )
            }

            // Slide-up lists (bouquets -> channels, PL13/PL14).
            if (ui.listsOpen) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color(0x80020100))
                        // Same as the video surface: the scrim is a
                        // full-screen clickable and must never take focus
                        // (tap still closes the lists).
                        .focusProperties { canFocus = false }
                        .clickable { viewModel.closeLists() }
                ) {
                    PlayerListsOverlay(
                        viewModel = viewModel,
                        allChannels = allChannels,
                        selectedBouquetId = listBouquetId,
                        onBouquet = { listBouquetId = it },
                        onChannel = { channel -> viewModel.onChannelChosenFromLists(channel) },
                        firstFocus = listsFirstFocus,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Controls overlay (PL03-PL07, PL17): transparent with black
            // top/bottom vignettes instead of a solid #CC000000 bar.
            if (ui.controlsVisible && !playback.isPip) {
                PlayerControls(
                    viewModel = viewModel,
                    playback = playback,
                    positionMs = position,
                    durationMs = duration,
                    clock = clock,
                    nowProgram = nowProgram,
                    nextProgram = nextProgram,
                    nowMs = nowMs,
                    recElapsed = recElapsed,
                    focusRequester = firstControlFocus,
                    onSelector = { selector = it },
                    onVolume = { openVolume() },
                    onProgramme = { epgPanelOpen = true },
                    onScores = {
                        // Scores button = Match Center toggle (2026-11-22):
                        // off -> enable + open the scores; on + open ->
                        // disable + close; on + closed -> just reopen.
                        if (!matchCenterOn) {
                            viewModel.toggleMatchCenter()
                            scoresOpen = true
                        } else if (scoresOpen) {
                            viewModel.toggleMatchCenter()
                            scoresOpen = false
                        } else {
                            scoresOpen = true
                        }
                    },
                    onToggleFailover = { viewModel.toggleSmartFailover() },
                    onExit = { exitPlayer() }
                )
            }

            // Volume OSD (PL08).
            if (volumeOsd) {
                VolumeOsd(
                    level = volumeLevel,
                    max = volumeMax,
                    onAdjust = { adjustVolume(it) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Programme panel (PL09): EPG of the current live channel.
        if (epgPanelOpen && ui.kind == "live") {
            val channelTitle = when (val item = playback.item) {
                is PlaybackItem.Live -> item.channel.customName ?: item.channel.name
                else -> ""
            }
            Dialog(
                onDismissRequest = { epgPanelOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f)
                        .background(Color(0xFF14100A), RoundedCornerShape(20.dp))
                        .border(2.dp, BorderStrong, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$channelTitle — ${stringResource(R.string.player_programme)}",
                        fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldWhite
                    )
                    if (epgPrograms.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_epg),
                            fontSize = 18.sp, color = TextMuted
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(480.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(epgPrograms) { prog ->
                                val isNow = nowMs in prog.startMillis until prog.endMillis
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isNow) Gold else CardSurface)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${fmtHM(prog.startMillis)} – ${fmtHM(prog.endMillis)}",
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = if (isNow) GoldDeep else Gold
                                    )
                                    Text(
                                        text = prog.title,
                                        fontSize = 18.sp,
                                        fontWeight = if (isNow) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isNow) GoldDeep else GoldWhite,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    GoldButton(
                        text = stringResource(R.string.close),
                        onClick = { epgPanelOpen = false },
                        modifier = Modifier.fillMaxWidth().focusRequester(programmeFocus)
                    )
                }
            }
        }

        // Selector dialogs (components agent)
        when (selector) {
            "audio" -> SelectorDialog(
                title = stringResource(R.string.audio),
                options = viewModel.playerManager.audioTracks().map { SelectorOption(it.id, it.label) },
                selectedId = viewModel.playerManager.audioTracks().firstOrNull { it.label == playback.audioTrackLabel }?.id,
                onSelect = { id -> viewModel.setAudioTrack(id); selector = null },
                onDismiss = { selector = null }
            )
            "subs" -> SelectorDialog(
                title = stringResource(R.string.subtitles),
                options = viewModel.playerManager.subtitleTracks().map { SelectorOption(it.id, it.label) },
                selectedId = viewModel.playerManager.subtitleTracks().firstOrNull { it.label == playback.subtitleLabel }?.id,
                onSelect = { id -> viewModel.setSubtitleTrack(id); selector = null },
                onDismiss = { selector = null }
            )
            "quality" -> SelectorDialog(
                title = stringResource(R.string.quality),
                options = viewModel.playerManager.qualityOptions().map { SelectorOption(it.id, it.label) },
                selectedId = viewModel.playerManager.qualityOptions().firstOrNull { it.label == playback.qualityLabel }?.id,
                onSelect = { id ->
                    val mode = when (id) {
                        "480" -> QualityMode.Q480; "720" -> QualityMode.Q720
                        "1080" -> QualityMode.Q1080; "4k" -> QualityMode.Q4K
                        else -> QualityMode.AUTO
                    }
                    viewModel.setQuality(mode); selector = null
                },
                onDismiss = { selector = null }
            )
            "display" -> SelectorDialog(
                title = stringResource(R.string.display),
                options = listOf(
                    SelectorOption("fill", stringResource(R.string.display_fill)),
                    SelectorOption("original", stringResource(R.string.display_original)),
                    SelectorOption("zoom", stringResource(R.string.display_zoom))
                ),
                selectedId = when (playback.displayMode) {
                    DisplayMode.FILL -> "fill"
                    DisplayMode.ORIGINAL -> "original"
                    DisplayMode.ZOOM -> "zoom"
                },
                onSelect = { id ->
                    val mode = when (id) {
                        "original" -> DisplayMode.ORIGINAL
                        "zoom" -> DisplayMode.ZOOM
                        else -> DisplayMode.FILL
                    }
                    viewModel.setDisplayMode(mode); selector = null
                },
                onDismiss = { selector = null }
            )
            "speed" -> SelectorDialog(
                title = stringResource(R.string.speed),
                options = viewModel.speedOptions.map { speed ->
                    SelectorOption(speedLabel(speed), speedLabel(speed))
                },
                selectedId = playback.speedLabel,
                onSelect = { id ->
                    id.removeSuffix("x").toFloatOrNull()?.let { viewModel.setSpeed(it) }
                    selector = null
                },
                onDismiss = { selector = null }
            )
        }

        if (ui.pinRequired) {
            PinDialog(
                title = stringResource(R.string.enter_pin),
                errorMessage = if (ui.pinError) stringResource(R.string.pin_wrong) else null,
                onComplete = { pin -> viewModel.checkPin(pin) },
                onCancel = { viewModel.dismissPin(); exitPlayer() }
            )
        }

        // Match Center (2026-11-22): live scores over the running match —
        // playback continues behind the dialog.
        if (scoresOpen) {
            val matchViewModel: MatchCenterViewModel = hiltViewModel()
            val matches by matchViewModel.matches.collectAsState()
            val loading by matchViewModel.loading.collectAsState()
            Dialog(
                onDismissRequest = { scoresOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.85f)
                        .background(Color(0xFF14100A), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.match_center_title),
                            fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldWhite
                        )
                        GoldButton(text = stringResource(R.string.refresh), modifier = Modifier.focusRequester(scoresFocus)) { matchViewModel.refresh() }
                    }
                    MatchCenterList(
                        matches = matches, loading = loading,
                        modifier = Modifier.fillMaxWidth().height(480.dp)
                    )
                    GoldButton(
                        text = stringResource(R.string.back),
                        onClick = { scoresOpen = false },
                        outlined = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Video surface: ExoPlayer PlayerView, or VLC VlcVideoLayout (CONTRACT §7). */
@Composable
private fun VideoSurface(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val playback by viewModel.playbackState.collectAsState()
    if (playback.engine == Engine.VLC) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    // CONTRACT §5: vlcVideoLayout is set by the UI when the
                    // VLC surface is created (the player agent made it a `var`
                    // on the interface for exactly this reason).
                    viewModel.playerManager.vlcVideoLayout = layout
                    viewModel.playerManager.vlcPlayer?.attachViews(layout, null, true, false)
                }
            },
            modifier = modifier.background(Color.Black)
        )
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.playerManager.exoPlayer
                    useController = false // Stream Master draws its own controls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view -> view.player = viewModel.playerManager.exoPlayer },
            modifier = modifier.background(Color.Black)
        )
    }
}

/**
 * Controls overlay (PL03-PL07, PL17): transparent, with black top/bottom
 * vignettes. Header (logo 74dp + channel 32sp + sub-line 19sp + badges +
 * Georgia clock 26sp), big center play button while paused, program
 * labels + 14dp progress bar with 26dp knob + next line, then the three
 * control groups and a second row with the approved extra buttons.
 */
@Composable
private fun PlayerControls(
    viewModel: PlayerViewModel,
    playback: com.streammaster.tv.player.PlaybackState,
    positionMs: Long,
    durationMs: Long,
    clock: String,
    nowProgram: EpgProgram?,
    nextProgram: EpgProgram?,
    nowMs: Long,
    recElapsed: String?,
    focusRequester: FocusRequester,
    onSelector: (String) -> Unit,
    onVolume: () -> Unit,
    onProgramme: () -> Unit,
    onScores: () -> Unit,
    onToggleFailover: () -> Unit,
    onExit: () -> Unit
) {
    val ui by viewModel.ui.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    val isLive = ui.kind == "live"
    val title = when (val item = playback.item) {
        is PlaybackItem.Live -> item.channel.customName ?: item.channel.name
        is PlaybackItem.Movie -> item.film.title
        is PlaybackItem.EpisodeItem -> "${item.series.title} — ${item.episode.title}"
        is PlaybackItem.RecordingItem -> item.recording.title
        is PlaybackItem.DownloadItem -> item.download.title
        null -> ""
    }
    val subLine = when {
        isLive -> listOfNotNull(
            ui.channel?.category?.takeIf { it.isNotBlank() },
            nowProgram?.title ?: ui.channel?.nowTitle?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
        ui.kind == "film" -> ui.film?.let {
            "${it.genre} • ${formatDurMin(it.durationMin)} • ${it.quality}"
        } ?: ""
        else -> ""
    }
    // Progress (PL06): EPG span for live, position/duration for VOD,
    // live edge (full bar, no knob) when live without EPG.
    val progressFraction: Float? = when {
        isLive && nowProgram != null ->
            ((nowMs - nowProgram.startMillis).toFloat() /
                (nowProgram.endMillis - nowProgram.startMillis).toFloat()).coerceIn(0f, 1f)
        isLive -> null
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> null
    }
    val startLabel = when {
        isLive && nowProgram != null -> fmtHM(nowProgram.startMillis)
        isLive -> ""
        else -> "00:00"
    }
    val endLabel = when {
        isLive && nowProgram != null -> fmtHM(nowProgram.endMillis)
        isLive -> ""
        durationMs > 0 -> formatDurMin((durationMs / 60_000L).toInt())
        else -> ""
    }
    val progTitle = when {
        isLive -> nowProgram?.title
            ?: ui.channel?.nowTitle?.takeIf { it.isNotBlank() } ?: title
        else -> title
    }
    val nextLine: String? = when {
        isLive -> nextProgram?.let {
            stringResource(R.string.player_next_format, fmtHM(it.startMillis), it.title)
        }
        durationMs > 0 -> stringResource(
            R.string.player_resume_format,
            (positionMs * 100L / durationMs).toInt()
        )
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        // Vignettes (PL17).
        Box(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(460.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 52.dp, top = 34.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header (PL03).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(74.dp).clip(RoundedCornerShape(20.dp))
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = title,
                            fontSize = 32.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif, color = GoldWhite,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (subLine.isNotEmpty()) {
                            Text(
                                text = subLine,
                                fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                color = GoldBright,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isLive) HeaderPill(text = stringResource(R.string.player_live))
                    StatusPills(
                        recElapsed = recElapsed,
                        isTimeshift = playback.isTimeshift,
                        sleepRemainingMin = playback.sleepRemainingMin,
                        usingBackup = playback.usingBackup,
                        networkSpeedLabel = playback.networkSpeedLabel
                    )
                    if (isLive) HeaderQualityPill(text = playback.qualityLabel)
                    Text(
                        text = clock,
                        fontSize = 26.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, color = GoldWhite
                    )
                }
            }

            // Big center play button (PL04): only while paused.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!playback.isPlaying) {
                    BigPlayButton { viewModel.togglePlayPause() }
                }
            }

            // Bottom: program labels + progress + next + controls.
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = startLabel,
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GoldBright
                    )
                    Text(
                        text = progTitle,
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite,
                        textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = endLabel,
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GoldBright
                    )
                }
                PlayerProgressBar(fraction = progressFraction)
                if (nextLine != null) {
                    Text(
                        text = nextLine,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = GoldSoft
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Group 1: -10s / play-pause / +10s / previous / live.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FsCtrlButton("-10 s", R.drawable.ic_rewind) { viewModel.skip(-10_000) }
                        FsMainPlayButton(
                            isPlaying = playback.isPlaying,
                            modifier = Modifier.focusRequester(focusRequester)
                        ) { viewModel.togglePlayPause() }
                        FsCtrlButton("+10 s", R.drawable.ic_forward) { viewModel.skip(10_000) }
                        if (isLive) {
                            FsCtrlButton(
                                stringResource(R.string.previous_channel),
                                R.drawable.ic_clock
                            ) {
                                viewModel.previousChannel()
                            }
                        }
                        if (isLive && !playback.atLiveEdge) {
                            FsCtrlButton(stringResource(R.string.go_live), R.drawable.ic_tv) {
                                viewModel.goLiveEdge()
                            }
                        }
                    }
                    // Group 2: Volume / Subtitles / Audio / Programme / Display.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FsCtrlButton(stringResource(R.string.player_volume), R.drawable.ic_volume) {
                            onVolume()
                        }
                        FsCtrlButton(stringResource(R.string.subtitles), R.drawable.ic_file_text) {
                            onSelector("subs")
                        }
                        FsCtrlButton(stringResource(R.string.audio), R.drawable.ic_volume_2) {
                            onSelector("audio")
                        }
                        if (isLive) {
                            FsCtrlButton(
                                stringResource(R.string.player_programme),
                                R.drawable.ic_calendar
                            ) { onProgramme() }
                        }
                        FsCtrlButton(stringResource(R.string.display), R.drawable.ic_cast) {
                            onSelector("display")
                        }
                    }
                    // Group 3: Favorite / Quality / PiP / Quit.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FsCtrlButton(stringResource(R.string.favorite), R.drawable.ic_heart) {
                            viewModel.toggleFavorite()
                        }
                        FsCtrlButton(stringResource(R.string.quality), R.drawable.ic_settings) {
                            onSelector("quality")
                        }
                        FsCtrlButton("PiP", R.drawable.ic_maximize) { viewModel.enterPip() }
                        FsCtrlButton(stringResource(R.string.player_quit), R.drawable.ic_x) {
                            onExit()
                        }
                    }
                }
                // Second row: approved post-model buttons (PL11), kept.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FsCtrlButton(stringResource(R.string.speed)) { onSelector("speed") }
                    if (isLive) {
                        FsCtrlButton(
                            if (recState is com.streammaster.tv.player.RecordingState.Active)
                                stringResource(R.string.record_stop)
                            else stringResource(R.string.record_start)
                        ) { viewModel.toggleRecording() }
                        // Smart failover toggle (2026-11-22): opt-in, off by
                        // default, persists until the user turns it off here.
                        val failoverOn by viewModel.smartFailover.collectAsState()
                        FsCtrlButton(
                            stringResource(R.string.failover),
                            // R-S3: ic_check icon when on instead of "✓ ".
                            iconRes = if (failoverOn) R.drawable.ic_check else null
                        ) { onToggleFailover() }
                        // Match Center toggle (2026-11-22): same pattern —
                        // the Scores button enables it (and opens the
                        // scores); while enabled it shows the check icon
                        // until turned off from this button.
                        val centerOn by viewModel.matchCenter.collectAsState()
                        FsCtrlButton(
                            stringResource(R.string.scores),
                            // R-S3: ic_check icon when on instead of "✓ ".
                            iconRes = if (centerOn) R.drawable.ic_check else null
                        ) { onScores() }
                    }
                    // External player (not for local recordings/downloads).
                    if (ui.kind != "recording" && ui.kind != "download") {
                        FsCtrlButton(stringResource(R.string.external_player)) {
                            viewModel.playExternally()
                        }
                    }
                }
            }
        }
    }
}

/** Model .fs-ctrl button (PL07): 62dp high, 31dp radius, 2px border, 16sp ExtraBold, icon + text. */
@Composable
private fun FsCtrlButton(
    text: String,
    iconRes: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(Color(0xD20C0802))
            .border(2.dp, BorderGold, RoundedCornerShape(31.dp))
            .tvFocus().clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                color = GoldWhite, maxLines = 1
            )
        }
    }
}

/** Model .fs-ctrl.main (PL10): gold 74dp circle, 30dp icon. */
@Composable
private fun FsMainPlayButton(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.size(74.dp)
            .background(GoldGradient, CircleShape)
            .border(2.dp, GoldWhite, CircleShape)
            .tvFocus().clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = null,
            tint = GoldDeep,
            modifier = Modifier.size(30.dp)
        )
    }
}

/** Model .fs-big-play (PL04): 130dp circle, 3px gold border, 54dp icon. */
@Composable
private fun BigPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(130.dp)
            .background(Color(0xD20C0802), CircleShape)
            .border(3.dp, GoldBright, CircleShape)
            // Focusable like every other control: without tvFocus the
            // D-pad can land on it with no halo.
            .tvFocus()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play),
            contentDescription = null,
            tint = GoldBright,
            modifier = Modifier.size(54.dp)
        )
    }
}

/** Model .fs-badge pill (PL03): EN DIRECT. */
@Composable
private fun HeaderPill(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.5.dp, GoldBright, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, fontSize = 15.sp, fontWeight = FontWeight.Black,
            color = GoldWhite, letterSpacing = 2.sp
        )
    }
}

/** Model .fs-quality pill (PL03). */
@Composable
private fun HeaderQualityPill(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(1.5.dp, BorderGold, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, fontSize = 15.sp, fontWeight = FontWeight.Black, color = GoldBright
        )
    }
}

/** Status pills (PL16): REC / TIMESHIFT / SLEEP / backup / network speed. */
@Composable
private fun StatusPills(
    recElapsed: String?,
    isTimeshift: Boolean,
    sleepRemainingMin: Int,
    usingBackup: Boolean,
    networkSpeedLabel: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (recElapsed != null) {
            Text(
                text = "● REC $recElapsed",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFC62828))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (isTimeshift) {
            Text(
                text = stringResource(R.string.timeshift),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Gold)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (sleepRemainingMin > 0) {
            Text(
                text = stringResource(R.string.sleep_badge, sleepRemainingMin),
                fontSize = 14.sp, color = GoldWhite,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(CardSurface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (usingBackup) {
            Text(
                text = stringResource(R.string.backup_source),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Gold)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (networkSpeedLabel.isNotEmpty()) {
            Text(
                text = networkSpeedLabel,
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Gold)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** Model .fs-progress (PL06): 14dp bar + 26dp gold knob with white border. */
@Composable
private fun PlayerProgressBar(fraction: Float?) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxWidth().height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .border(1.5.dp, BorderGold, RoundedCornerShape(7.dp))
        ) {
            if (fraction != null) {
                Box(
                    modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(GoldGradient, RoundedCornerShape(7.dp))
                )
            } else {
                // Live edge: full gold bar, no draggable knob.
                Box(Modifier.fillMaxSize().background(GoldGradient, RoundedCornerShape(7.dp)))
            }
        }
        if (fraction != null) {
            val f = fraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier.size(26.dp)
                    .offset(x = maxWidth * f - 13.dp)
                    .background(GoldGradient, CircleShape)
                    .border(3.dp, GoldWhite, CircleShape)
            )
        }
    }
}

/** Model .fs-loading-bar (PL02): 420x8dp track, 40% gold segment sliding. */
@Composable
private fun PlayerLoadingBar() {
    val transition = rememberInfiniteTransition(label = "player-loading")
    val slide by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "player-loading-slide"
    )
    Box(
        modifier = Modifier.width(420.dp).height(8.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0x29FFE89E))
    ) {
        Box(
            modifier = Modifier.width(168.dp).height(8.dp)
                .offset(x = (-168f + slide * 588f).dp)
                .background(GoldGradient, RoundedCornerShape(5.dp))
        )
    }
}

/** Volume OSD (PL08): dark box with the STREAM_MUSIC level and +/− steps. */
@Composable
private fun VolumeOsd(
    level: Int,
    max: Int,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pct = if (max > 0) (level * 100 / max) else 0
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(Color(0xE0080501))
            .border(2.dp, BorderStrong, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_volume),
                contentDescription = null,
                tint = GoldBright,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "${stringResource(R.string.player_volume)} $pct %",
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite
            )
            Box(
                modifier = Modifier.width(300.dp).height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                        .background(GoldGradient, RoundedCornerShape(4.dp))
                )
            }
            VolumeStepButton(text = "−") { onAdjust(-1) }
            VolumeStepButton(text = "+") { onAdjust(1) }
        }
    }
}

@Composable
private fun VolumeStepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp)
            .clip(CircleShape)
            .background(CardSurface)
            .border(2.dp, BorderGold, CircleShape)
            .tvFocus().clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
    }
}

/** Model .fs-pip-btn (PL12): small button in the PiP bar. */
@Composable
private fun PipBarButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x24E5C158))
            .border(2.dp, BorderStrong, RoundedCornerShape(12.dp))
            .tvFocus().clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite)
    }
}

/** Two slide-up lists: bouquets -> channels (PL13/PL14). Panel is 68% of
 *  the screen height, sliding from the bottom, with the "Chaînes" header
 *  + hint and the Bouquets | Chaînes column titles. */
@Composable
private fun PlayerListsOverlay(
    viewModel: PlayerViewModel,
    allChannels: List<Channel>,
    selectedBouquetId: String?,
    onBouquet: (String) -> Unit,
    onChannel: (Channel) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    // Bouquets/channels come from the Live data via a lightweight local
    // ViewModel-free collect: reuse the PlayerViewModel's content through
    // the shared repository is not exposed here, so the overlay reads the
    // current playback item's bouquet siblings from the Live screen state
    // via the player's channel list is unavailable — instead it lists the
    // channels of the current bouquet from the repository through a
    // dedicated hiltViewModel(LiveViewModel) instance.
    val liveViewModel: LiveViewModel = hiltViewModel()
    val liveState by liveViewModel.state.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val bouquets = liveState.bouquets
    val channels = liveState.channels.filter {
        selectedBouquetId == null || it.bouquetId == selectedBouquetId
    }
    val playingId = (playback.item as? PlaybackItem.Live)?.channel?.id
    Column(
        modifier = modifier.fillMaxWidth().fillMaxHeight(0.68f)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFA1A1306), Color(0xFA0A0702))
                )
            )
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(BorderStrong))
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.channels),
                    fontSize = 26.sp, fontWeight = FontWeight.Black, color = GoldWhite
                )
                Text(
                    text = stringResource(R.string.player_lists_hint),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldSoft
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Column(modifier = Modifier.width(300.dp)) {
                    Text(
                        text = stringResource(R.string.bouquets).uppercase(),
                        fontSize = 16.sp, fontWeight = FontWeight.Black,
                        color = GoldBright, letterSpacing = 2.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight().padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(bouquets) { index, bouquet ->
                            val selected = bouquet.id == (selectedBouquetId ?: liveState.selectedBouquetId)
                            val count = allChannels.count { it.bouquetId == bouquet.id }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) Color(0x2EE5C158) else Color(0x12E5C158)
                                    )
                                    .border(
                                        2.dp,
                                        if (selected) GoldBright else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    // The focusRequester must come BEFORE
                                    // tvFocus (which owns the focusable).
                                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                    .tvFocus()
                                    .clickable {
                                        onBouquet(bouquet.id)
                                        liveViewModel.selectBouquet(bouquet.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bouquet.name,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                                    color = GoldWhite,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = count.toString(),
                                    fontSize = 15.sp, fontWeight = FontWeight.Black,
                                    color = GoldSoft
                                )
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.channels).uppercase(),
                        fontSize = 16.sp, fontWeight = FontWeight.Black,
                        color = GoldBright, letterSpacing = 2.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight().padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(channels) { channel ->
                            val playing = channel.id == playingId
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (playing) Color(0x2EE5C158) else Color(0x12E5C158)
                                    )
                                    .border(
                                        2.dp,
                                        if (playing) GoldBright else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .tvFocus().clickable { onChannel(channel) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = channel.customNum?.toString() ?: channel.num,
                                    fontSize = 15.sp, fontWeight = FontWeight.Black,
                                    color = GoldSoft,
                                    modifier = Modifier.width(30.dp)
                                )
                                Box(
                                    modifier = Modifier.size(52.dp, 40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(GoldGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = channelInitials(channel.customName ?: channel.name),
                                        fontSize = 15.sp, fontWeight = FontWeight.Black,
                                        color = GoldDeep
                                    )
                                }
                                Text(
                                    text = channel.customName ?: channel.name,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                                    color = GoldWhite,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                val nowTitle = channel.nowTitle
                                if (nowTitle.isNotBlank()) {
                                    Text(
                                        text = nowTitle,
                                        modifier = Modifier.weight(0.6f),
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = GoldSoft,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (playing) {
                                    Box(
                                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(GoldGradient)
                                            .padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.player_playing),
                                            fontSize = 14.sp, fontWeight = FontWeight.Black,
                                            color = GoldDeep, letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmtHM(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDurMin(min: Int): String =
    if (min >= 60) "${min / 60}h ${min % 60}min" else "${min}min"

private fun speedLabel(speed: Float): String =
    if (speed == 1f) "1x" else "${String.format(Locale.US, "%g", speed)}x"

private fun channelInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].first().toString() + words[1].first().toString()).uppercase()
    }
}
