package com.streammaster.tv.data.repository

import com.streammaster.tv.data.db.StreamMasterDatabase
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
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
     * for a live channel — if its bouquet is locked.
     */
    override suspend fun isLocked(kind: ContentKind, id: String): Boolean {
        val current = kotlinx.coroutines.flow.first(settingsStore.settings)
        if (current.pin.isEmpty()) return false
        if (id in current.lockedItems) return true
        val lockedBouquetIds = current.lockedBouquets.values.flatten().toSet()
        if (id in lockedBouquetIds) return true
        if (kind == ContentKind.LIVE) {
            val channel = db.channelDao().getById(id)
            if (channel != null && channel.bouquetId in lockedBouquetIds) return true
        }
        return false
    }
}
