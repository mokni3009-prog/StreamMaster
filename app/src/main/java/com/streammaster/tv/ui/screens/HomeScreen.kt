package com.streammaster.tv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.HomeViewModel
import com.streammaster.tv.ui.components.CardSurface
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
    // Initial focus (2026-11-23): the hero's first button, else the first popular poster, so the
    // remote (D-pad) moves from the first press. The target only exists
    // once the data has loaded — a LaunchedEffect(Unit) fires before that
    // and the request is lost — so the request is re-tried (once) when the
    // films arrive.
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.popularFilms.isNotEmpty()) {
        if (state.popularFilms.isNotEmpty() && !initialFocusDone) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    // UI scaling (2026-11-23): Hero (420dp) + stats + 4 poster rows
    // exceed the viewport even at the reference, and the Shell does not
    // scroll its content — so Home scrolls vertically (LazyRows inside
    // still scroll horizontally; D-pad focus scrolls into view).
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Expiry card at the top (no welcome text).
        CardSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
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
        // Flexible hero height (2026-11-23): the content (~412dp with a
        // 2-line title + 2-line description) overflows the 358dp left by
        // the fixed 420dp height on the narrow 1280x720 screen
        // (640x360dp) and is clipped from the top by Arrangement.Bottom.
        // B-S5: on the reference the height is the fixed 420dp of the
        // prototype (`.hero{height:420px;overflow:hidden}`) — heightIn
        // alone let a 3-line description grow the hero to ~444dp. The
        // flexible height stays only on a narrow canvas
        // (screenWidthDp < 700, the codebase's narrow definition).
        // The Box sits inside a verticalScroll, so its children must
        // not use fillMaxSize (unbounded max height): the backdrop uses
        // matchParentSize and the content column is content-sized,
        // bottom-aligned by the Box.
        if (heroFilm != null) {
            val narrow = LocalConfiguration.current.screenWidthDp < 700
            Box(
                modifier = Modifier.fillMaxWidth()
                    .then(if (narrow) Modifier.heightIn(min = 420.dp) else Modifier.height(420.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .border(2.dp, BorderStrong, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.BottomStart
            ) {
                HeroBackdrop(heroFilm.hue, Modifier.matchParentSize())
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 38.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Gold tag pill (prototype `.tag`).
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(GoldGradient)
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_star), contentDescription = null,
                            tint = GoldDeep, modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.hero_tag),
                            fontSize = 16.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp, color = GoldDeep
                        )
                    }
                    Text(
                        text = heroFilm.title,
                        fontSize = 68.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, color = GoldWhite,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                    )
                    // Meta line (prototype `.meta`) + quality box (`.q`).
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "★ ${formatRating(heroFilm.rating)}",
                            fontSize = 21.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                        Text(
                            text = heroFilm.year.toString(),
                            fontSize = 21.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                        Text(
                            text = formatDuration(heroFilm.durationMin),
                            fontSize = 21.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                        Text(
                            text = heroFilm.genre,
                            fontSize = 21.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                        if (heroFilm.quality.isNotBlank()) {
                            Text(
                                text = heroFilm.quality,
                                fontSize = 16.sp, fontWeight = FontWeight.Black, color = GoldWhite,
                                modifier = Modifier
                                    .border(2.dp, GoldBright, RoundedCornerShape(8.dp))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (heroFilm.description.isNotBlank()) {
                        Text(
                            text = heroFilm.description,
                            fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                            color = GoldWhite, lineHeight = 32.sp,
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .padding(top = 12.dp, bottom = 22.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HeroActionButton(
                            text = stringResource(R.string.hero_detail),
                            primary = true, big = true, iconRes = R.drawable.ic_play,
                            modifier = Modifier.focusRequester(firstFocus)
                        ) {
                            navController.navigate(Routes.detail("film", heroFilm.id))
                        }
                        HeroActionButton(
                            text = stringResource(R.string.nav_live),
                            primary = false, big = false, iconRes = R.drawable.ic_tv
                        ) {
                            navController.navigate(Routes.LIVE)
                        }
                    }
                }
            }
        }

        // Stats (prototype `.stat`: gradient, 1.5px border, 60x60 gold icon
        // box, Georgia 32 number, 15px uppercase label).
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple(R.string.stats_channels, state.channelCount, R.drawable.ic_tv),
                Triple(R.string.stats_films, state.filmCount, R.drawable.ic_film),
                Triple(R.string.stats_series, state.seriesCount, R.drawable.ic_layers),
                Triple(R.string.stats_favorites, state.favoriteCount, R.drawable.ic_heart)
            ).forEach { (label, count, iconRes) ->
                Row(
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xF0221807), Color(0xF0100B03))
                            )
                        )
                        .border(1.5.dp, BorderGold, RoundedCornerShape(22.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(60.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(GoldGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(iconRes), contentDescription = null,
                            tint = GoldDeep, modifier = Modifier.size(30.dp)
                        )
                    }
                    Column {
                        Text(
                            text = count.toString(), fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif, color = GoldWhite
                        )
                        Text(
                            text = stringResource(label).uppercase(),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp, color = GoldBright
                        )
                    }
                }
            }
        }

        // Dynamic Récemment vus — wide posters (prototype `.poster.wide` 360x206).
        if (state.recentFilms.isNotEmpty() || state.recentSeries.isNotEmpty()) {
            SectionHeader(
                stringResource(R.string.section_recent),
                stringResource(R.string.section_recent_badge)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                items(state.recentFilms) { film ->
                    PosterTile(
                        title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                        progressPercent = film.recentProgress, wide = true,
                        subtitle = film.remainingText.ifBlank { null },
                        quality = film.quality, rating = film.rating, year = film.year,
                        unstable = film.isBroken
                    ) { navController.navigate(Routes.detail("film", film.id)) }
                }
                items(state.recentSeries) { series ->
                    PosterTile(
                        title = series.title, hue = series.hue, code = series.title.take(2).uppercase(),
                        progressPercent = series.recentProgress, wide = true,
                        subtitle = series.remainingText.ifBlank {
                            stringResource(R.string.seasons_label, series.seasons)
                        },
                        quality = series.quality, rating = series.rating,
                        seasons = series.seasons, unstable = series.isBroken
                    ) { navController.navigate(Routes.detail("series", series.id)) }
                }
            }
        }

        // Films populaires
        SectionHeader(stringResource(R.string.section_popular_films))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(state.popularFilms) { film ->
                PosterTile(
                    title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                    isFavorite = film.isFavorite, progressPercent = film.recentProgress,
                    quality = film.quality, rating = film.rating, year = film.year,
                    unstable = film.isBroken,
                    modifier = if (heroFilm == null && film == state.popularFilms.firstOrNull()) Modifier.focusRequester(firstFocus) else Modifier
                ) { navController.navigate(Routes.detail("film", film.id)) }
            }
        }

        // Séries tendances
        SectionHeader(stringResource(R.string.section_trending_series))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(state.trendingSeries) { series ->
                PosterTile(
                    title = series.title, hue = series.hue, code = series.title.take(2).uppercase(),
                    isFavorite = series.isFavorite, progressPercent = series.recentProgress,
                    quality = series.quality, rating = series.rating,
                    seasons = series.seasons, unstable = series.isBroken
                ) { navController.navigate(Routes.detail("series", series.id)) }
            }
        }

        // Recommandé pour vous (2026-11-22): same genres as recently watched.
        if (state.recommendedFilms.isNotEmpty() || state.recommendedSeries.isNotEmpty()) {
            SectionHeader(stringResource(R.string.section_recommended))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                items(state.recommendedFilms) { film ->
                    PosterTile(
                        title = film.title, hue = film.hue, code = film.title.take(2).uppercase(),
                        isFavorite = film.isFavorite, progressPercent = film.recentProgress,
                        quality = film.quality, rating = film.rating, year = film.year,
                        unstable = film.isBroken
                    ) { navController.navigate(Routes.detail("film", film.id)) }
                }
                items(state.recommendedSeries) { series ->
                    PosterTile(
                        title = series.title, hue = series.hue, code = series.title.take(2).uppercase(),
                        isFavorite = series.isFavorite, progressPercent = series.recentProgress,
                        quality = series.quality, rating = series.rating,
                        seasons = series.seasons, unstable = series.isBroken
                    ) { navController.navigate(Routes.detail("series", series.id)) }
                }
            }
        }
    }
}

