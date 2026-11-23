package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import com.streammaster.tv.R
import com.streammaster.tv.data.api.XtreamService
import com.streammaster.tv.data.db.UserRatingDao
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.DownloadRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.repository.WatchLaterRepository
import com.streammaster.tv.player.VodDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

data class DetailUiState(
    val kind: String = "film",
    val film: Film? = null,
    val series: Series? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    /**
     * §4.49 — REAL provider credits only. Names come from the Xtream
     * `cast` field (`get_vod_info` / `get_series_info`); the director from
     * `director`. No generated names, no invented roles: the provider
     * supplies no role, so none is shown. Empty when the provider supplies
     * no credits — the screen then shows "cast unavailable" instead of
     * inventing names (the old hash-based `castFor` was deleted 2026-11-23).
     */
    val cast: List<String> = emptyList(),
    /** §4.49 — director from the provider (`director`), null when absent. */
    val director: String? = null,
    /** True once the credits fetch finished (success or not), so the
     *  screen can tell "no credits" apart from "still loading". */
    val creditsLoaded: Boolean = false,
    /** §4.49 — same-genre titles from the active catalog (no external API). */
    val similarFilms: List<Film> = emptyList(),
    val similarSeries: List<Series> = emptyList(),
    /** §4.49 — actor tapped in the cast row; their other catalog works. */
    val selectedActor: String? = null,
    val actorWorksFilms: List<Film> = emptyList(),
    val actorWorksSeries: List<Series> = emptyList(),
    val actorWorksLoading: Boolean = false,
    /** §4.53 — the user's own star rating for this title (0 = not rated). */
    val userStars: Int = 0,
    /** §4.1 — a saved progress point exists (recentProgress in 1..94,
     *  the same window PlayerViewModel uses to resume a film). */
    val hasResume: Boolean = false,
    val resumePercent: Int = 0,
    /** Offline download status of the current film (2026-11-22). */
    val downloadStatus: DownloadStatus? = null,
    /** One-shot notice (string res) shown as a toast, then consumed. */
    val downloadNoticeRes: Int? = null
)

/**
 * §4.53 — one user star rating (UI-layer shape, mapped from the Room
 * `UserRating` row — same fields, but `itemType` as [ContentKind] here).
 * Feed rows to the §4.36.1 taste engine by mapping them to
 * `HomeViewModel.TasteRating(refId = itemId, kind = itemType, stars)`.
 */
data class UserRating(
    val itemType: ContentKind,
    val itemId: String,
    val stars: Int,
    val updatedAt: Long
)

/** §4.49 — provider credits for one title (cast names + director). */
data class VodCredits(
    val cast: List<String>,
    val director: String?
)

/** Xtream `get_vod_info` (films). Not in `XtreamService` yet — this local
 *  Retrofit interface covers it; `get_series_info` already exists there.
 *  Response shape per the Xtream API: `{"info": {"cast", "director", …}}`.
 *  `cast` is one comma-separated string ("Actor A, Actor B, …"). */
private data class VodInfoResponse(val info: VodInfoDetails?)
private data class VodInfoDetails(
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?
)
private interface VodInfoService {
    @GET("player_api.php?action=get_vod_info")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Int
    ): VodInfoResponse
}

