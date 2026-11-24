package com.streammaster.tv.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.security.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val TAG = "StreamMaster"
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore Preferences "settings". Keys exactly as CONTRACT §4.
 * lockedBouquetsJson format: "LIVE:id1,id2;FILMS:id3" (kind -> comma-separated ids).
 * lockedItemsJson format: comma-separated item ids.
 *
 * SEC-02 (2026-11-24): the parental PIN is stored ENCRYPTED via
 * [SecureStore] (Android KeyStore + AES/GCM, `v1:` format). [AppSettings.pin]
 * still carries the plaintext PIN in memory, so the `== pin` comparisons
 * in Splash/Shell/PlayerViewModel are unchanged. A PIN stored as plaintext
 * before this change is decrypted transparently on read and rewritten
 * encrypted by [migrateLegacyPin] on first collection.
 */
class SettingsStore(
    private val context: Context,
    private val secureStore: SecureStore = SecureStore()
) {

    companion object {
        val KEY_LANG = stringPreferencesKey("lang")
        val KEY_DNS = stringPreferencesKey("dns")
        val KEY_DNS_CUSTOM = stringPreferencesKey("dnsCustom")
        val KEY_UA = stringPreferencesKey("ua")
        val KEY_UA_CUSTOM = stringPreferencesKey("uaCustom")
        val KEY_FORMAT = stringPreferencesKey("format")
        val KEY_ENGINE = stringPreferencesKey("engine")
        val KEY_EPG_WINDOW = stringPreferencesKey("epgWindow")
        val KEY_AUTO_RESUME = booleanPreferencesKey("autoResume")
        val KEY_SLEEP_MINUTES = intPreferencesKey("sleepMinutes")
        val KEY_PIN = stringPreferencesKey("pin")
        val KEY_LOCKED_BOUQUETS_JSON = stringPreferencesKey("lockedBouquetsJson")
        val KEY_LOCKED_ITEMS_JSON = stringPreferencesKey("lockedItemsJson")
        val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("autoStartOnBoot")
        val KEY_APP_LOCK = booleanPreferencesKey("appLock")
        val KEY_KIDS_MODE = booleanPreferencesKey("kidsMode")
        val KEY_FRAME_RATE_SWITCH = booleanPreferencesKey("frameRateSwitch")
        val KEY_EXTERNAL_PLAYER = stringPreferencesKey("externalPlayer")
        val KEY_RECORDING_FOLDER_URI = stringPreferencesKey("recordingFolderUri")
        val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("activeProfileId")
        val KEY_UPDATE_URL = stringPreferencesKey("updateUrl")
        val KEY_SMART_FAILOVER = booleanPreferencesKey("smartFailover")
        val KEY_MATCH_CENTER = booleanPreferencesKey("matchCenter")
        val KEY_GOAL_ALERTS = booleanPreferencesKey("goalAlerts")
        val KEY_MAX_SPEED = booleanPreferencesKey("maxSpeed")
        val KEY_REMOTE_ENABLED = booleanPreferencesKey("remoteEnabled")
        val KEY_REMOTE_CODE = stringPreferencesKey("remoteCode")
        // §4.37 / §4.36 / §4.54 / §4.56 / §4.59 / §4.61 / §4.39 / §4.42
        // (2026-11-23): new settings — see AppSettings for the meaning of
        // each field. Contract: other builders read these via the
        // AppSettings flow; setters below are the write path.
        val KEY_LAST_CHANNEL = stringPreferencesKey("lastChannelId")
        val KEY_VOLUME_NORMALIZATION = booleanPreferencesKey("volumeNormalization")
        val KEY_AUTO_ARABIC_SUBS = booleanPreferencesKey("autoArabicSubs")
        val KEY_DND_ENABLED = booleanPreferencesKey("dndEnabled")
        val KEY_SMART_MATCH_ALERTS = booleanPreferencesKey("smartMatchAlerts")
        val KEY_FIRST_RUN_DONE = booleanPreferencesKey("firstRunDone")
        val KEY_AUTO_BACKUP_WEEKLY = booleanPreferencesKey("autoBackupWeekly")
        val KEY_SUBTITLE_SIZE = intPreferencesKey("subtitleSize")
        val KEY_SUBTITLE_COLOR = stringPreferencesKey("subtitleColor")
        val KEY_SUBTITLE_POSITION = stringPreferencesKey("subtitlePosition")
        val KEY_PIN_RECOVERY_HASH = stringPreferencesKey("pinRecoveryHash")

        fun encodeLockedBouquets(map: Map<String, List<String>>): String =
            map.entries.filter { it.value.isNotEmpty() }
                .joinToString(";") { (k, v) -> "$k:${v.joinToString(",")}" }

        fun decodeLockedBouquets(raw: String): Map<String, List<String>> {
            if (raw.isBlank()) return emptyMap()
            return raw.split(";").mapNotNull { part ->
                val idx = part.indexOf(':')
                if (idx <= 0) null else part.substring(0, idx) to
                    part.substring(idx + 1).split(",").filter { it.isNotBlank() }
            }.toMap()
        }

        fun encodeLockedItems(list: List<String>): String = list.joinToString(",")
        fun decodeLockedItems(raw: String): List<String> =
            if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        // OP-16: a corrupt/unreadable DataStore file used to throw out of
        // this Flow and crash every collector (settings are read almost
        // everywhere) — fall back to defaults instead.
        // OP-16 (fixed 2026-11-24 during the SEC-02 work): the catch must
        // emit emptyPreferences() — the flow carries Preferences, so the
        // previous emit(AppSettings()) did not type-check. The downstream
        // map then builds the default AppSettings() from the empty prefs.
        .catch { e ->
            Log.w(TAG, "settings read failed, using defaults", e)
            emit(emptyPreferences())
        }
        // SEC-02: one-shot migration — a PIN stored as plaintext before
        // encryption is rewritten encrypted on first collection.
        .onEach { prefs -> migrateLegacyPin(prefs) }
        .map { prefs ->
            AppSettings(
                lang = prefs[KEY_LANG] ?: "fr",
                dns = prefs[KEY_DNS] ?: "auto",
                dnsCustom = prefs[KEY_DNS_CUSTOM] ?: "",
                ua = prefs[KEY_UA] ?: "default",
                uaCustom = prefs[KEY_UA_CUSTOM] ?: "",
                format = prefs[KEY_FORMAT] ?: "auto",
                engine = prefs[KEY_ENGINE] ?: "exo",
                epgWindow = prefs[KEY_EPG_WINDOW] ?: "24h",
                autoResume = prefs[KEY_AUTO_RESUME] ?: false,
                sleepMinutes = prefs[KEY_SLEEP_MINUTES] ?: 0,
                pin = secureStore.decrypt(prefs[KEY_PIN]),
                lockedBouquets = decodeLockedBouquets(prefs[KEY_LOCKED_BOUQUETS_JSON] ?: ""),
                lockedItems = decodeLockedItems(prefs[KEY_LOCKED_ITEMS_JSON] ?: ""),
                autoStartOnBoot = prefs[KEY_AUTO_START_ON_BOOT] ?: false,
                appLock = prefs[KEY_APP_LOCK] ?: false,
                kidsMode = prefs[KEY_KIDS_MODE] ?: false,
                frameRateSwitch = prefs[KEY_FRAME_RATE_SWITCH] ?: true,
                externalPlayer = prefs[KEY_EXTERNAL_PLAYER] ?: "",
                recordingFolderUri = prefs[KEY_RECORDING_FOLDER_URI] ?: "",
                activeProfileId = prefs[KEY_ACTIVE_PROFILE_ID] ?: "default",
                updateUrl = prefs[KEY_UPDATE_URL] ?: "",
                smartFailover = prefs[KEY_SMART_FAILOVER] ?: false,
                matchCenter = prefs[KEY_MATCH_CENTER] ?: false,
                goalAlerts = prefs[KEY_GOAL_ALERTS] ?: false,
                maxSpeed = prefs[KEY_MAX_SPEED] ?: true,
                remoteEnabled = prefs[KEY_REMOTE_ENABLED] ?: false,
                remoteCode = prefs[KEY_REMOTE_CODE] ?: "",
                lastChannelId = prefs[KEY_LAST_CHANNEL],
                volumeNormalization = prefs[KEY_VOLUME_NORMALIZATION] ?: true,
                autoArabicSubs = prefs[KEY_AUTO_ARABIC_SUBS] ?: true,
                dndEnabled = prefs[KEY_DND_ENABLED] ?: false,
                smartMatchAlerts = prefs[KEY_SMART_MATCH_ALERTS] ?: true,
                firstRunDone = prefs[KEY_FIRST_RUN_DONE] ?: false,
                autoBackupWeekly = prefs[KEY_AUTO_BACKUP_WEEKLY] ?: false,
                subtitleSize = prefs[KEY_SUBTITLE_SIZE] ?: 100,
                subtitleColor = prefs[KEY_SUBTITLE_COLOR] ?: "#FFFFFF",
                subtitlePosition = prefs[KEY_SUBTITLE_POSITION] ?: "bottom",
                pinRecoveryHash = prefs[KEY_PIN_RECOVERY_HASH]
            )
        }

    /**
     * SEC-02 — one-shot migration of a legacy plaintext PIN: if the stored
     * value has no `v1:` prefix it is encrypted in place. Runs on first
     * collection of [settings]; after migration the check is a no-op.
     * Re-reads the value inside `edit` (it may have changed since the
     * snapshot), and never loses the value — if encryption fails the
     * plaintext is kept so the PIN still works.
     */
    private suspend fun migrateLegacyPin(prefs: Preferences) {
        val stored = prefs[KEY_PIN] ?: return
        if (stored.isEmpty() || secureStore.isEncrypted(stored)) return
        runCatching {
            context.settingsDataStore.edit { current ->
                val raw = current[KEY_PIN] ?: return@edit
                if (raw.isNotEmpty() && !secureStore.isEncrypted(raw)) {
                    current[KEY_PIN] = secureStore.encryptForStore(raw, raw)
                }
            }
        }.onFailure { Log.w(TAG, "PIN migration failed, keeping plaintext value", it) }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        // Read current value from the flow is racy; instead read prefs directly.
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                lang = prefs[KEY_LANG] ?: "fr",
                dns = prefs[KEY_DNS] ?: "auto",
                dnsCustom = prefs[KEY_DNS_CUSTOM] ?: "",
                ua = prefs[KEY_UA] ?: "default",
                uaCustom = prefs[KEY_UA_CUSTOM] ?: "",
                format = prefs[KEY_FORMAT] ?: "auto",
                engine = prefs[KEY_ENGINE] ?: "exo",
                epgWindow = prefs[KEY_EPG_WINDOW] ?: "24h",
                autoResume = prefs[KEY_AUTO_RESUME] ?: false,
                sleepMinutes = prefs[KEY_SLEEP_MINUTES] ?: 0,
                pin = secureStore.decrypt(prefs[KEY_PIN]),
                lockedBouquets = decodeLockedBouquets(prefs[KEY_LOCKED_BOUQUETS_JSON] ?: ""),
                lockedItems = decodeLockedItems(prefs[KEY_LOCKED_ITEMS_JSON] ?: ""),
                autoStartOnBoot = prefs[KEY_AUTO_START_ON_BOOT] ?: false,
                appLock = prefs[KEY_APP_LOCK] ?: false,
                kidsMode = prefs[KEY_KIDS_MODE] ?: false,
                frameRateSwitch = prefs[KEY_FRAME_RATE_SWITCH] ?: true,
                externalPlayer = prefs[KEY_EXTERNAL_PLAYER] ?: "",
                recordingFolderUri = prefs[KEY_RECORDING_FOLDER_URI] ?: "",
                activeProfileId = prefs[KEY_ACTIVE_PROFILE_ID] ?: "default",
                updateUrl = prefs[KEY_UPDATE_URL] ?: "",
                smartFailover = prefs[KEY_SMART_FAILOVER] ?: false,
                matchCenter = prefs[KEY_MATCH_CENTER] ?: false,
                goalAlerts = prefs[KEY_GOAL_ALERTS] ?: false,
                maxSpeed = prefs[KEY_MAX_SPEED] ?: true,
                remoteEnabled = prefs[KEY_REMOTE_ENABLED] ?: false,
                remoteCode = prefs[KEY_REMOTE_CODE] ?: "",
                lastChannelId = prefs[KEY_LAST_CHANNEL],
                volumeNormalization = prefs[KEY_VOLUME_NORMALIZATION] ?: true,
                autoArabicSubs = prefs[KEY_AUTO_ARABIC_SUBS] ?: true,
                dndEnabled = prefs[KEY_DND_ENABLED] ?: false,
                smartMatchAlerts = prefs[KEY_SMART_MATCH_ALERTS] ?: true,
                firstRunDone = prefs[KEY_FIRST_RUN_DONE] ?: false,
                autoBackupWeekly = prefs[KEY_AUTO_BACKUP_WEEKLY] ?: false,
                subtitleSize = prefs[KEY_SUBTITLE_SIZE] ?: 100,
                subtitleColor = prefs[KEY_SUBTITLE_COLOR] ?: "#FFFFFF",
                subtitlePosition = prefs[KEY_SUBTITLE_POSITION] ?: "bottom",
                pinRecoveryHash = prefs[KEY_PIN_RECOVERY_HASH]
            )
            val next = transform(current)
            prefs[KEY_LANG] = next.lang
            prefs[KEY_DNS] = next.dns
            prefs[KEY_DNS_CUSTOM] = next.dnsCustom
            prefs[KEY_UA] = next.ua
            prefs[KEY_UA_CUSTOM] = next.uaCustom
            prefs[KEY_FORMAT] = next.format
            prefs[KEY_ENGINE] = next.engine
            prefs[KEY_EPG_WINDOW] = next.epgWindow
            prefs[KEY_AUTO_RESUME] = next.autoResume
            prefs[KEY_SLEEP_MINUTES] = next.sleepMinutes
            // SEC-02: the PIN is stored encrypted; on encryption failure
            // the previously stored value is kept (never plaintext).
            prefs[KEY_PIN] = secureStore.encryptForStore(next.pin, prefs[KEY_PIN])
            prefs[KEY_LOCKED_BOUQUETS_JSON] = encodeLockedBouquets(next.lockedBouquets)
            prefs[KEY_LOCKED_ITEMS_JSON] = encodeLockedItems(next.lockedItems)
            prefs[KEY_AUTO_START_ON_BOOT] = next.autoStartOnBoot
            prefs[KEY_APP_LOCK] = next.appLock
            prefs[KEY_KIDS_MODE] = next.kidsMode
            prefs[KEY_FRAME_RATE_SWITCH] = next.frameRateSwitch
            prefs[KEY_EXTERNAL_PLAYER] = next.externalPlayer
            prefs[KEY_RECORDING_FOLDER_URI] = next.recordingFolderUri
            prefs[KEY_ACTIVE_PROFILE_ID] = next.activeProfileId
            prefs[KEY_UPDATE_URL] = next.updateUrl
            prefs[KEY_SMART_FAILOVER] = next.smartFailover
            prefs[KEY_MATCH_CENTER] = next.matchCenter
            prefs[KEY_GOAL_ALERTS] = next.goalAlerts
            prefs[KEY_MAX_SPEED] = next.maxSpeed
            prefs[KEY_REMOTE_ENABLED] = next.remoteEnabled
            prefs[KEY_REMOTE_CODE] = next.remoteCode
            if (next.lastChannelId == null) prefs.remove(KEY_LAST_CHANNEL)
            else prefs[KEY_LAST_CHANNEL] = next.lastChannelId
            prefs[KEY_VOLUME_NORMALIZATION] = next.volumeNormalization
            prefs[KEY_AUTO_ARABIC_SUBS] = next.autoArabicSubs
            prefs[KEY_DND_ENABLED] = next.dndEnabled
            prefs[KEY_SMART_MATCH_ALERTS] = next.smartMatchAlerts
            prefs[KEY_FIRST_RUN_DONE] = next.firstRunDone
            prefs[KEY_AUTO_BACKUP_WEEKLY] = next.autoBackupWeekly
            prefs[KEY_SUBTITLE_SIZE] = next.subtitleSize
            prefs[KEY_SUBTITLE_COLOR] = next.subtitleColor
            prefs[KEY_SUBTITLE_POSITION] = next.subtitlePosition
            if (next.pinRecoveryHash == null) prefs.remove(KEY_PIN_RECOVERY_HASH)
            else prefs[KEY_PIN_RECOVERY_HASH] = next.pinRecoveryHash
        }
    }

    suspend fun setPin(pin: String) {
        // SEC-02: stored encrypted; on encryption failure the previous
        // value is kept (never plaintext, never lost).
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PIN] = secureStore.encryptForStore(pin, prefs[KEY_PIN])
        }
    }

    /** §4.37 — last watched live channel (null clears it). Read by the
     *  launch flow when autoResume is on; written on every channel switch. */
    suspend fun setLastChannelId(id: String?) {
        context.settingsDataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_LAST_CHANNEL) else prefs[KEY_LAST_CHANNEL] = id
        }
    }

    suspend fun setVolumeNormalization(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_VOLUME_NORMALIZATION] = enabled }
    }

    suspend fun setAutoArabicSubs(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AUTO_ARABIC_SUBS] = enabled }
    }

    suspend fun setDndEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_DND_ENABLED] = enabled }
    }

    suspend fun setSmartMatchAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SMART_MATCH_ALERTS] = enabled }
    }

    suspend fun setFirstRunDone(done: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_FIRST_RUN_DONE] = done }
    }

    suspend fun setAutoBackupWeekly(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AUTO_BACKUP_WEEKLY] = enabled }
    }

    suspend fun setSubtitleSize(percent: Int) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SUBTITLE_SIZE] = percent }
    }

    suspend fun setSubtitleColor(hex: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SUBTITLE_COLOR] = hex }
    }

    suspend fun setSubtitlePosition(position: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SUBTITLE_POSITION] = position }
    }

    /** §4.42 — stores ONLY the hash of the PIN recovery code (null clears
     *  it, e.g. when the PIN is removed). The code itself is never stored. */
    suspend fun setPinRecoveryHash(hash: String?) {
        context.settingsDataStore.edit { prefs ->
            if (hash == null) prefs.remove(KEY_PIN_RECOVERY_HASH)
            else prefs[KEY_PIN_RECOVERY_HASH] = hash
        }
    }

}
