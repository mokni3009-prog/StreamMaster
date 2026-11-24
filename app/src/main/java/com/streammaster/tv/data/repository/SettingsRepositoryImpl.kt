package com.streammaster.tv.data.repository

import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
    private val db: StreamMasterDatabase
) : SettingsRepository {

    override val settings: Flow<AppSettings> = settingsStore.settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
    }

    /** PIN applies immediately (no Save button needed for parental control). */
    override suspend fun setPin(pin: String) {
        settingsStore.setPin(pin)
    }

    /**
     * Locked if the id is in lockedItems, or in any lockedBouquets list, or —
     * for a live channel — if its bouquet is locked. LG-14: films/series in
     * a locked bouquet used to open without a PIN (only LIVE was checked).
     * Films/series carry no bouquetId column, so membership is resolved by
     * bouquet name == genre within the item's own playlist (Xtream bouquets
     * are named after their category, which is also the film/series genre).
     */
    override suspend fun isLocked(kind: ContentKind, id: String): Boolean {
        val current = settingsStore.settings.first()
        if (current.pin.isEmpty()) return false
        if (id in current.lockedItems) return true
        val lockedBouquetIds = current.lockedBouquets.values.flatten().toSet()
        if (id in lockedBouquetIds) return true
        if (kind == ContentKind.LIVE) {
            val channel = db.channelDao().getById(id)
            if (channel != null && channel.bouquetId in lockedBouquetIds) return true
        }
        if (kind == ContentKind.FILM || kind == ContentKind.SERIES) {
            val (playlistId, genre) = when (kind) {
                ContentKind.FILM -> db.filmDao().getById(id)?.let { it.playlistId to it.genre }
                else -> db.seriesDao().getById(id)?.let { it.playlistId to it.genre }
            } ?: return false
            val bouquetKind = if (kind == ContentKind.FILM) "FILMS" else "SERIES"
            val locked = db.bouquetDao().getAll(playlistId)
                .filter { it.kind == bouquetKind && it.id in lockedBouquetIds }
                .any { it.name == genre }
            if (locked) return true
        }
        return false
    }
}
