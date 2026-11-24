package com.streammaster.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.subtitles.SubtitleDownloadButton
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.util.TrailerOpener
import com.streammaster.tv.ui.viewmodel.DetailViewModel

/**
 * Detail — film/series detail (full-screen, no sidebar): poster, facts,
 * cast, episodes list with seasons, play button, favourite toggle.
 * Literal to the prototype `.dt-*` (topbar, 320x470 poster, 60px title,
 * facts row, 180px cast cards, 2-column episode grid, 50px season pills).
 */
@Composable
fun DetailScreen(
    navController: NavHostController,
    kind: String,
    id: String,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isWatchLater by viewModel.isWatchLater.collectAsState()
    val context = LocalContext.current
    // Focus restore (§4.1, 2026-11-23): PlayerScreen writes "focusedId"
    // into this entry's savedStateHandle when leaving the player; coming
    // back, the focus returns to the Lecture button (firstFocus) — the
    // action the user came from. The initial focus waits for the data:
    // requesting it before load risks losing it if the composition
    // changes (e.g. the Reprendre button appearing later).
    val firstFocus = remember { FocusRequester() }
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val focusedId = savedStateHandle?.get<String>("focusedId")
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.film, state.series) {
        if (initialFocusDone) return@LaunchedEffect
        if (state.film == null && state.series == null) return@LaunchedEffect
        firstFocus.requestFocus()
        initialFocusDone = true
        if (focusedId != null) savedStateHandle?.remove<String>("focusedId")
    }
    LaunchedEffect(kind, id) { viewModel.load(kind, id) }
    // Leaving Detail writes this item's id for the parent screen
    // (Home / Films / Series / Search) to restore onto — same pattern
    // as PlayerScreen.exitPlayer(). Both the Retour pill and the
    // system/remote Back key go through here.
    fun exitDetail() {
        navController.previousBackStackEntry?.savedStateHandle?.set("focusedId", id)
        navController.popBackStack()
    }
    BackHandler { exitDetail() }
    // One-shot download notice as a toast.
    LaunchedEffect(state.downloadNoticeRes) {
        val res = state.downloadNoticeRes ?: return@LaunchedEffect
        showToast(context, context.getString(res))
        viewModel.consumeDownloadNotice()
    }

    val title = state.film?.title ?: state.series?.title ?: ""
    val hue = state.film?.hue ?: state.series?.hue ?: 44
    val description = state.film?.description ?: state.series?.description ?: ""
    val rating = state.film?.rating ?: state.series?.rating ?: 0.0
    val quality = state.film?.quality ?: state.series?.quality ?: ""
    val isFavorite = state.film?.isFavorite ?: state.series?.isFavorite ?: false
    val isFilm = state.film != null
    val genre = state.film?.genre ?: state.series?.genre ?: ""

    ThemedScreen {
        Box(modifier = Modifier.fillMaxSize()) {
            DetailBackdrop(hue, Modifier.fillMaxSize())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 44.dp, end = 44.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // D01 — top bar: "Retour" pill + gold type badge.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.height(54.dp)
                                .clip(RoundedCornerShape(27.dp))
                                .background(Color(0xE60F0B04))
                                .border(2.dp, BorderStrong, RoundedCornerShape(27.dp))
                                .tvFocus()
                                .clickable { exitDetail() }
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_left),
                                contentDescription = null, tint = GoldWhite,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(R.string.back),
                                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                            )
                        }
                        Text(
                            text = stringResource(
                                if (isFilm) R.string.detail_type_film else R.string.detail_type_series
                            ),
                            fontSize = 15.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp, color = GoldDeep,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(GoldGradient)
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                // D02-D07 — poster + info.
                // R-01: never compose the detail body before the data
                // arrives — the first frame has film == null && series
                // == null and the meta/facts rows below dereference them.
                item {
                    val film = state.film
                    val series = state.series
                    if (film == null && series == null) return@item
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(44.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    ) {
                        // Poster (prototype `.dt-poster`): 320x470, radius 24,
                        // 2.5px border, two rings, shade, quality badge, title inside.
                        Box(
                            modifier = Modifier.width(320.dp).height(470.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .border(2.5.dp, BorderStrong, RoundedCornerShape(24.dp))
                        ) {
                            HueBackdrop(hue, Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 90.dp, y = (-60).dp)
                                    .size(290.dp)
                                    .border(2.dp, Color(0x73FFE89E), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = (-60).dp, y = 150.dp)
                                    .size(180.dp)
                                    .border(2.dp, Color(0x73FFE89E).copy(alpha = 0.6f), CircleShape)
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.40f to Color(0xFF060401).copy(alpha = 0.10f),
                                        1.0f to Color(0xFF060401).copy(alpha = 0.95f)
                                    )
                                )
                            )
                            Text(
                                text = title.take(2).uppercase(),
                                fontSize = 72.sp, fontWeight = FontWeight.Black, color = GoldWhite,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            if (quality.isNotBlank()) {
                                Text(
                                    text = quality,
                                    fontSize = 15.sp, fontWeight = FontWeight.Black, color = GoldDeep,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GoldGradient)
                                        .padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                            Text(
                                text = title,
                                fontSize = 26.sp, fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Serif, color = GoldWhite,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                            )
                        }
                        // Info column.
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).padding(top = 10.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 60.sp, fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Serif, color = GoldWhite,
                                lineHeight = 63.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            // Meta line (prototype `.dt-meta`) + quality box (`.q`).
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "★ ${formatRating(rating)}",
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                                )
                                if (film != null) {
                                    Text(
                                        text = "${film.year}",
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldBright
                                    )
                                    Text(
                                        text = formatDuration(film.durationMin),
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldBright
                                    )
                                } else if (series != null) {
                                    Text(
                                        text = stringResource(
                                            R.string.seasons_label,
                                            series.seasons
                                        ),
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldBright
                                    )
                                }
                                if (genre.isNotBlank()) {
                                    Text(
                                        text = genre,
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldBright
                                    )
                                }
                                if (quality.isNotBlank()) {
                                    Text(
                                        text = quality,
                                        fontSize = 16.sp, fontWeight = FontWeight.Black, color = GoldWhite,
                                        modifier = Modifier
                                            .border(2.dp, GoldBright, RoundedCornerShape(8.dp))
                                            .background(Color(0x8C000000))
                                            .padding(horizontal = 12.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = description,
                                fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                                color = GoldWhite, lineHeight = 34.sp,
                                modifier = Modifier
                                    .widthIn(max = 1000.dp)
                                    .padding(bottom = 14.dp)
                            )
                            // Actions (prototype `.dt-actions`): Lecture 70dp +
                            // Favori + Plus tard (+ Trailer / Télécharger, approved).
                            // 2026-11-23: LazyRow (was a plain Row) so the action buttons
                            // scroll horizontally instead of overflowing (~213dp on 1280)
                            // and the focused button is brought into view by the remote.
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                item {
                                    HeroActionButton(
                                        text = stringResource(R.string.detail_play),
                                        primary = true, big = true, iconRes = R.drawable.ic_play,
                                        modifier = Modifier.focusRequester(firstFocus)
                                    ) {
                                        navController.navigate(Routes.player(kind, id))
                                    }
                                }
                                // §4.1 — "Reprendre" next to "Lecture" when a
                                // saved progress point exists. Films resume
                                // from recentProgress in the player; series
                                // episode-level resume needs the engine's
                                // resumeInfoFor (see report).
                                item {
                                    if (state.hasResume) {
                                        HeroActionButton(
                                            text = stringResource(
                                                R.string.detail_resume_percent,
                                                state.resumePercent
                                            ),
                                            primary = false, big = false,
                                            iconRes = R.drawable.ic_play
                                        ) {
                                            navController.navigate(Routes.player(kind, id))
                                        }
                                    }
                                }
                                item {
                                    HeroActionButton(
                                        text = if (isFavorite) stringResource(R.string.favorite_remove)
                                        else stringResource(R.string.favorite),
                                        primary = false, big = false, iconRes = R.drawable.ic_heart
                                    ) {
                                        viewModel.toggleFavorite()
                                    }
                                }
                                item {
                                    HeroActionButton(
                                        // R-S3: ic_check icon when active instead
                                        // of a "✓ " text prefix.
                                        text = stringResource(R.string.watch_later),
                                        primary = false, big = false,
                                        iconRes = if (isWatchLater) R.drawable.ic_check else R.drawable.ic_plus
                                    ) {
                                        viewModel.toggleWatchLater()
                                    }
                                }
                                // Trailer (2026-11-22): provider YouTube link when
                                // available, else a YouTube search for the title.
                                item {
                                    HeroActionButton(
                                        text = stringResource(R.string.trailer),
                                        primary = false, big = false, iconRes = null
                                    ) {
                                        TrailerOpener.open(
                                            context, title,
                                            state.film?.trailerUrl ?: state.series?.trailerUrl
                                        )
                                    }
                                }
                                // Offline download (2026-11-22): films only here;
                                // series download per episode below.
                                item {
                                    if (state.film != null) {
                                        val dlLabel = when (state.downloadStatus) {
                                            DownloadStatus.DONE -> stringResource(R.string.downloaded)
                                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> stringResource(R.string.download_downloading)
                                            DownloadStatus.PAUSED -> stringResource(R.string.download_paused)
                                            DownloadStatus.ERROR -> stringResource(R.string.download_retry)
                                            null -> stringResource(R.string.download)
                                        }
                                        HeroActionButton(
                                            text = dlLabel,
                                            primary = false, big = false,
                                            // R-S3: ic_check when downloaded.
                                            iconRes = if (state.downloadStatus == DownloadStatus.DONE) R.drawable.ic_check else null
                                        ) { viewModel.downloadFilm() }
                                    }
                                }
                                // §4.48 — real subtitle download button
                                // (data/subtitles); takes itemType as a
                                // String ("film" | "series") and a nullable year.
                                item {
                                    SubtitleDownloadButton(
                                        itemType = if (isFilm) "film" else "series",
                                        itemId = id,
                                        title = title,
                                        year = state.film?.year
                                    )
                                }
                            }
                            // Facts (prototype `.dt-facts`): label 14sp uppercase
                            // + value 19sp, gold top border. Only facts the data
                            // carries: year/duration/genre/quality/rating
                            // (the prototype's "Réalisateur" has no data source here).
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0x33FFE89E))
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                                    modifier = Modifier.padding(top = 18.dp)
                                ) {
                                    if (film != null) {
                                        DetailFact(
                                            stringResource(R.string.year_label),
                                            "${film.year}"
                                        )
                                        DetailFact(
                                            stringResource(R.string.duration_label),
                                            formatDuration(film.durationMin)
                                        )
                                        DetailFact(
                                            stringResource(R.string.genre_label),
                                            genre
                                        )
                                    } else if (series != null) {
                                        DetailFact(
                                            stringResource(R.string.fact_seasons),
                                            stringResource(
                                                R.string.seasons_label,
                                                series.seasons
                                            )
                                        )
                                        DetailFact(
                                            stringResource(R.string.genre_label),
                                            genre
                                        )
                                        DetailFact(
                                            stringResource(R.string.quality_label),
                                            quality
                                        )
                                    }
                                    // §4.49 — director, only when the
                                    // provider actually supplied one.
                                    if (state.director != null) {
                                        DetailFact(
                                            stringResource(R.string.detail_director),
                                            state.director!!
                                        )
                                    }
                                    DetailFact(
                                        stringResource(R.string.fact_rating),
                                        "★ ${formatRating(rating)} / 10"
                                    )
                                }
                            }
                        }
                    }
                }

                // §4.53 — user star rating (1-5), next to the provider
                // rating shown above in the facts row.
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        SectionHeader(stringResource(R.string.detail_your_rating))
                        StarRatingInput(stars = state.userStars) { viewModel.setUserRating(it) }
                        Text(
                            text = if (state.userStars > 0) {
                                stringResource(R.string.detail_your_rating_set, state.userStars)
                            } else {
                                stringResource(R.string.detail_rate_hint)
                            },
                            fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = GoldSoft
                        )
                    }
                }

                // D08 — cast (prototype `.cast-card`: 180px card, 96px round avatar).
                // §4.49 — REAL provider names only (no roles: the provider
                // supplies none). Tapping an actor shows their other works.
                // When the provider supplies no credits at all, the section
                // says so instead of inventing names.
                if (state.creditsLoaded) {
                    item {
                        val castShown = state.cast.take(6)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            SectionHeader(
                                stringResource(R.string.detail_cast_title),
                                count = castShown.size
                            )
                            if (castShown.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    items(castShown) { name ->
                                        CastCard(
                                            name = name,
                                            selected = name == state.selectedActor
                                        ) { viewModel.selectActor(name) }
                                    }
                                }
                            } else if (state.director == null) {
                                Text(
                                    text = stringResource(R.string.detail_cast_unavailable),
                                    fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                    color = GoldSoft
                                )
                            }
                        }
                    }
                }

                // §4.49 — similar titles: same genre from the catalog.
                if (state.similarFilms.isNotEmpty() || state.similarSeries.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            SectionHeader(
                                stringResource(R.string.detail_similar_title),
                                count = state.similarFilms.size + state.similarSeries.size
                            )
                            if (state.similarFilms.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    items(state.similarFilms) { film ->
                                        PosterTile(
                                            title = film.title,
                                            hue = film.hue,
                                            code = film.title.take(2).uppercase(),
                                            progressPercent = film.recentProgress,
                                            isFavorite = film.isFavorite,
                                            quality = film.quality,
                                            rating = film.rating,
                                            year = film.year
                                        ) {
                                            navController.navigate(Routes.detail("film", film.id))
                                        }
                                    }
                                }
                            }
                            if (state.similarSeries.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    items(state.similarSeries) { series ->
                                        PosterTile(
                                            title = series.title,
                                            hue = series.hue,
                                            code = series.title.take(2).uppercase(),
                                            progressPercent = series.recentProgress,
                                            isFavorite = series.isFavorite,
                                            quality = series.quality,
                                            rating = series.rating,
                                            seasons = series.seasons
                                        ) {
                                            navController.navigate(Routes.detail("series", series.id))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // §4.49 — the tapped actor's other works in the catalog.
                // Tapping the same actor again closes this section.
                if (state.selectedActor != null) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            SectionHeader(
                                stringResource(
                                    R.string.detail_actor_works_title,
                                    state.selectedActor!!
                                )
                            )
                            when {
                                state.actorWorksLoading &&
                                    state.actorWorksFilms.isEmpty() &&
                                    state.actorWorksSeries.isEmpty() -> {
                                    Text(
                                        text = stringResource(R.string.detail_actor_works_loading),
                                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                        color = GoldSoft
                                    )
                                }
                                state.actorWorksFilms.isEmpty() &&
                                    state.actorWorksSeries.isEmpty() -> {
                                    Text(
                                        text = stringResource(R.string.detail_actor_works_empty),
                                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                                        color = GoldSoft
                                    )
                                }
                                else -> {
                                    if (state.actorWorksFilms.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                            items(state.actorWorksFilms) { film ->
                                                PosterTile(
                                                    title = film.title,
                                                    hue = film.hue,
                                                    code = film.title.take(2).uppercase(),
                                                    progressPercent = film.recentProgress,
                                                    isFavorite = film.isFavorite,
                                                    quality = film.quality,
                                                    rating = film.rating,
                                                    year = film.year
                                                ) {
                                                    navController.navigate(Routes.detail("film", film.id))
                                                }
                                            }
                                        }
                                    }
                                    if (state.actorWorksSeries.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                            items(state.actorWorksSeries) { series ->
                                                PosterTile(
                                                    title = series.title,
                                                    hue = series.hue,
                                                    code = series.title.take(2).uppercase(),
                                                    progressPercent = series.recentProgress,
                                                    isFavorite = series.isFavorite,
                                                    quality = series.quality,
                                                    rating = series.rating,
                                                    seasons = series.seasons
                                                ) {
                                                    navController.navigate(Routes.detail("series", series.id))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // D09 + D10 — seasons & episodes (prototype `.ep-grid`, 2 columns).
                if (state.series != null) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            SectionHeader(stringResource(R.string.detail_seasons_episodes))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.seasons) { season ->
                                    SeasonPill(
                                        text = stringResource(R.string.detail_season, season),
                                        selected = state.selectedSeason == season
                                    ) {
                                        viewModel.selectSeason(season)
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                state.episodes.chunked(2).forEach { pair ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        pair.forEach { episode ->
                                            EpisodeCard(
                                                episode = episode,
                                                quality = quality,
                                                modifier = Modifier.weight(1f),
                                                onDownload = { viewModel.downloadEpisode(episode) }
                                            ) {
                                                // R-03: pass the tapped episode itself, not
                                                // just the series id — "seriesId:S01E07"
                                                // (same ":" encoding as the catchup ids).
                                                // PlayerViewModel.load("series", id) must
                                                // split it and play that exact episode
                                                // instead of the resume/S1E1 fallback.
                                                navController.navigate(
                                                    Routes.player(
                                                        "series",
                                                        "$id:${episode.code.ifBlank { "E${episode.number}" }}"
                                                    )
                                                )
                                            }
                                        }
                                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prototype `#dt-backdrop`: two hue radial glows over a dark linear
 * gradient, full screen behind the scrolling content.
 */
@Composable
private fun DetailBackdrop(hue: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A0703), Color(0xFF050301)),
                    start = Offset(0f, 0f),
                    end = Offset(0f, h)
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.0f to Color.hsl(hue.toFloat(), 0.80f, 0.50f, 0.34f),
                    0.62f to Color.Transparent,
                    center = Offset(w * 0.72f, h * 0.06f),
                    radius = w * 0.625f
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.0f to Color.hsl(hue.toFloat(), 0.80f, 0.40f, 0.22f),
                    0.62f to Color.Transparent,
                    center = Offset(w * 0.12f, h * 0.96f),
                    radius = w * 0.47f
                )
            )
        )
    }
}

/** One `.dt-facts` cell: 14sp uppercase label + 19sp value. */
@Composable
private fun DetailFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label.uppercase(),
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp, color = GoldSoft
        )
        Text(
            text = value,
            fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite
        )
    }
}

