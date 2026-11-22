package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.HomeViewModel
import com.streammaster.tv.ui.components.SectionHeader

/**
 * Home — subscription expiry date at the top INSTEAD of a welcome text
 * (CONTRACT §6), then well-spaced sections: stats, dynamic Récemment vus,
 * Films populaires, Séries tendances.
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
        // Expiry card at the top (no welcome text).
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.expiryDate.isNotBlank())
                    stringResource(R.string.expiry_label, state.expiryDate)
                else stringResource(R.string.profile_status),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold
            )
            Text(text = stringResource(R.string.app_version), fontSize = 16.sp, color = TextMuted)
        }

        // Subscription-expiry alert (2026-11-22): warn when <= 7 days remain.
        val daysLeft = state.expiryDaysLeft
        if (daysLeft != null && daysLeft <= 7) {
            Text(
                text = if (daysLeft < 0) stringResource(R.string.expiry_expired)
                else stringResource(R.string.expiry_soon, daysLeft),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFC62828))
                    .padding(20.dp)
            )
        }

        // Stats
        SectionHeader(stringResource(R.string.section_stats))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                R.string.stats_channels to state.channelCount,
                R.string.stats_films to state.filmCount,
                R.string.stats_series to state.seriesCount,
                R.string.stats_playlists to state.playlistCount
            ).forEach { (label, count) ->
                Column(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        .padding(20.dp)
                ) {
                    Text(text = count.toString(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Gold)
                    Text(text = stringResource(label), fontSize = 17.sp, color = GoldWhite)
                }
            }
        }

        // Dynamic Récemment vus
        if (state.recentFilms.isNotEmpty() || state.recentSeries.isNotEmpty()) {
            SectionHeader(stringResource(R.string.section_recent))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.recentFilms) { film ->
                    PosterTile(
                        title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                        progressPercent = film.recentProgress
                    ) { navController.navigate(Routes.detail("film", film.id)) }
                }
                items(state.recentSeries) { series ->
                    PosterTile(
                        title = series.title, hue = series.hue, code = series.title.take(2).uppercase(),
                        progressPercent = series.recentProgress
                    ) { navController.navigate(Routes.detail("series", series.id)) }
                }
            }
        }

        // Films populaires
        SectionHeader(stringResource(R.string.section_popular_films))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(state.popularFilms) { film ->
                PosterTile(
                    title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                    isFavorite = film.isFavorite, progressPercent = film.recentProgress
                ) { navController.navigate(Routes.detail("film", film.id)) }
            }
        }

        // Séries tendances
        SectionHeader(stringResource(R.string.section_trending_series))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(state.trendingSeries) { series ->
                PosterTile(
                    title = series.title, hue = series.hue, code = series.title.take(2).uppercase(),
                    isFavorite = series.isFavorite, progressPercent = series.recentProgress
                ) { navController.navigate(Routes.detail("series", series.id)) }
            }
        }

        // Recommandé pour vous (2026-11-22): same genres as recently watched.
        if (state.recommendedFilms.isNotEmpty() || state.recommendedSeries.isNotEmpty()) {
            SectionHeader(stringResource(R.string.section_recommended))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.recommendedFilms) { film ->
                    PosterTile(
                        title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                        isFavorite = film.isFavorite, progressPercent = film.recentProgress
                    ) { navController.navigate(Routes.detail("film", film.id)) }
                }
                items(state.recommendedSeries) { series ->
                    PosterTile(
                        title = series.title, hue = series.hue, code = series.title.take(2).uppercase(),
                        isFavorite = series.isFavorite, progressPercent = series.recentProgress
                    ) { navController.navigate(Routes.detail("series", series.id)) }
                }
            }
        }
    }
}
