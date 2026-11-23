package com.streammaster.tv.ui.screens

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.player.DisplayMode
import com.streammaster.tv.player.Engine
import com.streammaster.tv.data.model.PlaybackItem
import com.streammaster.tv.player.QualityMode
import com.streammaster.tv.ui.components.PinDialog
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.player.remote.RemoteCommand
import com.streammaster.tv.ui.viewmodel.MatchCenterViewModel
import com.streammaster.tv.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.streammaster.tv.ui.components.GoldButton

/**
 * PlayerScreen — fullscreen (CONTRACT §7).
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
    val position by viewModel.positionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    val context = LocalContext.current
    var selector by remember { mutableStateOf<String?>(null) } // audio | subs | quality | display
    var scoresOpen by remember { mutableStateOf(false) } // Match Center dialog (live only)
    // Match Center toggle (2026-11-22): opt-in, persists until the user
    // turns it off from the same Scores button in the player.
    val matchCenterOn by viewModel.matchCenter.collectAsState()
    LaunchedEffect(matchCenterOn) { if (!matchCenterOn) scoresOpen = false }
    var clock by remember { mutableStateOf("") }
    var listBouquetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kind, id) { viewModel.load(kind, id) }
    LaunchedEffect(position, duration) { viewModel.onPositionAdvanced(position, duration) }
    fun exitPlayer() {
        viewModel.stop()
        navController.previousBackStackEntry?.savedStateHandle?.set("focusedId", id)
        navController.popBackStack()
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
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }
    LaunchedEffect(ui.toastText) {
        ui.toastText?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }
    LaunchedEffect(ui.pinError) {
        if (ui.pinError) {
            Toast.makeText(context, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(ui.introSkipped) {
        if (ui.introSkipped) {
            Toast.makeText(context, R.string.intro_skipped, Toast.LENGTH_SHORT).show()
            viewModel.clearIntroFlag()
        }
    }
    LaunchedEffect(ui.filmFinished) {
        if (ui.filmFinished) {
            Toast.makeText(context, R.string.film_finished, Toast.LENGTH_LONG).show()
            viewModel.clearFilmFinished()
            navController.popBackStack()
        }
    }
    LaunchedEffect(playback.showError) {
        if (playback.showError) viewModel.armControlsAutoHide()
    }

    fun onBackPressed(): Boolean {
        return when {
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
                .clickable { viewModel.showControls() }
        ) {
            if (playback.isPip) {
                // PiP: floating 480x270 window bottom-right with Agrandir/Fermer.
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
                    Column {
                        Box(
                            modifier = Modifier.size(480.dp, 270.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black)
                        ) { VideoSurface(viewModel) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                            GoldButton(text = stringResource(R.string.enlarge)) { viewModel.exitPip() }
                            GhostButton(text = stringResource(R.string.close)) { exitPlayer() }
                        }
                    }
                }
            } else {
                VideoSurface(viewModel, Modifier.fillMaxSize())
            }

            // Digit-entry OSD
            ui.digitOsd?.let { osd ->
                Text(
                    text = stringResource(R.string.channel_osd, osd),
                    fontSize = 40.sp, fontWeight = FontWeight.Black, color = Gold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                )
            }

            // Error overlay
            if (playback.showError) {
                ErrorScreen(
                    navController = navController,
                    message = playback.errorMessage,
                    onRetry = { playback.item?.let { viewModel.playerManager.play(it) } },
                    onBack = { exitPlayer() },
                    onChangeChannel = { viewModel.openLists() }
                )
            }

            // Slide-up lists (bouquets -> channels)
            if (ui.listsOpen) {
                PlayerListsOverlay(
                    viewModel = viewModel,
                    selectedBouquetId = listBouquetId,
                    onBouquet = { listBouquetId = it },
                    onChannel = { channel -> viewModel.onChannelChosenFromLists(channel) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Controls (auto-hide after 4s)
            if (ui.controlsVisible && !playback.isPip) {
                PlayerControls(
                    viewModel = viewModel,
                    playback = playback,
                    positionMs = position,
                    durationMs = duration,
                    clock = clock,
                    onSelector = { selector = it },
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
                    onExit = { exitPlayer() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // TIMESHIFT badge + sleep badge + REC badge
            Row(modifier = Modifier.align(Alignment.TopStart).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val rec = recState as? com.streammaster.tv.player.RecordingState.Active
                if (rec != null) {
                    // Recomposed every second by the clock ticker, so the
                    // elapsed time stays live.
                    val elapsedS = ((System.currentTimeMillis() - rec.startedAtMillis) / 1000).toInt()
                    val elapsed = "%02d:%02d".format(elapsedS / 60, elapsedS % 60)
                    Text(text = "● REC $elapsed", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFC62828)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (playback.isTimeshift) {
                    Text(text = stringResource(R.string.timeshift), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (playback.sleepRemainingMin > 0) {
                    Text(text = stringResource(R.string.sleep_badge, playback.sleepRemainingMin), fontSize = 14.sp, color = GoldWhite, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CardSurface).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (playback.isBuffering) {
                    Text(text = stringResource(R.string.buffering), fontSize = 14.sp, color = GoldWhite, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CardSurface).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                // Smart failover badge: the primary stream failed and the
                // player is on a backup source (2026-11-22).
                if (playback.usingBackup) {
                    Text(text = stringResource(R.string.backup_source), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                // Live network speed (2026-11-22): proves the max-speed mode
                // is using the available bandwidth, e.g. "12.5 Mb/s".
                if (playback.networkSpeedLabel.isNotEmpty()) {
                    Text(text = playback.networkSpeedLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).padding(horizontal = 10.dp, vertical = 4.dp))
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
                        GoldButton(text = stringResource(R.string.refresh)) { matchViewModel.refresh() }
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

@Composable
private fun PlayerControls(
    viewModel: PlayerViewModel,
    playback: com.streammaster.tv.player.PlaybackState,
    positionMs: Long,
    durationMs: Long,
    clock: String,
    onSelector: (String) -> Unit,
    onScores: () -> Unit,
    onToggleFailover: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (val item = playback.item) {
        is PlaybackItem.Live -> item.channel.customName ?: item.channel.name
        is PlaybackItem.Movie -> item.film.title
        is PlaybackItem.EpisodeItem -> "${item.series.title} — ${item.episode.title}"
        is PlaybackItem.RecordingItem -> item.recording.title
        is PlaybackItem.DownloadItem -> item.download.title
        null -> ""
    }
    Column(
        modifier = modifier.fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldWhite)
            Text(text = clock, fontSize = 20.sp, color = Gold)
        }
        if (durationMs > 0) {
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Gold, trackColor = Color.White.copy(alpha = 0.25f)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatMs(positionMs), fontSize = 14.sp, color = TextMuted)
                Text(text = formatMs(durationMs), fontSize = 14.sp, color = TextMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerButton(if (playback.isPlaying) "⏸" else "▶") { viewModel.togglePlayPause() }
            PlayerButton("−10s") { viewModel.skip(-10_000) }
            PlayerButton("+10s") { viewModel.skip(10_000) }
            PlayerButton(stringResource(R.string.previous_channel)) { viewModel.previousChannel() }
            PlayerButton(stringResource(R.string.go_live)) { viewModel.goLiveEdge() }
            PlayerButton(stringResource(R.string.audio)) { onSelector("audio") }
            PlayerButton(stringResource(R.string.subtitles)) { onSelector("subs") }
            PlayerButton(stringResource(R.string.quality)) { onSelector("quality") }
            PlayerButton(stringResource(R.string.display)) { onSelector("display") }
            PlayerButton(stringResource(R.string.speed)) { onSelector("speed") }
            PlayerButton(stringResource(R.string.pip)) { viewModel.enterPip() }
            PlayerButton("♥") { viewModel.toggleFavorite() }
            // REC (live only): start/stop an instant recording of this channel.
            val ui by viewModel.ui.collectAsState()
            val recState by viewModel.recordingState.collectAsState()
            if (ui.kind == "live") {
                PlayerButton(
                    if (recState is com.streammaster.tv.player.RecordingState.Active) stringResource(R.string.record_stop)
                    else stringResource(R.string.record_start)
                ) { viewModel.toggleRecording() }
                // Smart failover toggle (2026-11-22): opt-in, off by
                // default, persists until the user turns it off here.
                val failoverOn by viewModel.smartFailover.collectAsState()
                PlayerButton(
                    if (failoverOn) "✓ ${stringResource(R.string.failover)}"
                    else stringResource(R.string.failover)
                ) { onToggleFailover() }
                // Match Center toggle (2026-11-22): same pattern — the
                // Scores button enables it (and opens the scores); while
                // enabled it shows ✓ until turned off from this button.
                val centerOn by viewModel.matchCenter.collectAsState()
                PlayerButton(
                    if (centerOn) "✓ ${stringResource(R.string.scores)}"
                    else stringResource(R.string.scores)
                ) { onScores() }
            }
            // External player (not for local recordings/downloads).
            if (ui.kind != "recording" && ui.kind != "download") {
                PlayerButton(stringResource(R.string.external_player)) { viewModel.playExternally() }
            }
            PlayerButton(stringResource(R.string.back)) { onExit() }
        }
    }
}

@Composable
private fun PlayerButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardSurface)
            .tvFocus().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite) }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

private fun speedLabel(speed: Float): String =
    if (speed == 1f) "1x" else "${String.format(Locale.US, "%g", speed)}x"

/** Two slide-up lists: bouquets -> channels. */
@Composable
private fun PlayerListsOverlay(
    viewModel: PlayerViewModel,
    selectedBouquetId: String?,
    onBouquet: (String) -> Unit,
    onChannel: (com.streammaster.tv.data.model.Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    // Bouquets/channels come from the Live data via a lightweight local
    // ViewModel-free collect: reuse the PlayerViewModel's content through
    // the shared repository is not exposed here, so the overlay reads the
    // current playback item's bouquet siblings from the Live screen state
    // via the player's channel list is unavailable — instead it lists the
    // channels of the current bouquet from the repository through a
    // dedicated hiltViewModel(LiveViewModel) instance.
    val liveViewModel: com.streammaster.tv.ui.viewmodel.LiveViewModel = hiltViewModel()
    val liveState by liveViewModel.state.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    val bouquets = liveState.bouquets
    val channels = liveState.channels.filter {
        selectedBouquetId == null || it.bouquetId == selectedBouquetId
    }
    Row(
        modifier = modifier.fillMaxWidth().height(320.dp)
            .background(Color(0xE6000000)).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        LazyColumn(modifier = Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bouquets) { bouquet ->
                val selected = bouquet.id == (selectedBouquetId ?: liveState.selectedBouquetId)
                Text(
                    text = bouquet.name, fontSize = 18.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) GoldDeep else GoldWhite,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Gold else CardSurface)
                        .tvFocus().clickable { onBouquet(bouquet.id); liveViewModel.selectBouquet(bouquet.id) }
                        .padding(12.dp)
                )
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(channels) { channel ->
                val playing = (playback.item as? PlaybackItem.Live)?.channel?.id == channel.id
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (playing) Gold else CardSurface)
                        .tvFocus().clickable { onChannel(channel) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = channel.customName ?: channel.name, fontSize = 18.sp, color = if (playing) GoldDeep else GoldWhite)
                    Text(text = channel.customNum?.toString() ?: channel.num, fontSize = 16.sp, color = if (playing) GoldDeep else Gold)
                }
            }
        }
    }
}
