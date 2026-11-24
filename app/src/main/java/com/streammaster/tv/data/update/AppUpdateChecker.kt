package com.streammaster.tv.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val available: Boolean,
    val version: String,
    val changelog: String,
    val downloadUrl: String,
    /** Remote versionCode (0 when the JSON carries none). */
    val versionCode: Int = 0
)

private data class UpdateJson(
    @SerializedName("version") val version: String?,
    @SerializedName("versionCode") val versionCode: Int?,
    @SerializedName("changelog") val changelog: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("downloadUrl") val downloadUrl: String?
)

/**
 * Checks for a newer app version. The update URL defaults to the public
 * `update.json` at the repo root (§4.25b); NEVER forces an update — the
 * user is only informed, per SPEC §10.
 *
 * Expected JSON at the update URL:
 *   {"version": "1.3.0", "versionCode": 130, "changelog": "...", "url": "https://...apk"}
 */
// §4.25b (2026-11-23): public update file on GitHub (raw). The file
// lives at the repo root as `update.json`; its versionCode must stay in
// sync with the latest published Release.
const val DEFAULT_UPDATE_URL =
    "https://raw.githubusercontent.com/mokni3009-prog/StreamMaster/main/update.json"

@Singleton
class AppUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    // OP-11: default must match the shipped versionCode (build.gradle.kts:
    // 121) — callers that omit it used to compare the remote code against
    // 120 and report 121 as "newer" than itself.
    suspend fun check(updateUrl: String = DEFAULT_UPDATE_URL, currentVersionCode: Int = 121): UpdateInfo =
        withContext(Dispatchers.IO) {
            val none = UpdateInfo(false, "", "", "")
            if (updateUrl.isBlank()) return@withContext none
            val req = Request.Builder().url(updateUrl).build()
            val resp = runCatching { okHttpClient.newCall(req).execute() }.getOrNull()
                ?: return@withContext none
            resp.use {
                if (!it.isSuccessful) return@withContext none
                val body = it.body?.string() ?: return@withContext none
                val json = runCatching { gson.fromJson(body, UpdateJson::class.java) }.getOrNull()
                    ?: return@withContext none
                val remoteCode = json.versionCode
                    ?: json.version?.let { parseVersionCode(it) }
                    ?: return@withContext none
                if (remoteCode <= currentVersionCode) return@withContext none
                UpdateInfo(
                    available = true,
                    version = json.version ?: remoteCode.toString(),
                    changelog = json.changelog ?: "",
                    downloadUrl = json.downloadUrl ?: json.url ?: "",
                    versionCode = remoteCode
                )
            }
        }

    /** "1.3.0" -> 130 (major*100 + minor*10 + patch), best effort. */
    private fun parseVersionCode(version: String): Int? {
        val parts = version.trim().split(".").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 100 + minor * 10 + patch
    }
}