/**
 * Hero backdrop (H04): the prototype hero is an SVG scene (golden sun +
 * dark mountains, `.hero-art`, viewBox 1500×420) drawn literally here
 * with Canvas — solid sun disc + two mountain ridges — over the dark
 * background, under the left-to-right + bottom vignette of
 * `.hero::after`. The radial glow stays hue-driven (featured film);
 * the sun and ridges are the model's fixed gold, as in the prototype.
 */
@Composable
private fun HeroBackdrop(hue: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.background(Color(0xFF0C0802))) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.0f to Color.hsl(hue.toFloat(), 0.85f, 0.62f),
                    0.40f to Color.hsl(hue.toFloat(), 0.80f, 0.50f).copy(alpha = 0.55f),
                    0.75f to Color.hsl(hue.toFloat(), 0.80f, 0.35f).copy(alpha = 0.20f),
                    1.0f to Color.Transparent,
                    center = Offset(w * 0.72f, h * 0.48f),
                    radius = maxOf(w, h) * 0.62f
                )
            )
        )
        // Hero scene (model `.hero-art`, stream-master.html:1235-1250):
        // the model's coordinates, scaled x/1500*w and y/420*h.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sx = { x: Float -> x / 1500f * size.width }
            val sy = { y: Float -> y / 420f * size.height }
            // Sun disc: circle cx=1080 cy=200 r=80, #fff6cc (the r=300
            // glow circle is the radial gradient above).
            drawCircle(
                color = Color(0xFFFFF6CC),
                radius = 80f / 1500f * size.width,
                center = Offset(sx(1080f), sy(200f))
            )
            // Far ridge, #3b2906.
            drawPath(
                path = Path().apply {
                    moveTo(sx(460f), sy(420f))
                    lineTo(sx(730f), sy(240f))
                    lineTo(sx(890f), sy(315f))
                    lineTo(sx(1070f), sy(190f))
                    lineTo(sx(1250f), sy(295f))
                    lineTo(sx(1390f), sy(230f))
                    lineTo(sx(1500f), sy(300f))
                    lineTo(sx(1500f), sy(420f))
                    close()
                },
                color = Color(0xFF3B2906)
            )
            // Near ridge, #191103.
            drawPath(
                path = Path().apply {
                    moveTo(sx(280f), sy(420f))
                    lineTo(sx(560f), sy(315f))
                    lineTo(sx(740f), sy(375f))
                    lineTo(sx(950f), sy(285f))
                    lineTo(sx(1160f), sy(375f))
                    lineTo(sx(1360f), sy(325f))
                    lineTo(sx(1500f), sy(390f))
                    lineTo(sx(1500f), sy(420f))
                    close()
                },
                color = Color(0xFF191103)
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    0.0f to Color(0xF7070502),
                    0.44f to Color(0xDB070502),
                    0.78f to Color(0x2E070502),
                    1.0f to Color(0x00070502),
                    start = Offset(0f, 0f),
                    end = Offset(w, 0f)
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.58f to Color.Transparent,
                    1.0f to Color(0xEB070502)
                )
            )
        )
    }
}

