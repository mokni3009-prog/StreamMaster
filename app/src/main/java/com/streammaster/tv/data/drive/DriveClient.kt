package com.streammaster.tv.data.drive

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val TAG = "StreamMaster"

/**
 * SPEC §4.63 — Google Drive client: the app's backup JSON
 * ([com.streammaster.tv.data.backup.BackupManager]) stored on the user's
 * Drive as a second destination, next to the local SAF export.
 *
 * File used on Drive: [BACKUP_FILE_NAME] (one file, overwritten on each
 * backup — no APK is ever uploaded; APKs stay on GitHub Releases).
 *
 * ### What is built here, and what is NOT (blocking on the coach)
 * Built: list / upload / download / delete of the backup JSON over the
 * Drive v3 REST API with a Bearer token from [DrivePrefs].
 * NOT built: obtaining the Bearer token. That needs, in order:
 *  1. the coach creates OAuth credentials in HIS Google Cloud Console
 *     (project + consent screen + Android Client ID with the app SHA-1),
 *     enables the Drive API and the `drive.file` scope;
 *  2. the coach enters that Client ID in [DriveScreen] (stored in
 *     [DrivePrefs], never hard-coded);
 *  3. a sign-in step on the device (Google Sign-In / Credential Manager)
 *     exchanges the Client ID for tokens — that step needs the
 *     `play-services-auth` dependency, which this task deliberately did
 *     NOT add (only the Chromecast dependency was authorised), so
 *     [linkWithTokens] is the seam the sign-in builder calls once the
 *     auth library is added. Until then [uploadBackup]/[downloadBackup]
 *     return [DriveResult.NotConfigured] / [DriveResult.NotLinked].
 *
 * Token refresh (OP-18): [bearer] renews an expired access token from the
 * stored refresh token (Google's oauth2 token endpoint, public Android
 * client — client_id only, no secret) before giving up with NotLinked.
 */
// CS-06: this client is built MANUALLY by DriveScreen (DriveClient(prefs)),
// never injected by Hilt — so it must not carry @Inject (Hilt has no
// binding for DriveService/DrivePrefs and the first @Inject attempt would
// fail the build). Keep it a plain class.
class DriveClient(
    private val prefs: DrivePrefs,
    private val service: DriveService = driveService(),
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        /** The single backup file on Drive. Small JSON only — never an APK. */
        const val BACKUP_FILE_NAME = "stream-master-backup.json"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }

    sealed interface DriveResult {
        data class Ok(val file: DriveFile? = null) : DriveResult
        /** No OAuth Client ID has been entered yet (coach setup pending). */
        data object NotConfigured : DriveResult
        /** Client ID exists but no (valid) token — sign-in not done yet. */
        data object NotLinked : DriveResult
        data class Error(val message: String) : DriveResult
    }

    private suspend fun bearer(): String? {
        val state = prefs.state.first()
        if (state.needsOAuthSetup) return null
        if (state.hasValidToken) return "Bearer ${state.accessToken}"
        // OP-18: expired access token + stored refresh token → renew it
        // instead of staying NotLinked forever (tokens live ~1h).
        return refreshAccessToken(state)
    }

    /** Exchanges the stored refresh token for a new access token (Google
     *  OAuth2 token endpoint). Returns the new "Bearer …" value, or null
     *  when the refresh fails (revoked token, no network, bad client id) —
     *  the caller then reports NotLinked as before. */
    private suspend fun refreshAccessToken(state: DrivePrefs.DriveState): String? {
        if (state.refreshToken.isBlank() || state.clientId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = FormBody.Builder()
                    .add("client_id", state.clientId)
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", state.refreshToken)
                    .build()
                val req = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(body)
                    .build()
                val jsonText = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.string() ?: return@runCatching null
                }
                val json = JSONObject(jsonText)
                val token = json.optString("access_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)
                if (token.isBlank()) return@runCatching null
                prefs.setAccessToken(token, expiresIn)
                "Bearer $token"
            }.onFailure { Log.w(TAG, "Drive token refresh failed", it) }
                .getOrNull()
        }
    }

    /**
     * Seam for the future sign-in step: stores the tokens Google returns
     * for [account] and marks Drive as linked. Called by the auth builder,
     * not by the UI directly.
     */
    suspend fun linkWithTokens(account: String, accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        prefs.setLinked(account, accessToken, refreshToken, expiresInSeconds)
    }

    suspend fun unlink() {
        prefs.unlink()
    }

    /**
     * Upload [backupJson] (from `BackupManager.export()`) to Drive,
     * creating [BACKUP_FILE_NAME] or overwriting it if it already exists.
     */
    suspend fun uploadBackup(backupJson: String): DriveResult {
        val state = prefs.state.first()
        if (state.needsOAuthSetup) return DriveResult.NotConfigured
        val auth = bearer() ?: return DriveResult.NotLinked
        return runCatching {
            val existing = findBackupFile(auth)
            val file = if (existing != null) {
                service.updateFileContent(
                    auth,
                    existing.id,
                    backupJson.toRequestBody("application/json; charset=utf-8".toMediaType())
                )
            } else {
                service.uploadFile(
                    auth,
                    jsonMetadataPart(BACKUP_FILE_NAME),
                    jsonMediaPart(BACKUP_FILE_NAME, backupJson)
                )
            }
            DriveResult.Ok(file)
        }.getOrElse { DriveResult.Error(it.message ?: "Drive upload failed") }
    }

    /**
     * Download the backup JSON from Drive (to feed `BackupManager.import()`).
     * Returns null when no backup file exists on Drive yet.
     */
    suspend fun downloadBackup(): Pair<DriveResult, String?> {
        val state = prefs.state.first()
        if (state.needsOAuthSetup) return DriveResult.NotConfigured to null
        val auth = bearer() ?: return DriveResult.NotLinked to null
        return runCatching {
            val existing = findBackupFile(auth) ?: return DriveResult.Ok(null) to null
            val body = service.downloadFile(
                auth,
                "https://www.googleapis.com/drive/v3/files/${existing.id}?alt=media"
            )
            DriveResult.Ok(existing) to body.string()
        }.getOrElse { DriveResult.Error(it.message ?: "Drive download failed") to null }
    }

    /** Delete the backup file from Drive (unlink keeps the file; this removes it). */
    suspend fun deleteBackup(): DriveResult {
        val state = prefs.state.first()
        if (state.needsOAuthSetup) return DriveResult.NotConfigured
        val auth = bearer() ?: return DriveResult.NotLinked
        return runCatching {
            val existing = findBackupFile(auth) ?: return DriveResult.Ok(null)
            service.deleteFile(auth, existing.id)
            DriveResult.Ok(existing)
        }.getOrElse { DriveResult.Error(it.message ?: "Drive delete failed") }
    }

    private suspend fun findBackupFile(auth: String): DriveFile? {
        val list = service.listFiles(auth, "name = '$BACKUP_FILE_NAME' and trashed = false")
        return list.files.firstOrNull()
    }
}
