package com.streammaster.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.FavorisViewModel
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.SectionHeader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Favoris — favourite channels, films and series (prototype view-favoris:
 *  numbered sections with count badges, always visible, one empty message
 *  per section). */
@Composable
fun FavorisScreen(
    navController: NavHostController,
    viewModel: FavorisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Initial focus: the first tile of the first non-empty section. The
    // target only exists once the data has loaded — a LaunchedEffect(Unit)
    // fires before that and the request is lost — so the request is
    // re-tried (once) when the first section fills.
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    // §4.1 — return to the same item: PlayerScreen writes the played id
    // into this entry's savedStateHandle ("focusedId") when exiting the
    // player. The id is a channel / film / series id; the matching tile
    // is scrolled into view (section + row) and focused instead of the
    // default first tile. (Films/series go through the detail page, so a
    // return from the player restores there first — this covers the
    // direct channel case and any id that does arrive here.)
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val restoreId = savedStateHandle?.get<String>("focusedId")
    val hasRestoreTarget = restoreId != null &&
        (state.channels.any { it.id == restoreId } ||
            state.films.any { it.id == restoreId } ||
            state.series.any { it.id == restoreId })
    val itemFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun focusFor(id: String) = itemFocusRequesters.getOrPut(id) { FocusRequester() }
    val listState = rememberLazyListState()
    val channelsRowState = rememberLazyListState()
    val filmsRowState = rememberLazyListState()
    val seriesRowState = rememberLazyListState()
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(state.channels.isNotEmpty(), state.films.isNotEmpty(), state.series.isNotEmpty()) {
        if ((state.channels.isNotEmpty() || state.films.isNotEmpty() || state.series.isNotEmpty()) && !initialFocusDone && !hasRestoreTarget) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    LaunchedEffect(restoreId, state.channels, state.films, state.series) {
        if (restoreId == null || focusRestored) return@LaunchedEffect
        val inChannels = state.channels.indexOfFirst { it.id == restoreId }
        val inFilms = state.films.indexOfFirst { it.id == restoreId }
        val inSeries = state.series.indexOfFirst { it.id == restoreId }
        if (inChannels < 0 && inFilms < 0 && inSeries < 0) {
            // Lists loaded and the id is in none of them: nothing to
            // restore. (All lists empty = still loading: wait.)
            if (state.channels.isNotEmpty() || state.films.isNotEmpty() || state.series.isNotEmpty()) {
                focusRestored = true
                savedStateHandle?.remove<String>("focusedId")
            }
            return@LaunchedEffect
        }
        val (targetRowState, targetIndex) = when {
            inChannels >= 0 -> {
                listState.scrollToItem(0)
                channelsRowState to inChannels
            }
            inFilms >= 0 -> {
                listState.scrollToItem(1)
                filmsRowState to inFilms
            }
            else -> {
                listState.scrollToItem(2)
                seriesRowState to inSeries
            }
        }
        targetRowState.scrollToItem(targetIndex)
        // Wait for the target tile to actually be laid out instead of
        // guessing with a fixed delay (same pattern as EpgScreen).
        withTimeoutOrNull(1000) {
            snapshotFlow { targetRowState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }.first { it }
        }
        focusFor(restoreId).requestFocus()
        focusRestored = true
        initialFocusDone = true
        savedStateHandle?.remove<String>("focusedId")
    }
    // 2026-11-23: LazyColumn (was a plain Column) so the three sections scroll
    // vertically — with all sections full (1151dp) the old Column clipped the
    // series section even at 1080p, with no way to reach it by remote.
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item {
            FavSection {
                SectionHeader("1. ${stringResource(R.string.fav_section_channels)}", count = state.channels.size)
                if (state.channels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.fav_empty_channels),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldSoft,
                        modifier = Modifier.padding(4.dp, 14.dp)
                    )
                } else {
                    // L-25 — a remove button under each tile (toggleFavorite
                    // existed in the ViewModel but no UI called it). The
                    // row has focusRestorer so focus survives the removal.
                    LazyRow(state = channelsRowState, modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        itemsIndexed(state.channels) { index, channel ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FavChannelCard(
                                    channel = channel,
                                    modifier = Modifier.focusRequester(focusFor(channel.id))
                                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                ) { navController.navigate(Routes.player("live", channel.id)) }
                                GhostButton(text = stringResource(R.string.fav_remove)) {
                                    viewModel.toggleFavorite(ContentKind.LIVE, channel.id, false)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            FavSection {
                SectionHeader("2. ${stringResource(R.string.fav_section_films)}", count = state.films.size)
                if (state.films.isEmpty()) {
                    Text(
                        text = stringResource(R.string.fav_empty_films),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldSoft,
                        modifier = Modifier.padding(4.dp, 14.dp)
                    )
                } else {
                    LazyRow(state = filmsRowState, modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        itemsIndexed(state.films) { index, film ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PosterTile(
                                    title = film.title, hue = film.hue,
                                    code = film.title.take(2).uppercase(), isFavorite = true,
                                    quality = film.quality, rating = film.rating, year = film.year,
                                    unstable = film.isBroken,
                                    modifier = Modifier.focusRequester(focusFor(film.id))
                                        .then(if (index == 0 && state.channels.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier)
                                ) {
                                    navController.navigate(Routes.detail("film", film.id))
                                }
                                GhostButton(text = stringResource(R.string.fav_remove)) {
                                    viewModel.toggleFavorite(ContentKind.FILM, film.id, false)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            FavSection {
                SectionHeader("3. ${stringResource(R.string.fav_section_series)}", count = state.series.size)
                if (state.series.isEmpty()) {
                    Text(
                        text = stringResource(R.string.fav_empty_series),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldSoft,
                        modifier = Modifier.padding(4.dp, 14.dp)
                    )
                } else {
                    LazyRow(state = seriesRowState, modifier = Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        itemsIndexed(state.series) { index, series ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PosterTile(
                                    title = series.title, hue = series.hue,
                                    code = series.title.take(2).uppercase(), isFavorite = true,
                                    quality = series.quality, rating = series.rating,
                                    seasons = series.seasons, unstable = series.isBroken,
                                    modifier = Modifier.focusRequester(focusFor(series.id))
                                        .then(if (index == 0 && state.channels.isEmpty() && state.films.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier)
                                ) {
                                    navController.navigate(Routes.detail("series", series.id))
                                }
                                GhostButton(text = stringResource(R.string.fav_remove)) {
                                    viewModel.toggleFavorite(ContentKind.SERIES, series.id, false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Prototype `.fav-section`: gradient card, radius 26, padding 24x28, 2px border, shadow. */
@Composable
private fun FavSection(content: @Composable () -> Unit) {
    // R-B2: CardSurface with the section's own 26dp shape, gradient and
    // 2px border, so it carries the --card-shadow.
    CardSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        background = Brush.linearGradient(
            listOf(Color(0xDB1C1406), Color(0xEB0C0903))
        ),
        border = BorderStroke(2.dp, BorderGold)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            content()
        }
    }
}

/**
 * Prototype `favChanCardHTML`: a wide 420px channel card — logo/initials,
 * quality chip, current programme and mini progress bar (when the data is
 * there), instead of the vertical PosterTile.
 */
@Composable
private fun FavChannelCard(
    channel: Channel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // R-B2: CardSurface with the card's own 20dp shape, gradient and 2px
    // border, so it carries the --card-shadow; focus/click stay on the
    // outer surface so the 1.03 scale covers the whole card.
    CardSurface(
        modifier = modifier.width(420.dp).tvFocus().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        background = Brush.linearGradient(
            listOf(Color(0xF01C1406), Color(0xF50E0A03))
        ),
        border = BorderStroke(2.dp, Color(0x47FFE89E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box(
            modifier = Modifier.size(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GoldGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.code, fontSize = 24.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif, color = GoldDeep
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.customName ?: channel.name,
                    fontSize = 22.sp, fontWeight = FontWeight.Black, color = GoldWhite,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (channel.quality.isNotBlank()) {
                    Text(
                        text = channel.quality,
                        fontSize = 13.sp, fontWeight = FontWeight.Black, color = GoldWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x38E5C158))
                            .border(1.dp, GoldBright, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (channel.nowTitle.isNotBlank()) {
                Text(
                    text = channel.nowTitle,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldBright,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (channel.progress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xBF000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(channel.progress / 100f)
                            .height(6.dp)
                            .background(GoldGradient)
                    )
                }
            }
        }
        }
    }
}
