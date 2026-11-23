package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.FavorisViewModel
import com.streammaster.tv.ui.components.SectionHeader

/** Favoris — favourite channels, films and series (prototype view-favoris:
 *  no page title, numbered sections with count badges, always visible). */
@Composable
fun FavorisScreen(
    navController: NavHostController,
    viewModel: FavorisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val empty = state.channels.isEmpty() && state.films.isEmpty() && state.series.isEmpty()
    // Initial focus: the first tile of the first non-empty section.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        if (empty) {
            Text(text = stringResource(R.string.favoris_empty), fontSize = 18.sp, color = TextMuted)
            return@Column
        }
        SectionHeader("1. ${stringResource(R.string.fav_section_channels)} (${state.channels.size})")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(state.channels) { index, channel ->
                PosterTile(
                    title = channel.customName ?: channel.name, hue = channel.hue, code = channel.code,
                    isFavorite = true,
                    numberBadge = channel.customNum?.toString(),
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                ) { navController.navigate(Routes.player("live", channel.id)) }
            }
        }
        SectionHeader("2. ${stringResource(R.string.fav_section_films)} (${state.films.size})")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(state.films) { index, film ->
                PosterTile(title = film.title, hue = film.hue, code = film.title.take(2).uppercase(), isFavorite = true, modifier = if (index == 0 && state.channels.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier) {
                    navController.navigate(Routes.detail("film", film.id))
                }
            }
        }
        SectionHeader("3. ${stringResource(R.string.fav_section_series)} (${state.series.size})")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(state.series) { index, series ->
                PosterTile(title = series.title, hue = series.hue, code = series.title.take(2).uppercase(), isFavorite = true, modifier = if (index == 0 && state.channels.isEmpty() && state.films.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier) {
                    navController.navigate(Routes.detail("series", series.id))
                }
            }
        }
    }
}
