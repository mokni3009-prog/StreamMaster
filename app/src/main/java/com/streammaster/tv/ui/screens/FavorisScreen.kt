package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.FavorisViewModel
import com.streammaster.tv.ui.components.SectionHeader

/** Favoris — favourite channels, films and series. */
@Composable
fun FavorisScreen(
    navController: NavHostController,
    viewModel: FavorisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val empty = state.channels.isEmpty() && state.films.isEmpty() && state.series.isEmpty()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text(text = stringResource(R.string.favoris_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
        if (empty) {
            Text(text = stringResource(R.string.favoris_empty), fontSize = 18.sp, color = TextMuted)
            return@Column
        }
        if (state.channels.isNotEmpty()) {
            SectionHeader(stringResource(R.string.channels))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.channels) { channel ->
                    PosterTile(
                        title = channel.customName ?: channel.name, hue = channel.hue, code = channel.code,
                        isFavorite = true,
                        numberBadge = channel.customNum?.toString()
                    ) { navController.navigate(Routes.player("live", channel.id)) }
                }
            }
        }
        if (state.films.isNotEmpty()) {
            SectionHeader(stringResource(R.string.stats_films))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.films) { film ->
                    PosterTile(title = film.title, hue = film.hue, code = film.title.take(2).uppercase(), isFavorite = true) {
                        navController.navigate(Routes.detail("film", film.id))
                    }
                }
            }
        }
        if (state.series.isNotEmpty()) {
            SectionHeader(stringResource(R.string.stats_series))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.series) { series ->
                    PosterTile(title = series.title, hue = series.hue, code = series.title.take(2).uppercase(), isFavorite = true) {
                        navController.navigate(Routes.detail("series", series.id))
                    }
                }
            }
        }
    }
}
