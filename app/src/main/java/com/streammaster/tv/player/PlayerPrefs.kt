package com.streammaster.tv.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small player-owned Preferences DataStore ("player_prefs").
 *
 * The task allows persisting the last measured bitrate in a small prefs file inside
 * the player package instead of extending [com.streammaster.tv.data.model.AppSettings]
 * (which is owned by the data agent). It also holds the RTSP-over-TCP force flag:
 * there is no `rtspTcp` field in AppSettings (CONTRACT §3), so the player keeps its
 * own flag here; [PlayerManagerImpl] reads it when building RTSP sources and the
 * Settings screen can expose it later through [BitrateStore.setForceRtspTcp].
 */
private val Context.playerPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_prefs"
)

@Singleton
class BitrateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val LAST_MEASURED_BITRATE = longPreferencesKey("last_measured_bitrate")
        private val FORCE_RTSP_TCP = booleanPreferencesKey("force_rtsp_tcp")

        /** Fallback initial bitrate (bps) when no measurement was ever stored (B3). */
        const val DEFAULT_INITIAL_BITRATE = 3_000_000L
    }

    suspend fun getLastMeasuredBitrate(): Long? =
        context.playerPrefsDataStore.data.map { it[LAST_MEASURED_BITRATE] }.first()

    suspend fun saveMeasuredBitrate(bitrateBps: Long) {
        if (bitrateBps <= 0) return
        context.playerPrefsDataStore.edit { prefs ->
            prefs[LAST_MEASURED_BITRATE] = bitrateBps
        }
    }

    suspend fun isForceRtspTcp(): Boolean =
        context.playerPrefsDataStore.data.map { it[FORCE_RTSP_TCP] ?: false }.first()

    suspend fun setForceRtspTcp(force: Boolean) {
        context.playerPrefsDataStore.edit { prefs -> prefs[FORCE_RTSP_TCP] = force }
    }
}
