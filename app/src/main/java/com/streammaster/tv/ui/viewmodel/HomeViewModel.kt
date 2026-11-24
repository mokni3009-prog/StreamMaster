package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.db.UserRatingDao
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.HistoryEntry
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val expiryDate: String = "",
    val expiryDaysLeft: Int? = null,
    val channelCount: Int = 0,
    val filmCount: Int = 0,
    val seriesCount: Int = 0,
    val playlistCount: Int = 0,
    /** 4th stat on Home (prototype "Mes Favoris"): favourites across all kinds. */
    val favoriteCount: Int = 0,
    val recentFilms: List<Film> = emptyList(),
    val recentSeries: List<Series> = emptyList(),
    val popularFilms: List<Film> = emptyList(),
    val trendingSeries: List<Series> = emptyList(),
    val recommendedFilms: List<Film> = emptyList(),
    val recommendedSeries: List<Series> = emptyList()
)

/**
 * §4.53 user star rating, as consumed by the §4.36.1 taste engine.
 * Wired to the Room `user_ratings` table: [HomeViewModel] collects
 * `UserRatingDao.getAll()` in init, maps each row to a [TasteRating]
 * and feeds it via [HomeViewModel.setUserRatings].
 */
data class TasteRating(
    /** Id of the rated film/series (Film.id / Series.id). */
    val refId: String,
    val kind: ContentKind,
    /** Stars given by the user (§4.53), 1..5. */
    val stars: Int
)

/** §4.21: at most 8 recommendations in total (films + series together). */
private const val MAX_RECOMMENDATIONS = 8

