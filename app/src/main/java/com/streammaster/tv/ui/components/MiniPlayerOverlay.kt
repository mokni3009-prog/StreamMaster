package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.PlaybackItem
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.PlayerExtrasViewModel
import com.streammaster.tv.ui.viewmodel.PlayerViewModel
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * In-app mini player (§4.44): the 480x270 floating window, bottom-right,
 * that stays visible over the app's other screens while the user browses
 * inside the app, with the sound continuing. Rendered by Shell (which
 * wraps every browsable screen) whenever the engine reports `isPip`.
 *
 * How it fits together: pressing Back while in PiP inside the player
 * does NOT pop the player route — it navigates to the matching view
 * leaving the player entry underneath in the back stack, so the
 * PlayerViewModel (and its loaded item) survive. Agrandir pops back to
 * that retained player route (no stream restart); if the entry is gone
 * (e.g. a sidebar navigation popped it), it opens a fresh player route
 * for the same item instead. Fermer stops playback, leaves PiP and
 * removes the player entry from the back stack.
 *
 * The video surface re-binds here exactly like PlayerScreen's
 * VideoSurface: the PlayerScreen surface is disposed while the player
 * route is not composed, so only one surface is ever bound to the
 * engine. Audio keeps playing because the engine itself is never
 * released. System PiP on leaving the app (MainActivity) is untouched.
 */
@Composable
fun MiniPlayerOverlay(
    navController: NavHostController,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val playback by playerViewModel.playbackState.collectAsState()
    if (!playback.isPip) return
    val item = playback.item
    val title = when (item) {
        is PlaybackItem.Live -> item.channel.customName ?: item.channel.name
        is PlaybackItem.Movie -> item.film.title
        is PlaybackItem.EpisodeItem -> "${item.series.title} — ${item.episode.title}"
        is PlaybackItem.RecordingItem -> item.recording.title
        is PlaybackItem.DownloadItem -> item.download.title
        null -> ""
    }
    // A-P1 (2026-11-23): the floating window NEVER requests focus on
    // its own when it appears — the focus stays where the user was
    // (channel card / poster / sidebar item). Both buttons stay
    // focusable, so the user reaches them with the arrows whenever they
    // want, or with an explicit press. The requester is kept attached
    // to Agrandir (reachable, never auto-requested).
    val enlargeFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier.padding(24.dp)
                .size(480.dp, 270.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(2.dp, BorderStrong, RoundedCornerShape(18.dp))
        ) {
            MiniVideoSurface(playerViewModel)
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
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                MiniPlayerButton(
                    text = stringResource(R.string.enlarge),
                    modifier = Modifier.focusRequester(enlargeFocus)
                ) {
                    // Back to the retained full player; fresh route only
                    // when the player entry is no longer in the stack.
                    val popped = navController.popBackStack(Routes.PLAYER, inclusive = false)
                    if (!popped) {
                        playerRouteFor(item)?.let { navController.navigate(it) }
                    }
                }
                MiniPlayerButton(text = stringResource(R.string.close)) {
                    playerViewModel.playerManager.stop()
                    playerViewModel.exitPip()
                    if (!navController.popBackStack(Routes.PLAYER, inclusive = true)) {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }
}

/** Player route for the item currently in the mini player (fallback
 *  when the retained player entry is gone). Series resume at the series
 *  (PlayerViewModel.load plays from the start / resume point). */
private fun playerRouteFor(item: PlaybackItem?): String? = when (item) {
    is PlaybackItem.Live -> Routes.player("live", item.channel.id)
    is PlaybackItem.Movie -> Routes.player("film", item.film.id)
    is PlaybackItem.EpisodeItem -> Routes.player("series", item.series.id)
    is PlaybackItem.RecordingItem -> Routes.player("recording", item.recording.id)
    is PlaybackItem.DownloadItem -> Routes.player("download", item.download.id)
    null -> null
}

/** Video surface for the mini player — same binding as PlayerScreen's
 *  VideoSurface (CONTRACT §7): ExoPlayer PlayerView, or VLC layout.
 *  The saved subtitle style (§4.56) is applied to the mini PlayerView
 *  too, so captions keep the user's size/colour when minimised. */
@Composable
private fun MiniVideoSurface(playerViewModel: PlayerViewModel) {
    val playback by playerViewModel.playbackState.collectAsState()
    val extrasViewModel: PlayerExtrasViewModel = hiltViewModel()
    val subtitleSize by extrasViewModel.subtitleSize.collectAsState()
    val subtitleColor by extrasViewModel.subtitleColor.collectAsState()
    val subtitlePosition by extrasViewModel.subtitlePosition.collectAsState()
    if (playback.engine == com.streammaster.tv.player.Engine.VLC) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    playerViewModel.playerManager.vlcVideoLayout = layout
                    playerViewModel.playerManager.vlcPlayer?.attachViews(layout, null, true, false)
                }
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerViewModel.playerManager.exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    applyMiniSubtitleStyle(subtitleView, subtitleSize, subtitleColor, subtitlePosition)
                }
            },
            update = { view ->
                view.player = playerViewModel.playerManager.exoPlayer
                applyMiniSubtitleStyle(view.subtitleView, subtitleSize, subtitleColor, subtitlePosition)
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    }
}

/** Same styling as PlayerScreen.applySubtitleStyle (§4.56) — kept local
 *  (not shared) so the overlay stays self-contained. */
private fun applyMiniSubtitleStyle(
    subtitleView: SubtitleView?,
    sizePercent: Int,
    colorHex: String,
    position: String
) {
    subtitleView?.let { sv ->
        val foreground = runCatching {
            android.graphics.Color.parseColor(colorHex)
        }.getOrDefault(android.graphics.Color.WHITE)
        sv.setStyle(
            CaptionStyleCompat(
                foreground,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                android.graphics.Color.BLACK,
                null
            )
        )
        sv.setFractionalTextSize(
            SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (sizePercent / 100f)
        )
        sv.setBottomPaddingFraction(if (position == "top") 0.85f else 0.08f)
    }
}

/** Same styling as the in-player PiP bar button (PlayerScreen). */
@Composable
private fun MiniPlayerButton(
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
