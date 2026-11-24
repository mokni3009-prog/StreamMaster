package com.streammaster.tv.data.subtitles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * SPEC §4.48 — Arabic subtitle download (OpenSubtitles) + local storage.
 * SPEC §4.61 — the local file found here is what the player auto-enables.
 *
 * Storage: `files/subtitles/<itemId>.srt` (internal storage, one file per
 * content item; re-download overwrites). `itemId` is sanitised to
 * `[A-Za-z0-9_-]` so a playlist id can never escape the folder.
 *
 * ### Contract for the Detail builder (DetailScreen)
 * ```kotlin
 * SubtitleDownloadButton(itemType = "film", itemId = film.id, title = film.title, year = film.year)
 * ```
 * `itemType` is informational only ("film" | "series" | "episode"); the
 * file name depends on `itemId` alone.
 *
 * ### Contract for the Player builder (player engine, §4.61)
 * ```kotlin
 * val local = context.findLocalSubtitle(itemId)   // File? — null when none
 * if (local != null) player.addSubtitle(local)    // ExoPlayer: MediaItem subtitle config
 * ```
 */
class SubtitleDownloader(
    private val context: Context,
    private val prefs: SubtitlePrefs = SubtitlePrefs(context),
    private val service: OpenSubtitlesService = openSubtitlesService(),
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    sealed interface DownloadResult {
        data class Ok(val file: File) : DownloadResult
        data object NoApiKey : DownloadResult
        data object NotFound : DownloadResult
        /** The API refused the download link (anonymous key quota / login needed). */
        data object NeedsLogin : DownloadResult
        data class Error(val message: String) : DownloadResult
    }

    /**
     * Search Arabic subtitles for [title] (+ [year] when known) and
     * download the most-downloaded one into `files/subtitles/<itemId>.srt`.
     */
    suspend fun downloadBest(title: String, year: Int?, itemId: String): DownloadResult =
        withContext(Dispatchers.IO) {
            val apiKey = prefs.apiKey.first()
            if (apiKey.isBlank()) return@withContext DownloadResult.NoApiKey
            runCatching {
                // OP-09: most Xtream films carry year = 0 (unknown) — sending
                // year=0 makes OpenSubtitles return nothing, so send null.
                val response = service.search(apiKey, query = title, year = year?.takeIf { it > 0 })
                val best = response.data
                    .flatMap { entry -> (entry.attributes?.files ?: emptyList()) }
                    .firstOrNull()
                    ?: return@withContext DownloadResult.NotFound
                val link = try {
                    service.downloadLink(apiKey, DownloadRequest(best.fileId)).link
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401 || e.code() == 403) {
                        return@withContext DownloadResult.NeedsLogin
                    }
                    throw e
                }
                if (link.isBlank()) return@withContext DownloadResult.NotFound
                val request = Request.Builder().url(link).build()
                val body = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext DownloadResult.Error("HTTP ${resp.code}")
                    }
                    resp.body?.bytes() ?: return@withContext DownloadResult.NotFound
                }
                val outFile = localSubtitleFile(context, itemId)
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(body)
                DownloadResult.Ok(outFile)
            }.getOrElse { e ->
                if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                    DownloadResult.NeedsLogin
                } else {
                    DownloadResult.Error(e.message ?: "Subtitle download failed")
                }
            }
        }

    /** True when an Arabic .srt was already downloaded for [itemId]. */
    fun hasLocalSubtitle(itemId: String): Boolean =
        localSubtitleFile(context, itemId).let { it.exists() && it.length() > 0 }

    companion object {
        const val SUBTITLES_DIR = "subtitles"

        fun sanitizeItemId(itemId: String): String =
            itemId.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }.joinToString("")

        fun localSubtitleFile(context: Context, itemId: String): File =
            File(File(context.filesDir, SUBTITLES_DIR), "${sanitizeItemId(itemId)}.srt")
    }
}

/**
 * Find the locally downloaded Arabic subtitle for [itemId], if any.
 * Used by the player engine (§4.61) to auto-enable it at playback start.
 * Returns null when no .srt was downloaded for this item.
 */
fun Context.findLocalSubtitle(itemId: String): File? {
    val file = SubtitleDownloader.localSubtitleFile(this, itemId)
    return file.takeIf { it.exists() && it.length() > 0 }
}
