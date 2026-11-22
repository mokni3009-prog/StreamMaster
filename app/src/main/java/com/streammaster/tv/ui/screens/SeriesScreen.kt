package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.viewmodel.SeriesSort
import com.streammaster.tv.ui.viewmodel.SeriesViewModel

/** Series — grid + sort pills (default / rated / most seasons / A–Z). */
@Composable
fun SeriesScreen(
    navController: NavHostController,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val series by viewModel.series.collectAsState()
    val sort by viewModel.sort.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = stringResource(R.string.series_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            SortPill(stringResource(R.string.sort_default), sort == SeriesSort.DEFAULT) { viewModel.setSort(SeriesSort.DEFAULT) }
            SortPill(stringResource(R.string.sort_rated), sort == SeriesSort.RATED) { viewModel.setSort(SeriesSort.RATED) }
            SortPill(stringResource(R.string.sort_seasons), sort == SeriesSort.SEASONS) { viewModel.setSort(SeriesSort.SEASONS) }
            SortPill(stringResource(R.string.sort_az), sort == SeriesSort.AZ) { viewModel.setSort(SeriesSort.AZ) }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            items(series) { item ->
                PosterTile(
                    title = item.title, hue = item.hue, code = item.title.take(2).uppercase(),
                    isFavorite = item.isFavorite, progressPercent = item.recentProgress
                ) { navController.navigate(Routes.detail("series", item.id)) }
            }
        }
    }
}
