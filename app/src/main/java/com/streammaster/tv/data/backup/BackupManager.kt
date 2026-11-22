package com.streammaster.tv.data.backup

import com.google.gson.Gson
import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.HistoryEntry
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.WatchLaterItem
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.repository.toEntity
import com.streammaster.tv.data.repository.toModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val settings: AppSettings? = null,
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val watchLater: List<WatchLaterItem> = emptyList(),
    val customNumbers: Map<String, Int> = emptyMap()
)

/**
 * Backup / restore (2026-11-22): one JSON file with settings, playlists
 * (including credentials — keep the file private), favourites, history,
 * watch-later and custom channel numbers. Recordings (.ts files) and the
 * app database are NOT included. Restore replaces playlists and settings,
 * and merges favourites, history, watch-later and custom numbers.
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: StreamMasterDatabase,
    private val settingsRepository: SettingsRepository,
    private val contentRepository: ContentRepository,
    private val playlistRepository: PlaylistRepository
) {
    private val gson = Gson()

    suspend fun export(): String {
        val settings = settingsRepository.settings.first()
        val playlists = playlistRepository.observePlaylists().first()
        val channels = contentRepository.observeChannels().first()
        val films = contentRepository.observeFilms().first()
        val series = contentRepository.observeSeries().first()
        val favorites = buildList {
            channels.filter { it.isFavorite }.forEach { add("LIVE:${it.id}") }
            films.filter { it.isFavorite }.forEach { add("FILM:${it.id}") }
            series.filter { it.isFavorite }.forEach { add("SERIES:${it.id}") }
        }
        val history = contentRepository.observeHistory().first()
        val watchLater = db.watchLaterDao().getAll().map { it.toModel() }
        val customNumbers = channels.mapNotNull { c -> c.customNum?.let { c.id to it } }.toMap()
        return gson.toJson(
            BackupData(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                settings = settings,
                playlists = playlists,
                favorites = favorites,
                history = history,
                watchLater = watchLater,
                customNumbers = customNumbers
            )
        )
    }

    suspend fun import(json: String): Boolean {
        val data = runCatching { gson.fromJson(json, BackupData::class.java) }.getOrNull()
            ?: return false
        data.settings?.let { settings ->
            settingsRepository.update { settings }
        }
        if (data.playlists.isNotEmpty()) {
            val current = playlistRepository.observePlaylists().first()
            current.forEach { playlistRepository.deletePlaylist(it.id) }
            data.playlists.forEach { playlistRepository.addPlaylist(it) }
        }
        data.favorites.forEach { fav ->
            val parts = fav.split(":", limit = 2)
            if (parts.size != 2) return@forEach
            runCatching {
                contentRepository.setFavorite(ContentKind.valueOf(parts[0]), parts[1], true)
            }
        }
        data.history.forEach { entry ->
            runCatching { contentRepository.recordHistory(entry.kind, entry.refId, entry.title) }
        }
        data.watchLater.forEach { item ->
            runCatching { db.watchLaterDao().upsert(item.toEntity()) }
        }
        data.customNumbers.forEach { (id, num) ->
            runCatching { contentRepository.setCustomNumber(id, num) }
        }
        return true
    }
}
