package com.streammaster.tv.data.drive

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streammaster.tv.data.security.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private const val TAG = "StreamMaster"
private val Context.driveDataStore: DataStore<Preferences> by preferencesDataStore(name = "drive")

/**
 * SPEC §4.63 — Google Drive link state, stored in its own DataStore
 * ("drive"), separate from the main settings store.
 *
 * NO secret is ever hard-coded here. The OAuth Client ID is entered by the
 * user (from their own Google Cloud Console project) and stored here; the
 * access/refresh tokens are filled in by the sign-in flow once it is wired
 * (see [DriveClient]; it needs the coach's Cloud Console credentials first).
 * Clearing [driveLinked]/tokens is the "unlink" operation.
 *
 * SEC-03 (2026-11-24): the access/refresh tokens are stored ENCRYPTED via
 * [SecureStore] (Android KeyStore + AES/GCM, `v1:` format). [DriveState]
 * still carries the plaintext tokens in memory, so [DriveClient] is
 * unchanged. Tokens stored as plaintext before this change are decrypted
 * transparently on read and rewritten encrypted by [migrateLegacyTokens]
 * on first collection.
 */
class DrivePrefs(
    private val context: Context,
    private val secureStore: SecureStore = SecureStore()
) {

    companion object {
        val KEY_LINKED = booleanPreferencesKey("driveLinked")
        val KEY_ACCOUNT = stringPreferencesKey("driveAccount")
        val KEY_CLIENT_ID = stringPreferencesKey("driveClientId")
        /** OAuth 2.0 access token (short-lived, "Bearer" for Drive REST). */
        val KEY_ACCESS_TOKEN = stringPreferencesKey("driveAccessToken")
        /** OAuth 2.0 refresh token (long-lived, exchanged for new access tokens). */
        val KEY_REFRESH_TOKEN = stringPreferencesKey("driveRefreshToken")
        /** Epoch millis when [KEY_ACCESS_TOKEN] expires (0 = unknown/expired). */
        val KEY_TOKEN_EXPIRES_AT = longPreferencesKey("driveTokenExpiresAt")
    }

    data class DriveState(
        val linked: Boolean = false,
        val account: String = "",
        val clientId: String = "",
        val accessToken: String = "",
        val refreshToken: String = "",
        val tokenExpiresAt: Long = 0L
    ) {
        /** True when we have a token that has not expired yet.
         *  LG-20: tokenExpiresAt == 0 means "unknown/expired" — it used to
         *  count as valid, so an expired token was sent until the next
         *  setAccessToken call. */
        val hasValidToken: Boolean
            get() = accessToken.isNotBlank() &&
                tokenExpiresAt > System.currentTimeMillis()

        /**
         * True when the app is fully configured for Drive: the coach entered
         * his OAuth Client ID AND a sign-in produced a token.
         */
        val ready: Boolean get() = linked && clientId.isNotBlank() && hasValidToken

        /**
         * True when the only thing missing is the coach's OAuth setup:
         * no Client ID has been entered yet.
         */
        val needsOAuthSetup: Boolean get() = clientId.isBlank()
    }

    val state: Flow<DriveState> = context.driveDataStore.data
        // OP-16: a corrupt DataStore file must not crash collectors.
        // (Fixed 2026-11-24 during the SEC-03 work: the flow carries
        // Preferences, so the previous emit(DriveState()) did not
        // type-check — emptyPreferences() lets the map below build the
        // default DriveState() instead.)
        .catch { emit(emptyPreferences()) }
        // SEC-03: one-shot migration — tokens stored as plaintext before
        // encryption are rewritten encrypted on first collection.
        .onEach { prefs -> migrateLegacyTokens(prefs) }
        .map { prefs ->
            DriveState(
                linked = prefs[KEY_LINKED] ?: false,
                account = prefs[KEY_ACCOUNT] ?: "",
                clientId = prefs[KEY_CLIENT_ID] ?: "",
                accessToken = secureStore.decrypt(prefs[KEY_ACCESS_TOKEN]),
                refreshToken = secureStore.decrypt(prefs[KEY_REFRESH_TOKEN]),
                tokenExpiresAt = prefs[KEY_TOKEN_EXPIRES_AT] ?: 0L
            )
        }

    /**
     * SEC-03 — one-shot migration of legacy plaintext tokens: any stored
     * token without the `v1:` prefix is encrypted in place. Runs on first
     * collection of [state]; after migration the check is a no-op.
     * Re-reads the values inside `edit`, and never loses a token — if
     * encryption fails the plaintext is kept so Drive keeps working.
     */
    private suspend fun migrateLegacyTokens(prefs: Preferences) {
        val access = prefs[KEY_ACCESS_TOKEN]
        val refresh = prefs[KEY_REFRESH_TOKEN]
        val needsMigration = (access != null && access.isNotEmpty() && !secureStore.isEncrypted(access)) ||
            (refresh != null && refresh.isNotEmpty() && !secureStore.isEncrypted(refresh))
        if (!needsMigration) return
        runCatching {
            context.driveDataStore.edit { current ->
                val rawAccess = current[KEY_ACCESS_TOKEN]
                if (rawAccess != null && rawAccess.isNotEmpty() && !secureStore.isEncrypted(rawAccess)) {
                    current[KEY_ACCESS_TOKEN] = secureStore.encryptForStore(rawAccess, rawAccess)
                }
                val rawRefresh = current[KEY_REFRESH_TOKEN]
                if (rawRefresh != null && rawRefresh.isNotEmpty() && !secureStore.isEncrypted(rawRefresh)) {
                    current[KEY_REFRESH_TOKEN] = secureStore.encryptForStore(rawRefresh, rawRefresh)
                }
            }
        }.onFailure { Log.w(TAG, "Drive token migration failed, keeping plaintext values", it) }
    }

    suspend fun setClientId(clientId: String) {
        context.driveDataStore.edit { it[KEY_CLIENT_ID] = clientId.trim() }
    }

    /** Called by the sign-in flow once Google returns tokens for [account]. */
    suspend fun setLinked(account: String, accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        // SEC-03: tokens are stored encrypted; on encryption failure the
        // previously stored value is kept (never plaintext).
        context.driveDataStore.edit {
            it[KEY_LINKED] = true
            it[KEY_ACCOUNT] = account
            it[KEY_ACCESS_TOKEN] = secureStore.encryptForStore(accessToken, it[KEY_ACCESS_TOKEN])
            it[KEY_REFRESH_TOKEN] = secureStore.encryptForStore(refreshToken, it[KEY_REFRESH_TOKEN])
            it[KEY_TOKEN_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000L
        }
    }

    suspend fun setAccessToken(accessToken: String, expiresInSeconds: Long) {
        // SEC-03: stored encrypted (see setLinked).
        context.driveDataStore.edit {
            it[KEY_ACCESS_TOKEN] = secureStore.encryptForStore(accessToken, it[KEY_ACCESS_TOKEN])
            it[KEY_TOKEN_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000L
        }
    }

    /** Unlink: forget the account and every token. The Client ID is kept so
     *  the coach does not have to re-enter it when he links again. */
    suspend fun unlink() {
        context.driveDataStore.edit {
            it[KEY_LINKED] = false
            it[KEY_ACCOUNT] = ""
            it[KEY_ACCESS_TOKEN] = ""
            it[KEY_REFRESH_TOKEN] = ""
            it[KEY_TOKEN_EXPIRES_AT] = 0L
        }
    }
}
