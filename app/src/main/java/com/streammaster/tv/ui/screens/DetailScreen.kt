package com.streammaster.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.util.TrailerOpener
import com.streammaster.tv.ui.viewmodel.DetailViewModel
import com.streammaster.tv.ui.components.GoldButton

/**
 * Detail — film/series detail (full-screen, no sidebar): poster, facts,
 * cast, episodes list with seasons, play button, favourite toggle.
 */
@Composable
fun DetailScreen(
    navController: NavHostController,
    kind: String,
    id: String,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isWatchLater by viewModel.isWatchLater.collectAsState()
    val context = LocalContext.current
    // Initial focus: the Play button (first action).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    LaunchedEffect(kind, id) { viewModel.load(kind, id) }
    // One-shot download notice as a toast.
    LaunchedEffect(state.downloadNoticeRes) {
        val res = state.downloadNoticeRes ?: return@LaunchedEffect
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
        viewModel.consumeDownloadNotice()
    }

    val title = state.film?.title ?: state.series?.title ?: ""
    val hue = state.film?.hue ?: state.series?.hue ?: 44
    val description = state.film?.description ?: state.series?.description ?: ""
    val rating = state.film?.rating ?: state.series?.rating ?: 0.0
    val quality = state.film?.quality ?: state.series?.quality ?: ""
    val isFavorite = state.film?.isFavorite ?: state.series?.isFavorite ?: false

    ThemedScreen {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    // Poster
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.width(300.dp).height(420.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.hsv(hue.toFloat(), 0.55f, 0.32f)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(text = title.take(2).uppercase(), fontSize = 72.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        Text(text = title, fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
                        Text(text = "★ $rating  •  $quality", fontSize = 18.sp, color = Gold)
                        if (state.film != null) {
                            Text(text = "${stringResource(R.string.year_label)}: ${state.film!!.year}  •  ${stringResource(R.string.genre_label)}: ${state.film!!.genre}  •  ${stringResource(R.string.duration_label)}: ${state.film!!.durationMin} min", fontSize = 17.sp, color = TextMuted)
                        }
                        if (state.series != null) {
                            Text(text = "${stringResource(R.string.seasons_label, state.series!!.seasons)}  •  ${stringResource(R.string.genre_label)}: ${state.series!!.genre}", fontSize = 17.sp, color = TextMuted)
                        }
                        Text(text = description, fontSize = 18.sp, color = GoldWhite)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GoldButton(text = stringResource(R.string.detail_play), modifier = Modifier.focusRequester(firstFocus)) {
                                navController.navigate(Routes.player(kind, id))
                            }
                            GhostButton(text = if (isFavorite) "♥ ${stringResource(R.string.favorite)}" else stringResource(R.string.favorite)) {
                                viewModel.toggleFavorite()
                            }
                            GhostButton(text = if (isWatchLater) "✓ ${stringResource(R.string.watch_later)}" else stringResource(R.string.watch_later)) {
                                viewModel.toggleWatchLater()
                            }
                            // Trailer (2026-11-22): provider YouTube link when
                            // available, else a YouTube search for the title.
                            GhostButton(text = stringResource(R.string.trailer)) {
                                TrailerOpener.open(
                                    context, title,
                                    state.film?.trailerUrl ?: state.series?.trailerUrl
                                )
                            }
                            // Offline download (2026-11-22): films only here;
                            // series download per episode below.
                            if (state.film != null) {
                                val dlLabel = when (state.downloadStatus) {
                                    DownloadStatus.DONE -> "✓ ${stringResource(R.string.downloaded)}"
                                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> stringResource(R.string.download_downloading)
                                    DownloadStatus.PAUSED -> stringResource(R.string.download_paused)
                                    DownloadStatus.ERROR -> stringResource(R.string.download_retry)
                                    null -> stringResource(R.string.download)
                                }
                                GhostButton(text = dlLabel) { viewModel.downloadFilm() }
                            }
                            GhostButton(text = stringResource(R.string.back)) { navController.popBackStack() }
                        }
                    }
                }
            }
            if (state.cast.isNotEmpty()) {
                item {
                    Text(text = stringResource(R.string.detail_cast), fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(top = 12.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.cast.forEach { (name, role) ->
                            Column(
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardSurface).padding(14.dp)
                            ) {
                                Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                                Text(text = role, fontSize = 14.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
            if (state.series != null) {
                item {
                    Text(text = stringResource(R.string.detail_episodes), fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(top = 12.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.seasons.forEach { season ->
                            SortPill(stringResource(R.string.detail_season, season), state.selectedSeason == season) {
                                viewModel.selectSeason(season)
                            }
                        }
                    }
                }
                items(state.episodes) { episode ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .tvFocus()
                            .clickable { navController.navigate(Routes.player("series", id)) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "E${episode.number} — ${episode.title}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite, modifier = Modifier.weight(1f))
                        Text(text = "${episode.durationMin} min", fontSize = 16.sp, color = TextMuted)
                        // Per-episode offline download (2026-11-22).
                        GhostButton(text = "↓") { viewModel.downloadEpisode(episode) }
                    }
                }
            }
        }
    }
}
