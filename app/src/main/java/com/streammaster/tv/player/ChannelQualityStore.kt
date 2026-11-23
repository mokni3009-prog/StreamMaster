package com.streammaster.tv.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-channel smart quality (§4.36, round 2 — approved 2026-11-23).
 *
 * Own Preferences DataStore ("channel_quality"), player-owned like
 * [BitrateStore]: channelId -> [QualityMode] name. The engine applies the
 * stored mode when a channel starts and persists a manual change for that
 * channel only. No stored entry means "highest quality possible" (the
 * current AUTO + maxSpeed behaviour) — nothing is stored for the default.
 */
private val Context.channelQualityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "channel_quality"
)

@Singleton
class ChannelQualityStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(channelId: String) = stringPreferencesKey("q_$channelId")

    suspend fun getQuality(channelId: String): QualityMode? =
        context.channelQualityDataStore.data.map { prefs ->
            prefs[key(channelId)]?.let { runCatching { QualityMode.valueOf(it) }.getOrNull() }
        }.first()

    fun observeQuality(channelId: String): Flow<QualityMode?> =
        context.channelQualityDataStore.data.map { prefs ->
            prefs[key(channelId)]?.let { runCatching { QualityMode.valueOf(it) }.getOrNull() }
        }

    suspend fun setQuality(channelId: String, mode: QualityMode) {
        context.channelQualityDataStore.edit { prefs ->
            prefs[key(channelId)] = mode.name
        }
    }

    suspend fun clearQuality(channelId: String) {
        context.channelQualityDataStore.edit { prefs ->
            prefs.remove(key(channelId))
        }
    }
}

/** Last-played episode + position of one series (5–94% resume window). */
data class EpisodeResume(val season: Int, val number: Int, val positionMs: Long)

/**
 * Per-episode resume store (§4.1 / §4.40 Continue Watching — series side).
 *
 * Own Preferences DataStore ("episode_resume"), player-owned like
 * [BitrateStore]: one entry per series, `seriesId -> "season:number:positionMs"`.
 *
 * Why a player-owned store instead of `EpisodeEntity.progressMs`: the
 * progressMs field does not exist yet in `Entities.kt` (the data builder
 * owns that file and the Room migration it would need). The engine must
 * save the current episode's position on pause/stop/switch either way, so
 * it persists it here — no schema change, no migration, and
 * [com.streammaster.tv.ui.viewmodel.PlayerViewModel.resumeInfoFor] reads
 * exactly this store. If the data builder later adds
 * `EpisodeEntity.progressMs`, this class is the single seam to swap: keep
 * the same [get]/[save]/[clear] API and read the entity instead.
 * Series-level `SeriesEntity.recentProgress` (percent) cannot attribute a
 * position to an episode, so it is deliberately NOT used here.
 */
private val Context.episodeResumeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "episode_resume"
)

@Singleton
class EpisodeResumeStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun key(seriesId: String) = stringPreferencesKey("ep_$seriesId")

    suspend fun get(seriesId: String): EpisodeResume? =
        context.episodeResumeDataStore.data.map { prefs ->
            prefs[key(seriesId)]?.let { parse(it) }
        }.first()

    fun observe(seriesId: String): Flow<EpisodeResume?> =
        context.episodeResumeDataStore.data.map { prefs ->
            prefs[key(seriesId)]?.let { parse(it) }
        }

    suspend fun save(seriesId: String, season: Int, number: Int, positionMs: Long) {
        if (positionMs <= 0) return
        context.episodeResumeDataStore.edit { prefs ->
            prefs[key(seriesId)] = "$season:$number:$positionMs"
        }
    }

    suspend fun clear(seriesId: String) {
        context.episodeResumeDataStore.edit { prefs ->
            prefs.remove(key(seriesId))
        }
    }

    private fun parse(raw: String): EpisodeResume? {
        val parts = raw.split(":")
        if (parts.size != 3) return null
        val season = parts[0].toIntOrNull() ?: return null
        val number = parts[1].toIntOrNull() ?: return null
        val positionMs = parts[2].toLongOrNull() ?: return null
        return EpisodeResume(season, number, positionMs)
    }
}