/**
 * Prototype `.cast-card` with a round initials avatar (no photos exist).
 * §4.49 — name only: the provider supplies no role, and the old invented
 * roles were removed. Tapping a card shows that actor's other works;
 * the selected actor gets a bright border.
 */
@Composable
private fun CastCard(name: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val hue = 30 + (kotlin.math.abs(name.hashCode()) % 45)
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Column(
        modifier = Modifier.width(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xD6191206))
            .border(
                1.5.dp,
                if (selected) Color(0xFFFFF8D6) else BorderGold,
                RoundedCornerShape(20.dp)
            )
            .then(if (onClick != null) Modifier.tvFocus().clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(96.dp)
                .clip(CircleShape)
                .background(Color.hsl(hue.toFloat(), 0.70f, 0.45f))
                .border(2.5.dp, GoldBright, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = 32.sp, fontWeight = FontWeight.Black, color = GoldWhite
            )
        }
        Text(
            text = name, fontSize = 18.sp, fontWeight = FontWeight.Black,
            color = GoldWhite, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

/** §4.53 — five focusable stars (remote-friendly); filled = rated. */
@Composable
private fun StarRatingInput(stars: Int, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..5).forEach { n ->
            val filled = n <= stars
            Box(
                modifier = Modifier.size(56.dp)
                    .clip(CircleShape)
                    .then(if (filled) Modifier.background(GoldGradient) else Modifier.background(Color(0xE6161005)))
                    .border(
                        2.dp,
                        if (filled) Color(0xFFFFF8D6) else BorderGold,
                        CircleShape
                    )
                    .tvFocus()
                    // T-05: tapping the already-selected star clears the
                    // rating (0) instead of re-setting the same value.
                    .clickable { onRate(if (n == stars) 0 else n) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "★",
                    fontSize = 30.sp, fontWeight = FontWeight.Black,
                    color = if (filled) GoldDeep else GoldBright
                )
            }
        }
    }
}