@HiltViewModel
class HomeViewModel @Inject constructor(
    contentRepository: ContentRepository,
    playlistRepository: PlaylistRepository,
    userRatingDao: UserRatingDao
) : ViewModel() {
    /** §4.36.1 / §4.53: user star ratings, fed via [setUserRatings] (see
     *  the [TasteRating] contract above). Wired to the Room `user_ratings`
     *  table: every rating change flows into the taste engine. */
    private val userRatings = MutableStateFlow<List<TasteRating>>(emptyList())

    init {
        viewModelScope.launch {
            userRatingDao.getAll().collect { rows ->
                // L-08: a stale/hand-written row with an unknown kind
                // must not crash the Home ViewModel — skip it.
                setUserRatings(rows.mapNotNull { row ->
                    runCatching { ContentKind.valueOf(row.itemType) }.getOrNull()?.let { kind ->
                        TasteRating(
                            refId = row.itemId,
                            kind = kind,
                            stars = row.stars
                        )
                    }
                })
            }
        }
    }

    /** Feed user star ratings (§4.53) into the §4.36.1 taste engine. */
    fun setUserRatings(ratings: List<TasteRating>) {
        userRatings.value = ratings
    }

    val state: StateFlow<HomeUiState> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeFilms(),
        contentRepository.observeSeries(),
        playlistRepository.observePlaylists(),
        contentRepository.observeHistory(),
        userRatings
    ) { channels: List<Channel>, films: List<Film>, series: List<Series>, playlists, history, ratings ->
        val active = playlists.firstOrNull { it.isActive } ?: playlists.firstOrNull()
        // §4.21 + §4.36.1: one ranked list of at most 8, split back by kind.
        val (recommendedFilms, recommendedSeries) = recommend(films, series, history, ratings)
        HomeUiState(
            expiryDate = active?.expiryDate ?: "",
            expiryDaysLeft = daysLeft(active?.expiryDate ?: ""),
            channelCount = channels.size, filmCount = films.size, seriesCount = series.size,
            playlistCount = playlists.size,
            // M-05: hidden favourites are excluded here, like
            // FavorisScreen — otherwise the Home stat (and the sidebar
            // badge) counts items the Favoris screen never shows.
            favoriteCount = channels.count { it.isFavorite && !it.hidden } + films.count { it.isFavorite && !it.hidden } + series.count { it.isFavorite && !it.hidden },
            // Dynamic "Récemment vus": progress recorded at pause/stop, cleared at >=95%.
            // Hidden items never appear on Home (same as Live/Epg/Favoris).
            recentFilms = films.filter { !it.hidden && it.recentProgress in 1..94 },
            recentSeries = series.filter { !it.hidden && it.recentProgress in 1..94 },
            popularFilms = films.filter { !it.hidden }.sortedByDescending { it.rating }.take(8),
            trendingSeries = series.filter { !it.hidden }.sortedByDescending { it.rating }.take(8),
            // Recommendations (§4.21 literal + §4.36.1 taste learning).
            recommendedFilms = recommendedFilms,
            recommendedSeries = recommendedSeries
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    // ------------------------------------------------------------------
    // §4.21 + §4.36.1 — Recommendations
    // ------------------------------------------------------------------

    /** One ranked candidate, either kind, so films + series share one top-8. */
    private sealed interface ScoredCandidate {
        val score: Double
    }
    private data class ScoredFilm(val film: Film, override val score: Double) : ScoredCandidate
    private data class ScoredSeries(val series: Series, override val score: Double) : ScoredCandidate

    /**
     * Taste profile of the active profile, learned from its own history
     * only (history is already per-profile: `observeHistory()` filters by
     * `activeProfileId`). Rule-based and fully on-device — no ML library.
     * Returns null when there is nothing to learn from (no last-watched
     * genre), in which case the section stays hidden (§4.21).
     */
    private data class TasteProfile(
        /** §4.21: genres of the LAST watched item only (single item). */
        val lastGenres: Set<String>,
        /** Watch frequency: how many history entries per genre. */
        val genreCounts: Map<String, Int>,
        /** Time of day: dominant watch hour-bucket per genre. */
        val dominantBucket: Map<String, Int>,
        /** Watch duration: average watched-film duration (minutes) per genre. */
        val avgDurationByGenre: Map<String, Double>,
        /** User star ratings (§4.53): average stars per genre. */
        val avgStarsByGenre: Map<String, Double>,
        /** Hour-bucket right now (see [hourBucket]). */
        val nowBucket: Int
    )

    private fun tasteProfile(
        films: List<Film>,
        series: List<Series>,
        history: List<HistoryEntry>,
        ratings: List<TasteRating>
    ): TasteProfile? {
        // History is newest-first (HistoryDao: ORDER BY timestampMillis
        // DESC), so first() is the last thing watched.
        val last = history.firstOrNull() ?: return null
        val lastGenres = genreOf(last, films, series)?.let { setOf(it) } ?: emptySet()
        if (lastGenres.isEmpty()) return null // last watched was live TV: no genre, no recommendations
        val genres = history.mapNotNull { genreOf(it, films, series) }
        val genreCounts = genres.groupingBy { it }.eachCount()
        val genreBuckets = history.mapNotNull { entry ->
            genreOf(entry, films, series)?.let { it to hourBucket(entry.timestampMillis) }
        }
        val dominantBucket = genreBuckets.groupBy({ it.first }, { it.second })
            .mapValues { (_, buckets) -> buckets.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: -1 }
        val avgDurationByGenre = history.mapNotNull { entry ->
            val genre = genreOf(entry, films, series) ?: return@mapNotNull null
            if (entry.kind != ContentKind.FILM) return@mapNotNull null
            val duration = films.firstOrNull { it.id == entry.refId }?.durationMin?.takeIf { it > 0 } ?: return@mapNotNull null
            genre to duration
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, durations) -> durations.average() }
        val avgStarsByGenre = ratings.mapNotNull { rating ->
            val genre = when (rating.kind) {
                ContentKind.FILM -> films.firstOrNull { it.id == rating.refId }?.genre
                ContentKind.SERIES -> series.firstOrNull { it.id == rating.refId }?.genre
                else -> null
            } ?: return@mapNotNull null
            genre to rating.stars
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, stars) -> stars.average() }
        return TasteProfile(
            lastGenres = lastGenres,
            genreCounts = genreCounts,
            dominantBucket = dominantBucket,
            avgDurationByGenre = avgDurationByGenre,
            avgStarsByGenre = avgStarsByGenre,
            nowBucket = hourBucket(System.currentTimeMillis())
        )
    }

    /** Genre of one history entry (film/series only; live TV has none). */
    private fun genreOf(entry: HistoryEntry, films: List<Film>, series: List<Series>): String? =
        when (entry.kind) {
            ContentKind.FILM -> films.firstOrNull { it.id == entry.refId }?.genre?.takeIf { it.isNotBlank() }
            ContentKind.SERIES -> series.firstOrNull { it.id == entry.refId }?.genre?.takeIf { it.isNotBlank() }
            else -> null
        }

    /** Hour bucket: 0 morning (06-11), 1 afternoon (12-17), 2 evening (18-23), 3 night (00-05). */
    private fun hourBucket(millis: Long): Int =
        when (Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)) {
            in 6..11 -> 0
            in 12..17 -> 1
            in 18..23 -> 2
            else -> 3
        }

    /**
     * Taste score of one candidate. The catalog rating is the main term
     * (§4.21: ordered by rating); the §4.36.1 signals only nudge it:
     * genre frequency (+0.15 per re-watch, max +0.60), time of day
     * (+0.50 when the genre is usually watched in the current hour-bucket
     * — e.g. watched in the evening → suggested in the evening), watch
     * duration (+0.30 when the film length is close to what the profile
     * usually watches in that genre), user stars (+0.25 per star above 3
     * on average for the genre, max +0.50).
     */
    private fun tasteScore(
        genre: String,
        catalogRating: Double,
        durationMin: Int?,
        profile: TasteProfile
    ): Double {
        var score = catalogRating
        score += minOf(profile.genreCounts[genre] ?: 0, 4) * 0.15
        if (profile.dominantBucket[genre] == profile.nowBucket) score += 0.5
        val avgDuration = profile.avgDurationByGenre[genre]
        if (durationMin != null && avgDuration != null) {
            val diff = kotlin.math.abs(durationMin - avgDuration)
            score += when {
                diff <= 30 -> 0.3
                diff <= 60 -> 0.15
                else -> 0.0
            }
        }
        val stars = profile.avgStarsByGenre[genre]
        if (stars != null && stars > 3.0) score += minOf((stars - 3.0) * 0.25, 0.5)
        return score
    }

    /**
     * §4.21 + §4.36.1: unwatched films + series from the genres of the
     * LAST watched item only, ranked by taste score, **8 in total**
     * (films + series together), split back by kind for the Home section.
     * Empty when there is no watch history — the section hides (§4.21).
     */
    private fun recommend(
        films: List<Film>,
        series: List<Series>,
        history: List<HistoryEntry>,
        ratings: List<TasteRating>
    ): Pair<List<Film>, List<Series>> {
        val profile = tasteProfile(films, series, history, ratings) ?: return emptyList<Film>() to emptyList()
        val candidates = recommendFilms(films, history, profile) + recommendSeries(series, history, profile)
        val top = candidates.sortedByDescending { it.score }.take(MAX_RECOMMENDATIONS)
        return top.mapNotNull { (it as? ScoredFilm)?.film } to
            top.mapNotNull { (it as? ScoredSeries)?.series }
    }

    /** Unwatched films whose genre matches the last watched item, ranked by taste score. */
    private fun recommendFilms(
        films: List<Film>,
        history: List<HistoryEntry>,
        profile: TasteProfile
    ): List<ScoredFilm> {
        val watchedIds = history.filter { it.kind == ContentKind.FILM }.map { it.refId }.toSet()
        return films.filter { !it.hidden && it.id !in watchedIds && it.genre in profile.lastGenres }
            .map { film -> ScoredFilm(film, tasteScore(film.genre, film.rating, film.durationMin, profile)) }
            .sortedByDescending { it.score }
    }

    /** Unwatched series whose genre matches the last watched item, ranked by taste score. */
    private fun recommendSeries(
        series: List<Series>,
        history: List<HistoryEntry>,
        profile: TasteProfile
    ): List<ScoredSeries> {
        val watchedIds = history.filter { it.kind == ContentKind.SERIES }.map { it.refId }.toSet()
        return series.filter { !it.hidden && it.id !in watchedIds && it.genre in profile.lastGenres }
            .map { s -> ScoredSeries(s, tasteScore(s.genre, s.rating, null, profile)) }
            .sortedByDescending { it.score }
    }

    /** Days until the subscription expires (null when unknown). Accepts a
     *  unix timestamp (seconds or millis) or a yyyy-MM-dd / dd/MM/yyyy date. */
    private fun daysLeft(expiryDate: String): Int? {
        val trimmed = expiryDate.trim()
        if (trimmed.isBlank()) return null
        val millis = trimmed.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000 else it }
            ?: runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(trimmed)?.time }.getOrNull()
            ?: runCatching { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).parse(trimmed)?.time }.getOrNull()
            ?: return null
        return ((millis - System.currentTimeMillis()) / 86_400_000L).toInt()
    }
}
