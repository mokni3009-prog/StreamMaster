package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.streammaster.tv.ui.viewmodel.FilmSort
import com.streammaster.tv.ui.viewmodel.FilmsViewModel

/** Films — grid + sort pills (default / recent / rated / A–Z). */
@Composable
fun FilmsScreen(
    navController: NavHostController,
    viewModel: FilmsViewModel = hiltViewModel()
) {
    val films by viewModel.films.collectAsState()
    val sort by viewModel.sort.collectAsState()
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
        LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            items(films) { film ->
                PosterTile(
                    title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                    isFavorite = film.isFavorite, progressPercent = film.recentProgress,
                    quality = film.quality, rating = film.rating, year = film.year,
                    unstable = film.isBroken, inGrid = true
                ) { navController.navigate(Routes.detail("film", film.id)) }
            }
        }
    }
}