/** Prototype `.season-pill`: 50dp high, radius 25. */
@Composable
private fun SeasonPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .then(
                if (selected) Modifier.background(GoldGradient)
                else Modifier.background(Color(0xEB1A1205))
            )
            .border(
                2.dp,
                if (selected) Color(0xFFFFF8D6) else BorderGold,
                RoundedCornerShape(25.dp)
            )
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            color = if (selected) GoldDeep else GoldWhite
        )
    }
}

/**
 * Prototype `.ep-card`: 64dp gold number box + title 19sp + meta 15sp +
 * 24dp play icon; the small per-episode download button is kept
 * (offline download is approved).
 *
 * Focus (2026-11-23): the card shell is NOT focusable — the play area
 * and the download button are two sibling focus stops inside it.
 * Before, the button was nested inside the focusable card (two
 * overlapping focus targets, OK could play or download unpredictably).
 * The layout is unchanged: same shell, same padding, same order.
 */
@Composable
private fun EpisodeCard(
    episode: Episode,
    quality: String,
    modifier: Modifier = Modifier,
    onDownload: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE6161005))
            .border(1.5.dp, BorderGold, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f)
                    .tvFocus()
                    .clickable { onClick() },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GoldGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = episode.code.ifBlank { "E${episode.number}" },
                        fontSize = 16.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp, color = GoldDeep,
                        maxLines = 1
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        fontSize = 19.sp, fontWeight = FontWeight.Black, color = GoldWhite,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${episode.durationMin} min • $quality",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldBright,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_play), contentDescription = null,
                    tint = GoldBright, modifier = Modifier.size(24.dp)
                )
            }
            // Per-episode offline download (2026-11-22).
            // R-S4: ic_download icon instead of the "↓" text glyph.
            GhostButton(iconRes = R.drawable.ic_download) { onDownload() }
        }
    }
}
