package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.R
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.model.Episode
import com.streammaster.tv.data.model.Film
import com.streammaster.tv.data.model.Series
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.DownloadRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.repository.WatchLaterRepository
import com.streammaster.tv.player.VodDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val kind: String = "film",
    val film: Film? = null,
    val series: Series? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val cast: List<Pair<String, String>> = emptyList(),
    /** Offline download status of the current film (2026-11-22). */
    val downloadStatus: DownloadStatus? = null,
    /** One-shot notice (string res) shown as a toast, then consumed. */
    val downloadNoticeRes: Int? = null
)

// Deterministic cast, mirroring the prototype's hash-based CAST_NAMES logic.
private val CAST_NAMES = listOf(
    "Karim Haddad", "Léa Moreau", "Yasmine Benali", "Omar Diallo", "Sofia Ricci",
    "Nadia Kaci", "Thomas Leroy", "Amine Cherif", "Claire Dubois", "Mehdi Ayari",
    "Inès Trabelsi", "Hugo Fontaine", "Sara Mansouri", "Lucas Bernard", "Aïcha Sassi",
    "Nicolas Petit", "Emma Girard", "Rayan Khelifi", "Chloé Martin", "Adam Bensaïd"
)
private val CAST_ROLES = listOf("Rôle principal", "Rôle principal", "Second rôle", "Antagoniste", "Invité", "Narrateur")

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: VodDownloadManager
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Whether the current film/series is in the active profile's watch-later list. */
    private val _isWatchLater = MutableStateFlow(false)
    val isWatchLater: StateFlow<Boolean> = _isWatchLater.asStateFlow()

    fun load(kind: String, id: String) = viewModelScope.launch {
        val cast = castFor(id)
        when (kind) {
            "film" -> {
                val film = contentRepository.getFilm(id)
                _state.update { it.copy(kind = kind, film = film, cast = cast) }
            }
            "series" -> {
                val series = contentRepository.getSeries(id)
                val seasons = (1..(series?.seasons ?: 1)).toList()
                val episodes = if (series != null) contentRepository.getEpisodes(id, 1) else emptyList()
                _state.update {
                    it.copy(kind = kind, series = series, seasons = seasons, selectedSeason = 1, episodes = episodes, cast = cast)
                }
            }
        }
        refreshWatchLater()
        refreshDownloadStatus()
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

    private fun castFor(id: String): List<Pair<String, String>> {
        var h = 0
        for (c in id) h = (h * 31 + c.code) ushr 0
        val start = h % CAST_NAMES.size
        return (0 until 6).map { i ->
            CAST_NAMES[(start + i * 5) % CAST_NAMES.size] to CAST_ROLES[i % CAST_ROLES.size]
        }
    }
}
