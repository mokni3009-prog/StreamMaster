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
import com.streammaster.tv.ui.viewmodel.FilmSort
import com.streammaster.tv.ui.viewmodel.FilmsViewModel
import com.streammaster.tv.ui.viewmodel.MyPlaylistViewModel
import kotlinx.coroutines.delay

/** Films — grid + sort pills (default / recent / rated / A–Z).
 *
 * §4.62 — two named sections with a flèche (▸/▾) above the grid:
 * "My Playlist" first, then the active subscription by name. The first
 * click opens the bouquet dropdown, the second hides it; picking a
 * bouquet shows that bouquet's films. For My Playlist the filter is
 * real (item references); for the active subscription it shows the
 * subscription's films — the Film model carries no bouquetId, so a
 * per-bouquet split of the active catalogue is impossible until the
 * data model gains one.
 *
 * §4.1 — returning from the player focuses the film that was playing
 * (PlayerScreen writes "focusedId" into this entry's savedStateHandle).
 */
@Composable
fun FilmsScreen(
    navController: NavHostController,
    viewModel: FilmsViewModel = hiltViewModel(),
    myPlaylistViewModel: MyPlaylistViewModel = hiltViewModel()
) {
    val films by viewModel.films.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val myFilmBouquets by myPlaylistViewModel.filmBouquets.collectAsState()
    val myFilmsAll by myPlaylistViewModel.myFilms.collectAsState()
    val myFilmBouquet by myPlaylistViewModel.myFilmBouquet.collectAsState()
    val activePlaylist by myPlaylistViewModel.activePlaylist.collectAsState()
    val activeFilmBouquets by myPlaylistViewModel.activeFilmBouquets.collectAsState()
    // §4.62 — one source browsed at a time (default: the active subscription).
    var mySectionOpen by remember { mutableStateOf(false) }
    var activeSectionOpen by remember { mutableStateOf(false) }
    var mySource by remember { mutableStateOf(false) }
    var myBouquetId by remember { mutableStateOf<Long?>(null) }
    var activeBouquetId by remember { mutableStateOf<String?>(null) }
    // Same sort as the active catalogue, applied to My Playlist films.
    val sortedMyFilms = remember(myFilmsAll, sort) {
        when (sort) {
            FilmSort.DEFAULT -> myFilmsAll
            FilmSort.RECENT -> myFilmsAll.sortedByDescending { it.year }
            FilmSort.RATED -> myFilmsAll.sortedByDescending { it.rating }
            FilmSort.AZ -> myFilmsAll.sortedBy { it.title }
        }
    }
    val displayedFilms = if (mySource) {
        if (myBouquetId == null) sortedMyFilms
        else sortedMyFilms.filter { myFilmBouquet[it.id] == myBouquetId }
    } else films
    // Focus restore (§4.1).
    val gridState = rememberLazyGridState()
    val tileFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val focusedId = savedStateHandle?.get<String>("focusedId")
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(focusedId, displayedFilms, myFilmsAll) {
        if (focusedId == null || focusRestored) return@LaunchedEffect
        if (!mySource && myFilmsAll.any { it.id == focusedId }) {
            mySource = true
            myBouquetId = myFilmBouquet[focusedId]
            return@LaunchedEffect // displayedFilms recomputes; the effect restarts
        }
        val idx = displayedFilms.indexOfFirst { it.id == focusedId }
        if (idx >= 0) {
            gridState.scrollToItem(idx)
            delay(120)
            tileFocusRequesters[focusedId]?.requestFocus()
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
        } else if (films.isNotEmpty() && myFilmsAll.isNotEmpty()) {
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
        }
    }
    // Initial focus: the first sort pill.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(modifier = Modifier.fillMaxSize()) {
        // Section head with the "Qualité 4K & FHD" count badge (prototype `.sec-head`).
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                stringResource(R.string.films_title),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.films_badge),
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
            SortPill(stringResource(R.string.sort_default), sort == FilmSort.DEFAULT, Modifier.focusRequester(firstFocus)) { viewModel.setSort(FilmSort.DEFAULT) }
            SortPill(stringResource(R.string.sort_recent), sort == FilmSort.RECENT) { viewModel.setSort(FilmSort.RECENT) }
            SortPill(stringResource(R.string.sort_rated), sort == FilmSort.RATED) { viewModel.setSort(FilmSort.RATED) }
            SortPill(stringResource(R.string.sort_az), sort == FilmSort.AZ) { viewModel.setSort(FilmSort.AZ) }
        }
        // §4.62 — named sections with a flèche.
        PlaylistSectionHeader(
            title = stringResource(R.string.my_playlist_name),
            count = myFilmBouquets.size,
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
                items(myFilmBouquets) { bouquet ->
                    SortPill(bouquet.name, mySource && myBouquetId == bouquet.id) {
                        mySource = true; myBouquetId = bouquet.id
                    }
                }
            }
        }
        PlaylistSectionHeader(
            title = activePlaylist?.name.orEmpty(),
            count = activeFilmBouquets.size,
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
                    SortPill(stringResource(R.string.live_all), !mySource && activeBouquetId == null) {
                        mySource = false; activeBouquetId = null
                    }
                }
                items(activeFilmBouquets) { bouquet ->
                    SortPill(bouquet.name, !mySource && activeBouquetId == bouquet.id) {
                        mySource = false; activeBouquetId = bouquet.id
                    }
                }
            }
        }
        if (displayedFilms.isEmpty() && mySource) {
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
                items(displayedFilms) { film ->
                    PosterTile(
                        title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                        isFavorite = film.isFavorite, progressPercent = film.recentProgress,
                        quality = film.quality, rating = film.rating, year = film.year,
                        unstable = film.isBroken, inGrid = true,
                        modifier = Modifier.focusRequester(
                            tileFocusRequesters.getOrPut(film.id) { FocusRequester() }
                        )
                    ) { navController.navigate(Routes.detail("film", film.id)) }
                }
            }
        }
    }
}
