package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.HomeViewModel
import com.streammaster.tv.ui.components.SectionHeader
import java.util.Locale

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
    // Initial focus (2026-11-23): the hero's first button, else the first popular poster, so the
    // remote (D-pad) moves from the first press.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
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

        // Hero (prototype view-home): featured film of the evening, from the
        // highest-rated film; hue-based backdrop like PosterTile.
        val heroFilm = state.popularFilms.firstOrNull()
        if (heroFilm != null) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.hsv(heroFilm.hue.toFloat(), 0.55f, 0.32f),
                                Color(0xFF120D05)
                            )
                        )
                    )
                    .padding(28.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "★ " + stringResource(R.string.hero_tag),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold
                    )
                    Text(
                        text = heroFilm.title,
                        fontSize = 44.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, color = GoldWhite
                    )
                    Text(
                        text = "★ ${String.format(Locale.getDefault(), "%.1f", heroFilm.rating)}" +
                            "  •  ${heroFilm.year}" +
                            "  •  ${formatDuration(heroFilm.durationMin)}" +
                            "  •  ${heroFilm.genre}" +
                            "  •  ${heroFilm.quality}",
                        fontSize = 18.sp, color = GoldWhite
                    )
                    if (heroFilm.description.isNotBlank()) {
                        Text(
                            text = heroFilm.description,
                            fontSize = 17.sp, color = TextMuted,
                            maxLines = 2
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GoldButton(text = stringResource(R.string.hero_detail), modifier = Modifier.focusRequester(firstFocus)) {
                            navController.navigate(Routes.detail("film", heroFilm.id))
                        }
                        GhostButton(text = stringResource(R.string.nav_live)) {
                            navController.navigate(Routes.LIVE)
                        }
                    }
                }
            }
        }

        // Stats (prototype: 12 450 Chaînes / 8 320 Films / 2 140 Séries / 6 Mes Favoris).
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                R.string.stats_channels to state.channelCount,
                R.string.stats_films to state.filmCount,
                R.string.stats_series to state.seriesCount,
                R.string.stats_favorites to state.favoriteCount
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
            SectionHeader(
                stringResource(R.string.section_recent),
                stringResource(R.string.section_recent_badge)
            )
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
                    isFavorite = film.isFavorite, progressPercent = film.recentProgress,
                    modifier = if (heroFilm == null && film == state.popularFilms.firstOrNull()) Modifier.focusRequester(firstFocus) else Modifier
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

/** "2 h 14 min" / "45 min" for the hero meta line. */
private fun formatDuration(durationMin: Int): String {
    val hours = durationMin / 60
    val minutes = durationMin % 60
    return if (hours > 0) "$hours h %02d min".format(minutes) else "$minutes min"
}
