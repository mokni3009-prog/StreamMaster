package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.viewmodel.MyPlaylistViewModel
import com.streammaster.tv.ui.viewmodel.SeriesSort
import com.streammaster.tv.ui.viewmodel.SeriesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Series — grid + sort pills (default / rated / most seasons / A–Z).
 *
 * §4.62 — two named sections with a flèche (▸/▾) above the grid:
 * "My Playlist" first, then the active subscription by name. The first
 * click opens the bouquet dropdown, the second hides it; picking a
 * bouquet shows that bouquet's series. For My Playlist the filter is
 * real (item references); for the active subscription it shows the
 * subscription's series — the Series model carries no bouquetId, so a
 * per-bouquet split of the active catalogue is impossible until the
 * data model gains one. Until then the active-subscription bouquet
 * pills are display-only ([DisabledBouquetPill]: same look, no focus,
 * no click) so the remote never lands on a button that does nothing.
 *
 * §4.1 — returning from the player focuses the series that was playing
 * (PlayerScreen writes "focusedId" into this entry's savedStateHandle).
 */
@Composable
fun SeriesScreen(
    navController: NavHostController,
    viewModel: SeriesViewModel = hiltViewModel(),
    myPlaylistViewModel: MyPlaylistViewModel = hiltViewModel()
) {
    val series by viewModel.series.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val mySeriesBouquets by myPlaylistViewModel.seriesBouquets.collectAsState()
    val mySeriesAll by myPlaylistViewModel.mySeries.collectAsState()
    val mySeriesBouquet by myPlaylistViewModel.mySeriesBouquet.collectAsState()
    val activePlaylist by myPlaylistViewModel.activePlaylist.collectAsState()
    val activeSeriesBouquets by myPlaylistViewModel.activeSeriesBouquets.collectAsState()
    // §4.62 — one source browsed at a time (default: the active subscription).
    var mySectionOpen by remember { mutableStateOf(false) }
    var activeSectionOpen by remember { mutableStateOf(false) }
    var mySource by remember { mutableStateOf(false) }
    var myBouquetId by remember { mutableStateOf<Long?>(null) }
    // D-01: the old `activeBouquetId` was only ever assigned null — the
    // active-subscription "Tout" pill is simply `!mySource`.
    // Same sort as the active catalogue, applied to My Playlist series.
    val sortedMySeries = remember(mySeriesAll, sort) {
        when (sort) {
            SeriesSort.DEFAULT -> mySeriesAll
            SeriesSort.RATED -> mySeriesAll.sortedByDescending { it.rating }
            SeriesSort.SEASONS -> mySeriesAll.sortedByDescending { it.seasons }
            SeriesSort.AZ -> mySeriesAll.sortedBy { it.title }
        }
    }
    val displayedSeries = if (mySource) {
        if (myBouquetId == null) sortedMySeries
        else sortedMySeries.filter { mySeriesBouquet[it.id] == myBouquetId }
    } else series
    // Focus restore (§4.1).
    val gridState = rememberLazyGridState()
    val tileFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val focusedId = savedStateHandle?.get<String>("focusedId")
    var focusRestored by remember { mutableStateOf(false) }
    // Initial focus: the first sort pill — but only when there is no
    // restore target, so it never steals the focus back from a restore.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusedId == null) firstFocus.requestFocus() }
    LaunchedEffect(focusedId, displayedSeries, mySeriesAll) {
        if (focusedId == null || focusRestored) return@LaunchedEffect
        if (!mySource && mySeriesAll.any { it.id == focusedId }) {
            mySource = true
            myBouquetId = mySeriesBouquet[focusedId]
            return@LaunchedEffect // displayedSeries recomputes; the effect restarts
        }
        val idx = displayedSeries.indexOfFirst { it.id == focusedId }
        if (idx >= 0) {
            gridState.scrollToItem(idx)
            // Wait for the tile to actually be laid out instead of
            // guessing with a fixed delay.
            withTimeoutOrNull(1000) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == idx } }.first { it }
            }
            tileFocusRequesters[focusedId]?.requestFocus()
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
        } else if (series.isNotEmpty()) {
            // The active catalogue has loaded and the id is in neither
            // list: nothing to restore. (An empty My Playlist must not
            // block this conclusion — its list is empty by design for a
            // new user.) The ViewModels are retained in the back stack,
            // so on this return path both lists already hold their true
            // values.
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
            firstFocus.requestFocus()
        }
    }
    // M-01: a truly empty catalogue never satisfies the restore effect
    // above (its `series.isNotEmpty()` conclusion never fires) — after a
    // short wait, drop the stale id and focus the first sort pill so the
    // remote is never left with no focused element.
    LaunchedEffect(focusedId) {
        if (focusedId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (!focusRestored) {
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
            firstFocus.requestFocus()
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Section head with the "Multi-Saisons" count badge (prototype `.sec-head`).
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                stringResource(R.string.series_title),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.series_badge),
                fontSize = 17.sp, fontWeight = FontWeight.Black, color = GoldBright,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x2EE5C158))
                    .border(1.5.dp, BorderGold, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        ) {
            SortPill(stringResource(R.string.sort_default), sort == SeriesSort.DEFAULT, Modifier.focusRequester(firstFocus)) { viewModel.setSort(SeriesSort.DEFAULT) }
            SortPill(stringResource(R.string.sort_rated), sort == SeriesSort.RATED) { viewModel.setSort(SeriesSort.RATED) }
            SortPill(stringResource(R.string.sort_seasons), sort == SeriesSort.SEASONS) { viewModel.setSort(SeriesSort.SEASONS) }
            SortPill(stringResource(R.string.sort_az), sort == SeriesSort.AZ) { viewModel.setSort(SeriesSort.AZ) }
        }
        // §4.62 — named sections with a flèche.
        PlaylistSectionHeader(
            title = stringResource(R.string.my_playlist_name),
            count = mySeriesBouquets.size,
            expanded = mySectionOpen,
            modifier = Modifier.padding(bottom = 10.dp),
            onToggle = { mySectionOpen = !mySectionOpen }
        )
        if (mySectionOpen) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SortPill(stringResource(R.string.live_all), mySource && myBouquetId == null) {
                        mySource = true; myBouquetId = null
                    }
                }
                items(mySeriesBouquets) { bouquet ->
                    SortPill(bouquet.name, mySource && myBouquetId == bouquet.id) {
                        mySource = true; myBouquetId = bouquet.id
                    }
                }
            }
        }
        PlaylistSectionHeader(
            title = activePlaylist?.name.orEmpty(),
            count = activeSeriesBouquets.size,
            expanded = activeSectionOpen,
            modifier = Modifier.padding(bottom = 10.dp),
            onToggle = { activeSectionOpen = !activeSectionOpen }
        )
        if (activeSectionOpen) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SortPill(stringResource(R.string.live_all), !mySource) {
                        mySource = false
                    }
                }
                items(activeSeriesBouquets) { bouquet ->
                    // Display-only: no bouquetId on Series, so this pill
                    // cannot filter the grid (see DisabledBouquetPill).
                    DisabledBouquetPill(bouquet.name)
                }
            }
        }
        // M-01: the empty message used to show only for My Playlist —
        // an empty active catalogue showed a bare grid with no text.
        if (displayedSeries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.my_playlist_section_empty),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldSoft
                )
            }
        } else {
            // UI scaling (2026-11-23): Adaptive keeps 5 columns at the reference and drops to 3 on the smallest canvas.
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 232.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(displayedSeries) { item ->
                    PosterTile(
                        title = item.title, hue = item.hue, code = item.title.take(2).uppercase(),
                        isFavorite = item.isFavorite, progressPercent = item.recentProgress,
                        quality = item.quality, rating = item.rating,
                        seasons = item.seasons, unstable = item.isBroken, inGrid = true,
                        modifier = Modifier.focusRequester(
                            tileFocusRequesters.getOrPut(item.id) { FocusRequester() }
                        )
                    ) { navController.navigate(Routes.detail("series", item.id)) }
                }
            }
        }
    }
}
