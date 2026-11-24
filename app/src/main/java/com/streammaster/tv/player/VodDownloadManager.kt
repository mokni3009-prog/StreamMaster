package com.streammaster.tv.player

import android.content.Context
import android.util.Log
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.Download
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline VOD downloads (2026-11-22): sequential queue, one file at a
 * time, resumable via HTTP Range. Files land in the app-private external
 * `downloads/` folder (no storage permission needed, deleted with the app).
 *
 * Only direct file URLs are supported (mp4/mkv/ts…). HLS playlists
 * (.m3u8) are refused by the caller — a playlist is not a file.
 * A download keeps running while the app is open; [DownloadService]
 * holds a foreground notification so the system does not kill it.
 * If the app itself is killed, QUEUED/DOWNLOADING rows stay in the DB
 * and resume from the bytes already on disk at the next start.
 */
@Singleton
class VodDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadRepository: DownloadRepository
) {
    companion object {
        private const val TAG = "VodDownload"
        private const val EMIT_EVERY_MS = 500L
        private const val EMIT_EVERY_BYTES = 256 * 1024L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var currentId: String? = null

    /** Queue (or re-queue) a VOD download. No-op when already downloaded. */
    fun startDownload(kind: ContentKind, refId: String, title: String, coverUrl: String?, url: String) {
        scope.launch {
            val existing = downloadRepository.getByRef(kind, refId)
            if (existing != null) {
                if (existing.status == DownloadStatus.DONE) return@launch
                // Re-queue (paused, failed, or a second tap while queued).
                downloadRepository.updateProgress(
                    existing.id, existing.downloadedBytes, existing.totalBytes, DownloadStatus.QUEUED
                )
            } else {
                val ext = url.substringAfterLast('.', "mp4").substringBefore('?')
                    .takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } } ?: "mp4"
                val safeTitle = title.replace(Regex("[^A-Za-z0-9À-ÿ _\\-]"), "").trim()
                    .replace(Regex("\\s+"), "_").take(60).ifBlank { "video" }
                val fileName = "${safeTitle}_${refId.hashCode().toString(16)}.$ext"
                // R-11: getExternalFilesDir(null) can return null — fall back
                // to the internal files dir instead of throwing NPE.
                val base = context.getExternalFilesDir(null) ?: context.filesDir
                val dir = File(base, "downloads").apply { mkdirs() }
                val row = Download(
                    id = "dl-${kind.name}-$refId", kind = kind, refId = refId, title = title,
                    coverUrl = coverUrl, fileName = fileName,
                    filePath = File(dir, fileName).absolutePath, sourceUrl = url,
                    totalBytes = -1, downloadedBytes = 0,
                    status = DownloadStatus.QUEUED, createdMillis = System.currentTimeMillis()
                )
                downloadRepository.save(row)
            }
            ensureLoop()
        }
    }

    /** Pause: marks the row PAUSED first (so the download loop's catch
     *  sees PAUSED, not a failure), then cancels the running call; the
     *  bytes on disk are kept for resume. */
    fun pause(id: String) {
        scope.launch {
            val row = downloadRepository.getDownload(id) ?: return@launch
            if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.QUEUED) {
                downloadRepository.updateProgress(id, row.downloadedBytes, row.totalBytes, DownloadStatus.PAUSED)
            }
            if (currentId == id) currentCall?.cancel()
        }
    }

    fun resume(id: String) {
        scope.launch {
            val row = downloadRepository.getDownload(id) ?: return@launch
            if (row.status == DownloadStatus.PAUSED || row.status == DownloadStatus.ERROR) {
                downloadRepository.updateProgress(id, row.downloadedBytes, row.totalBytes, DownloadStatus.QUEUED)
                ensureLoop()
            }
        }
    }

    /** Cancels (if active) and deletes the file + the row. */
    fun delete(id: String) {
        scope.launch {
            if (currentId == id) currentCall?.cancel()
            val row = downloadRepository.getDownload(id)
            if (row != null) runCatching { File(row.filePath).delete() }
            downloadRepository.delete(id)
        }
    }

    /** Cancels the download currently being written, if any. L-08: marks
     *  the row PAUSED first (user-cancelled, not a failure) and remembers
     *  the id so the download loop's catch — which may run before the DB
     *  write lands — also treats the abort as a clean pause, not ERROR. */
    fun cancelActive() {
        val id = currentId
        if (id != null) {
            userCancelledId = id
            scope.launch {
                runCatching { downloadRepository.getDownload(id) }.getOrNull()?.let { row ->
                    if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.QUEUED) {
                        downloadRepository.updateProgress(
                            id, row.downloadedBytes, row.totalBytes, DownloadStatus.PAUSED
                        )
                    }
                }
            }
        }
        currentCall?.cancel()
    }

    @Volatile
    private var userCancelledId: String? = null

    private fun ensureLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (true) {
                val next = downloadRepository.getNextQueued() ?: break
                DownloadService.start(context)
                downloadOne(next)
            }
            DownloadService.stop(context)
        }
    }

    private suspend fun downloadOne(row: Download) {
        val file = File(row.filePath)
        var downloaded = if (file.exists()) file.length() else 0L
        currentId = row.id
        try {
            downloadRepository.updateProgress(row.id, downloaded, row.totalBytes, DownloadStatus.DOWNLOADING)
            val request = Request.Builder().url(row.sourceUrl).apply {
                if (downloaded > 0) header("Range", "bytes=$downloaded-")
            }.build()
            val call = okHttpClient.newCall(request)
            currentCall = call
            val response = call.execute()
            response.use { resp ->
                if (resp.code == 416) {
                    // Range beyond EOF: the file is already complete.
                    downloadRepository.updateProgress(row.id, downloaded, downloaded, DownloadStatus.DONE)
                    return
                }
                if (!resp.isSuccessful) {
                    downloadRepository.updateProgress(row.id, downloaded, row.totalBytes, DownloadStatus.ERROR)
                    return
                }
                val body = resp.body ?: run {
                    downloadRepository.updateProgress(row.id, downloaded, row.totalBytes, DownloadStatus.ERROR)
                    return
                }
                var total = row.totalBytes
                if (resp.code == 200 && downloaded > 0) {
                    // Server ignored Range: restart from scratch.
                    file.delete()
                    downloaded = 0
                    total = body.contentLength()
                } else if (resp.code == 206) {
                    total = downloaded + body.contentLength()
                } else if (total < 0) {
                    total = body.contentLength()
                }
                var lastEmitBytes = downloaded
                var lastEmitMs = System.currentTimeMillis()
                body.byteStream().use { input ->
                    FileOutputStream(file, downloaded > 0).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (downloaded - lastEmitBytes >= EMIT_EVERY_BYTES || now - lastEmitMs >= EMIT_EVERY_MS) {
                                lastEmitBytes = downloaded
                                lastEmitMs = now
                                downloadRepository.updateProgress(row.id, downloaded, total, DownloadStatus.DOWNLOADING)
                            }
                        }
                    }
                }
                downloadRepository.updateProgress(row.id, downloaded, total, DownloadStatus.DONE)
            }
        } catch (e: Exception) {
            // Pause/cancel aborts the call: the row already says PAUSED
            // (or is gone) — anything else is a real failure.
            val current = runCatching { downloadRepository.getDownload(row.id) }.getOrNull()
            if (current == null) return
            if (current.status == DownloadStatus.PAUSED || userCancelledId == row.id) {
                // L-08: user-cancelled from the notification — leave the row
                // PAUSED (resumable) instead of ERROR.
                downloadRepository.updateProgress(row.id, downloaded, row.totalBytes, DownloadStatus.PAUSED)
                return
            }
            Log.w(TAG, "download ${row.id} failed", e)
            downloadRepository.updateProgress(row.id, downloaded, row.totalBytes, DownloadStatus.ERROR)
        } finally {
            if (currentId == row.id) {
                currentId = null
                currentCall = null
            }
            if (userCancelledId == row.id) userCancelledId = null
        }
    }
}
