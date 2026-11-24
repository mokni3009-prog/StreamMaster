package com.streammaster.tv.data.subtitles

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streammaster.tv.data.security.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val TAG = "StreamMaster"
private val Context.subtitleDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitles")

/**
 * SPEC §4.48 — OpenSubtitles API key, stored in its own DataStore
 * ("subtitles"). The key comes from the user's own free OpenSubtitles
 * account (opensubtitles.com → API consumers); it is typed in from the
 * subtitle button dialog and NEVER hard-coded anywhere in the app.
 * The key is only ever sent to api.opensubtitles.com, in the `Api-Key`
 * header — never logged, never put in a URL.
 *
 * SEC-04 (2026-11-24): the key is stored ENCRYPTED via [SecureStore]
 * (Android KeyStore + AES/GCM, `v1:` format). [apiKey] still emits the
 * plaintext key, so [SubtitleDownloader] is unchanged. A key stored as
 * plaintext before this change is decrypted transparently on read and
 * rewritten encrypted by [migrateLegacyApiKey] on first collection.
 */
class SubtitlePrefs(
    private val context: Context,
    private val secureStore: SecureStore = SecureStore()
) {

    companion object {
        val KEY_API_KEY = stringPreferencesKey("openSubtitlesApiKey")
    }

    // OP-16: a corrupt DataStore file must not crash collectors.
    // (Fixed 2026-11-24 during the SEC-04 work: the flow carries
    // Preferences, so the previous emit("") did not type-check —
    // emptyPreferences() lets the map below emit "" instead.)
    val apiKey: Flow<String> = context.subtitleDataStore.data
        .catch { emit(emptyPreferences()) }
        // SEC-04: one-shot migration — a key stored as plaintext before
        // encryption is rewritten encrypted on first collection.
        .onEach { prefs -> migrateLegacyApiKey(prefs) }
        .map { secureStore.decrypt(it[KEY_API_KEY]) }

    /**
     * SEC-04 — one-shot migration of a legacy plaintext API key: if the
     * stored value has no `v1:` prefix it is encrypted in place. Runs on
     * first collection of [apiKey]; after migration the check is a no-op.
     * Re-reads the value inside `edit`, and never loses the key — if
     * encryption fails the plaintext is kept so subtitles keep working.
     */
    private suspend fun migrateLegacyApiKey(prefs: Preferences) {
        val stored = prefs[KEY_API_KEY] ?: return
        if (stored.isEmpty() || secureStore.isEncrypted(stored)) return
        runCatching {
            context.subtitleDataStore.edit { current ->
                val raw = current[KEY_API_KEY] ?: return@edit
                if (raw.isNotEmpty() && !secureStore.isEncrypted(raw)) {
                    current[KEY_API_KEY] = secureStore.encryptForStore(raw, raw)
                }
            }
        }.onFailure { Log.w(TAG, "OpenSubtitles key migration failed, keeping plaintext value", it) }
    }

    suspend fun setApiKey(apiKey: String) {
        // SEC-04: stored encrypted; on encryption failure the previous
        // value is kept (never plaintext, never lost).
        context.subtitleDataStore.edit {
            it[KEY_API_KEY] = secureStore.encryptForStore(apiKey.trim(), it[KEY_API_KEY])
        }
    }

    suspend fun clearApiKey() {
        context.subtitleDataStore.edit { it.remove(KEY_API_KEY) }
    }
}