/**
 * Prototype `.btn` (hero + detail actions): height 62dp (70dp big),
 * radius 31, 2px gold-bright border; primary = gold gradient with a
 * #fff8d6 border. Shared with DetailScreen (D06).
 */
@Composable
internal fun HeroActionButton(
    text: String,
    primary: Boolean,
    big: Boolean,
    iconRes: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(if (big) 70.dp else 62.dp)
            .clip(RoundedCornerShape(31.dp))
            .then(
                if (primary) Modifier.background(GoldGradient)
                else Modifier.background(Color(0xE0140E04))
            )
            .border(
                2.dp,
                if (primary) Color(0xFFFFF8D6) else GoldBright,
                RoundedCornerShape(31.dp)
            )
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = if (big) 40.dp else 30.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painterResource(iconRes), contentDescription = null,
                tint = if (primary) GoldDeep else GoldWhite,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = text,
            fontSize = if (big) 23.sp else 21.sp,
            fontWeight = FontWeight.Black,
            color = if (primary) GoldDeep else GoldWhite
        )
    }
}

/** "2 h 14 min" / "45 min" for the hero meta line. */
internal fun formatDuration(durationMin: Int): String {
    val hours = durationMin / 60
    val minutes = durationMin % 60
    return if (hours > 0) "$hours h %02d min".format(minutes) else "$minutes min"
}