/** Max catalog items whose credits are fetched for one actor tap (§4.49). */
private const val MAX_ACTOR_SCAN = 60
/** Max actor works shown, per kind. */
private const val MAX_ACTOR_WORKS = 8
/** Max similar titles shown, per kind. */
private const val MAX_SIMILAR = 8

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: VodDownloadManager,
    private val playlistRepository: PlaylistRepository,
    private val okHttpClient: OkHttpClient,
    private val userRatingDao: UserRatingDao
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Whether the current film/series is in the active profile's watch-later list. */
    private val _isWatchLater = MutableStateFlow(false)
    val isWatchLater: StateFlow<Boolean> = _isWatchLater.asStateFlow()

    /** In-memory credits cache (id -> real provider credits). */
    private val creditsCache = mutableMapOf<String, VodCredits>()
    /** Ids whose credits fetch already failed this session — not retried. */
    private val creditsMissed = mutableSetOf<String>()
    private var actorJob: Job? = null

    fun load(kind: String, id: String) = viewModelScope.launch {
        val currentId = _state.value.film?.id ?: _state.value.series?.id
        if (currentId != null && currentId != id) {
            actorJob?.cancel()
            _state.update {
                it.copy(
                    selectedActor = null, actorWorksFilms = emptyList(),
                    actorWorksSeries = emptyList(), actorWorksLoading = false
                )
            }
        }
        when (kind) {
            "film" -> {
                val film = contentRepository.getFilm(id)
                val stars = film?.let {
                    userRatingDao.getRating(ContentKind.FILM.name, it.id).first()
                } ?: 0
                _state.update {
                    it.copy(
                        kind = kind, film = film,
                        hasResume = (film?.recentProgress ?: 0) in 1..94,
                        resumePercent = film?.recentProgress ?: 0,
                        userStars = stars
                    )
                }
            }
            "series" -> {
                val series = contentRepository.getSeries(id)
                val seasons = (1..(series?.seasons ?: 1)).toList()
                val episodes = if (series != null) contentRepository.getEpisodes(id, 1) else emptyList()
                val stars = series?.let {
                    userRatingDao.getRating(ContentKind.SERIES.name, it.id).first()
                } ?: 0
                _state.update {
                    it.copy(
                        kind = kind, series = series, seasons = seasons,
                        selectedSeason = 1, episodes = episodes,
                        hasResume = (series?.recentProgress ?: 0) in 1..94,
                        resumePercent = series?.recentProgress ?: 0,
                        userStars = stars
                    )
                }
            }
        }
        refreshWatchLater()
        refreshDownloadStatus()
        // §4.49 — real provider credits only (best effort, cached). A
        // missing/failed fetch leaves cast empty and the screen shows
        // "cast unavailable" — never invented names.
        val credits = creditsFor(kind, id)
        _state.update {
            it.copy(
                cast = credits?.cast ?: emptyList(),
                director = credits?.director,
                creditsLoaded = true
            )
        }
        refreshSimilar()
    }

    /** §4.49 — real credits for one title, from the active Xtream
     *  playlist only. Stalker/M3U playlists carry no cast/director
     *  endpoint, so they return null (section hidden / "unavailable"). */
    private suspend fun creditsFor(kind: String, id: String): VodCredits? {
        creditsCache[id]?.let { return it }
        if (id in creditsMissed) return null
        val credits = loadCredits(kind, id)
        if (credits != null) creditsCache[id] = credits else creditsMissed += id
        return credits
    }

    private suspend fun loadCredits(kind: String, id: String): VodCredits? {
        val playlist = playlistRepository.getActive() ?: return null
        if (playlist.type != PlaylistType.XTREAM) return null
        val numericId = id.removePrefix("xt-vod-").removePrefix("xt-ser-").toIntOrNull()
            ?: return null
        val base = playlist.serverUrl.trimEnd('/') + "/"
        val retrofit = Retrofit.Builder()
            .baseUrl(base)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return runCatching {
            if (kind == "film") {
                val info = retrofit.create(VodInfoService::class.java)
                    .getVodInfo(playlist.username, playlist.password, numericId).info
                info?.let { VodCredits(parseCast(it.cast), cleanName(it.director)) }
            } else {
                val info = retrofit.create(XtreamService::class.java)
                    .getSeriesInfo(playlist.username, playlist.password, numericId).info
                info?.let { VodCredits(parseCast(it.cast), cleanName(it.director)) }
            }
        }.getOrNull()
    }

    /** Xtream `cast` is one comma-separated string. */
    private fun parseCast(raw: String?): List<String> =
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.distinct()?.take(12) ?: emptyList()

    private fun cleanName(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    /** §4.49 — similar titles: same genre in the active catalog, best
     *  provider rating first. No external API. */
    private suspend fun refreshSimilar() {
        val s = _state.value
        val genre = (s.film?.genre ?: s.series?.genre)?.trim().orEmpty()
        val selfId = s.film?.id ?: s.series?.id ?: return
        if (genre.isEmpty()) {
            _state.update { it.copy(similarFilms = emptyList(), similarSeries = emptyList()) }
            return
        }
        val films = contentRepository.observeFilms().first()
        val series = contentRepository.observeSeries().first()
        _state.update {
            it.copy(
                similarFilms = films
                    .filter { f -> f.id != selfId && !f.hidden && f.genre.trim().equals(genre, ignoreCase = true) }
                    .sortedByDescending { f -> f.rating }.take(MAX_SIMILAR),
                similarSeries = series
                    .filter { s2 -> s2.id != selfId && !s2.hidden && s2.genre.trim().equals(genre, ignoreCase = true) }
                    .sortedByDescending { s2 -> s2.rating }.take(MAX_SIMILAR)
            )
        }
    }

    /** §4.49 — tapping an actor shows their other catalog works.
     *  The catalog models carry no cast field, so each candidate's
     *  credits are fetched on demand from the provider (cached per id)
     *  and matched on the actor name. Bounded scan: at most
     *  [MAX_ACTOR_SCAN] titles, stops once [MAX_ACTOR_WORKS] per kind
     *  are found. Tapping the same actor again closes the section. */
    fun selectActor(name: String) {
        if (_state.value.selectedActor == name) {
            clearActor()
            return
        }
        actorJob?.cancel()
        _state.update {
            it.copy(
                selectedActor = name, actorWorksFilms = emptyList(),
                actorWorksSeries = emptyList(), actorWorksLoading = true
            )
        }
        actorJob = viewModelScope.launch {
            val films = contentRepository.observeFilms().first()
            val series = contentRepository.observeSeries().first()
            val foundFilms = mutableListOf<Film>()
            val foundSeries = mutableListOf<Series>()
            var scanned = 0
            for (film in films) {
                if (foundFilms.size >= MAX_ACTOR_WORKS || scanned >= MAX_ACTOR_SCAN) break
                ensureActive()
                scanned++
                if (creditsFor("film", film.id)?.cast
                        ?.any { it.equals(name, ignoreCase = true) } == true
                ) {
                    foundFilms += film
                    _state.update { it.copy(actorWorksFilms = foundFilms.toList()) }
                }
            }
            for (s in series) {
                if (foundSeries.size >= MAX_ACTOR_WORKS || scanned >= MAX_ACTOR_SCAN) break
                ensureActive()
                scanned++
                if (creditsFor("series", s.id)?.cast
                        ?.any { it.equals(name, ignoreCase = true) } == true
                ) {
                    foundSeries += s
                    _state.update { it.copy(actorWorksSeries = foundSeries.toList()) }
                }
            }
            _state.update { it.copy(actorWorksLoading = false) }
        }
    }

    fun clearActor() {
        actorJob?.cancel()
        _state.update {
            it.copy(
                selectedActor = null, actorWorksFilms = emptyList(),
                actorWorksSeries = emptyList(), actorWorksLoading = false
            )
        }
    }

    /** §4.53 — save the user's star rating (1..5) for the current title. */
    fun setUserRating(stars: Int) {
        val s = _state.value
        val kind = if (s.kind == "series") ContentKind.SERIES else ContentKind.FILM
        val id = s.film?.id ?: s.series?.id ?: return
        val clamped = stars.coerceIn(1, 5)
        viewModelScope.launch {
            userRatingDao.setRating(kind.name, id, clamped, System.currentTimeMillis())
        }
        _state.update { it.copy(userStars = clamped) }
    }

    /** §4.53 — every stored rating, for the §4.36.1 taste engine
     *  (map each row to `TasteRating(refId = itemId, kind, stars)`). */
    suspend fun getAllUserRatings(): List<UserRating> =
        userRatingDao.getAll().first().map { row ->
            UserRating(
                itemType = ContentKind.valueOf(row.itemType),
                itemId = row.itemId,
                stars = row.stars,
                updatedAt = row.updatedAt
            )
        }

    /** Offline download status of the current film (series use per-episode
     *  downloads; their status lives on the Downloads screen). */
    private suspend fun refreshDownloadStatus() {
        val film = _state.value.film ?: return
        val row = downloadRepository.getByRef(ContentKind.FILM, film.id)
        _state.update { it.copy(downloadStatus = row?.status) }
    }

    /** Downloads the current film for offline watching (2026-11-22).
     *  HLS playlists (.m3u8) cannot be saved as a file — refused with a
     *  notice instead of a broken download. */
    fun downloadFilm() = viewModelScope.launch {
        val film = _state.value.film ?: return@launch
        if (film.streamUrl.contains(".m3u8")) {
            _state.update { it.copy(downloadNoticeRes = R.string.download_unsupported) }
            return@launch
        }
        downloadManager.startDownload(ContentKind.FILM, film.id, film.title, film.posterUrl, film.streamUrl)
        _state.update { it.copy(downloadNoticeRes = R.string.download_started, downloadStatus = DownloadStatus.QUEUED) }
    }

    /** Downloads one episode (refId = "seriesId:S01E01", unique per series). */
    fun downloadEpisode(episode: Episode) = viewModelScope.launch {
        val series = _state.value.series ?: return@launch
        val url = episode.streamUrl ?: return@launch
        if (url.contains(".m3u8")) {
            _state.update { it.copy(downloadNoticeRes = R.string.download_unsupported) }
            return@launch
        }
        downloadManager.startDownload(
            ContentKind.SERIES, "${series.id}:${episode.code}",
            "${series.title} — ${episode.title}", series.posterUrl, url
        )
        _state.update { it.copy(downloadNoticeRes = R.string.download_started) }
    }

    fun consumeDownloadNotice() {
        _state.update { it.copy(downloadNoticeRes = null) }
    }

    fun selectSeason(season: Int) = viewModelScope.launch {
        val series = _state.value.series ?: return@launch
        val episodes = contentRepository.getEpisodes(series.id, season)
        _state.update { it.copy(selectedSeason = season, episodes = episodes) }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val s = _state.value
        when (s.kind) {
            "film" -> s.film?.let { contentRepository.setFavorite(ContentKind.FILM, it.id, !it.isFavorite) }
            "series" -> s.series?.let { contentRepository.setFavorite(ContentKind.SERIES, it.id, !it.isFavorite) }
        }
        // Refresh the displayed item so the favourite badge updates.
        load(s.kind, s.film?.id ?: s.series?.id ?: return@launch)
    }

    /** Watch-later (2026-11-22): toggles the current title in the active
     *  profile's "À voir plus tard" list. */
    fun toggleWatchLater() = viewModelScope.launch {
        val s = _state.value
        val kind = if (s.kind == "series") ContentKind.SERIES else ContentKind.FILM
        val id = s.film?.id ?: s.series?.id ?: return@launch
        val title = s.film?.title ?: s.series?.title ?: return@launch
        val profileId = settingsRepository.settings.first().activeProfileId
        watchLaterRepository.toggle(kind, id, title, profileId)
        refreshWatchLater()
    }

    private suspend fun refreshWatchLater() {
        val s = _state.value
        val kind = if (s.kind == "series") ContentKind.SERIES else ContentKind.FILM
        val id = s.film?.id ?: s.series?.id ?: return
        val profileId = settingsRepository.settings.first().activeProfileId
        _isWatchLater.value = watchLaterRepository.isSaved(kind, id, profileId)
    }
}
