package com.streammaster.tv.player

import android.util.Log
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.PlaybackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/** One QoE event (GUIDE D9). Persisted by the data agent, see [QoeStore]. */
data class QoeEvent(
    val type: QoeEventType,
    val itemId: String,
    val kind: ContentKind,
    val engine: Engine,
    val timestampMillis: Long = System.currentTimeMillis(),
    /** e.g. rebuffer duration ms, ABR bitrate, stall count. */
    val valueMillis: Long = 0L,
    val details: String = ""
)

enum class QoeEventType {
    PLAYBACK_START,
    REBUFFER_START,
    REBUFFER_END,
    ABR_SWITCH,
    ENGINE_SWITCH,
    STALL,
    ERROR
}

/**
 * Storage behind [QoeLogger].
 *
 * The current approved storage is logcat: `di/AppModule.kt` binds [QoeStore]
 * to [LoggingQoeStore]. A Room-backed store (`qoe_events` table + QoeDao,
 * provided by the data agent) can replace this binding later without
 * touching the player — the [QoeLogger] only depends on this interface.
 */
interface QoeStore {
    suspend fun log(event: QoeEvent)
}

/** Current store: QoE events go to logcat (see [QoeStore]). */
@Singleton
class LoggingQoeStore @Inject constructor() : QoeStore {
    override suspend fun log(event: QoeEvent) {
        Log.i(TAG, "QoE ${event.type} item=${event.itemId} engine=${event.engine} " +
            "valueMs=${event.valueMillis} details=${event.details}")
    }
}

/**
 * Quality-of-experience logging (GUIDE D9): start time, rebuffer count and
 * duration, ABR switches, engine switches and stall firings — each tagged with
 * the item, the engine and the quality in use, so a bad channel, a bad CDN or
 * a bad device can be told apart later from the `qoe_events` table.
 */
@Singleton
class QoeLogger @Inject constructor(
    private val store: QoeStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var rebufferStartMillis: Long = 0L
    private var rebufferCount: Int = 0

    private fun itemInfo(item: PlaybackItem?): Pair<String, ContentKind> = when (item) {
        is PlaybackItem.Live -> item.channel.id to ContentKind.LIVE
        is PlaybackItem.Movie -> item.film.id to ContentKind.FILM
        is PlaybackItem.EpisodeItem -> item.series.id to ContentKind.SERIES
        is PlaybackItem.RecordingItem -> item.recording.channelId to ContentKind.LIVE
        is PlaybackItem.DownloadItem -> item.download.refId to item.download.kind
        null -> "" to ContentKind.LIVE
    }

    private fun emit(event: QoeEvent) {
        scope.launch {
            try {
                store.log(event)
            } catch (e: Exception) {
                Log.w(TAG, "QoE log failed", e)
            }
        }
    }

    fun logPlaybackStart(item: PlaybackItem?, engine: Engine) {
        val (id, kind) = itemInfo(item)
        rebufferCount = 0
        rebufferStartMillis = 0L
        emit(QoeEvent(QoeEventType.PLAYBACK_START, id, kind, engine))
    }

    fun logRebufferStart(item: PlaybackItem?, engine: Engine) {
        if (rebufferStartMillis != 0L) return // Already inside a rebuffer.
        rebufferStartMillis = System.currentTimeMillis()
        rebufferCount++
        val (id, kind) = itemInfo(item)
        emit(QoeEvent(QoeEventType.REBUFFER_START, id, kind, engine))
    }

    /** Ends the current rebuffer and logs its duration. No-op if not rebuffering. */
    fun logRebufferEnd(item: PlaybackItem?, engine: Engine) {
        if (rebufferStartMillis == 0L) return
        val duration = System.currentTimeMillis() - rebufferStartMillis
        rebufferStartMillis = 0L
        val (id, kind) = itemInfo(item)
        emit(
            QoeEvent(
                QoeEventType.REBUFFER_END, id, kind, engine,
                valueMillis = duration,
                details = "rebufferCount=$rebufferCount"
            )
        )
    }

    fun logAbrSwitch(item: PlaybackItem?, engine: Engine, fromLabel: String, toLabel: String) {
        val (id, kind) = itemInfo(item)
        emit(
            QoeEvent(
                QoeEventType.ABR_SWITCH, id, kind, engine,
                details = "$fromLabel -> $toLabel"
            )
        )
    }

    fun logEngineSwitch(item: PlaybackItem?, from: Engine, to: Engine, reason: String) {
        val (id, kind) = itemInfo(item)
        emit(
            QoeEvent(
                QoeEventType.ENGINE_SWITCH, id, kind, to,
                details = "$from -> $to: $reason"
            )
        )
    }

    fun logStall(item: PlaybackItem?, engine: Engine) {
        val (id, kind) = itemInfo(item)
        emit(QoeEvent(QoeEventType.STALL, id, kind, engine))
    }

    fun logError(item: PlaybackItem?, engine: Engine, message: String) {
        val (id, kind) = itemInfo(item)
        emit(QoeEvent(QoeEventType.ERROR, id, kind, engine, details = message))
    }
}
