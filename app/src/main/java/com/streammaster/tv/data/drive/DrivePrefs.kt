package com.streammaster.tv.data.drive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
 */
class DrivePrefs(private val context: Context) {

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
        /** True when we have a token that has not expired yet. */
        val hasValidToken: Boolean
            get() = accessToken.isNotBlank() &&
                (tokenExpiresAt == 0L || tokenExpiresAt > System.currentTimeMillis())

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

    val state: Flow<DriveState> = context.driveDataStore.data.map { prefs ->
        DriveState(
            linked = prefs[KEY_LINKED] ?: false,
            account = prefs[KEY_ACCOUNT] ?: "",
            clientId = prefs[KEY_CLIENT_ID] ?: "",
            accessToken = prefs[KEY_ACCESS_TOKEN] ?: "",
            refreshToken = prefs[KEY_REFRESH_TOKEN] ?: "",
            tokenExpiresAt = prefs[KEY_TOKEN_EXPIRES_AT] ?: 0L
        )
    }

    suspend fun setClientId(clientId: String) {
        context.driveDataStore.edit { it[KEY_CLIENT_ID] = clientId.trim() }
    }

    /** Called by the sign-in flow once Google returns tokens for [account]. */
    suspend fun setLinked(account: String, accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        context.driveDataStore.edit {
            it[KEY_LINKED] = true
            it[KEY_ACCOUNT] = account
            it[KEY_ACCESS_TOKEN] = accessToken
            it[KEY_REFRESH_TOKEN] = refreshToken
            it[KEY_TOKEN_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000L
        }
    }

    suspend fun setAccessToken(accessToken: String, expiresInSeconds: Long) {
        context.driveDataStore.edit {
            it[KEY_ACCESS_TOKEN] = accessToken
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
