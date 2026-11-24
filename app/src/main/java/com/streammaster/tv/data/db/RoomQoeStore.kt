package com.streammaster.tv.data.db

import com.streammaster.tv.player.QoeEvent as PlayerQoeEvent
import com.streammaster.tv.player.QoeStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DC-01 — the Room-backed [QoeStore] the player contract (QoeLogger KDoc)
 * requires: QoE events land in the `qoe_events` table (read back by
 * DiagnosticsViewModel) instead of logcat only. The old [LoggingQoeStore]
 * binding stays as the fallback in the player package.
 *
 * Rows older than 30 days are pruned on each insert so the table stays
 * bounded (the diagnostics screen only ever shows the recent 500).
 */
@Singleton
class RoomQoeStore @Inject constructor(
    private val qoeDao: QoeDao
) : QoeStore {

    override suspend fun log(event: PlayerQoeEvent) {
        qoeDao.insert(
            QoeEvent(
                timestampMillis = event.timestampMillis,
                eventType = event.type.name,
                engine = event.engine.name,
                channelId = event.itemId.takeIf { it.isNotBlank() },
                detail = event.details,
                valueMs = event.valueMillis
            )
        )
        qoeDao.deleteOlderThan(
            System.currentTimeMillis() - RETENTION_MILLIS
        )
    }

    companion object {
        const val RETENTION_MILLIS = 30L * 24 * 3600_000L
    }
}
