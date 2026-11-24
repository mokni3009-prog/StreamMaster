package com.streammaster.tv.player

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/**
 * Timeshift buffer (§4.45 — live TV recorded to a temp file on the device).
 *
 * While a live channel is paused, the stream keeps airing; this class
 * captures it into a temp `.ts` file under `timeshift/` (app-private
 * external files) via the existing [StreamRecorder] — the recorder fetches
 * the channel URL itself in parallel with the player, exactly like the
 * instant DVR recording in [RecordingManager]. On resume the player plays
 * the temp file from its start (= the pause point) as a
 * `PlaybackItem.RecordingItem`; "Direct" returns to the live edge and
 * deletes the temp file.
 *
 * Honest limits (same as [StreamRecorder] itself):
 * - Encrypted HLS (`#EXT-X-KEY`) and fMP4 segments are NOT recordable —
 *   [StreamRecorder] returns `Failed("encrypted")` and the caller falls
 *   back to freezing the playhead (the old behaviour).
 * - The recording is a second connection to the provider (Xtream
 *   max_connections): a provider that allows a single connection may
 *   refuse the parallel fetch, in which case the buffer stays empty and
 *   the caller falls back to the live edge.
 * - HLS capture starts from the segments in the live playlist window, so
 *   the file may begin a few seconds BEFORE the pause point; raw
 *   (non-HLS) capture starts exactly at the pause point.
 * - Only one timeshift buffer at a time; starting a new one (or zapping
 *   to another channel) discards the previous file.
 */
@Singleton
class TimeshiftBuffer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder = StreamRecorder(okHttpClient)

    private var recordJob: Job? = null
    private var out: FileOutputStream? = null

    /** The temp file being written, null when nothing is buffered.
     *  D-06: private — nothing outside this class reads it (callers use
     *  [active] / [stop] / [discard]). */
    @Volatile
    private var file: File? = null

    /** True while the recorder is running. */
    @Volatile
    var active: Boolean = false
        private set

    fun dir(): File {
        // R-11: getExternalFilesDir(null) can return null — fall back to
        // the internal files dir instead of throwing NPE.
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "timeshift").apply { mkdirs() }
    }

    /**
     * Starts capturing [url] into a fresh temp file. Returns false when a
     * capture is already active or the file cannot be created (caller then
     * keeps the freeze-only timeshift). Stale temp files are deleted.
     */
    fun start(url: String): Boolean {
        if (active) return false
        cleanupStale()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val target = File(dir(), "timeshift_$stamp.ts")
        val stream = try {
            FileOutputStream(target)
        } catch (e: Exception) {
            Log.w(TAG, "Timeshift: cannot create temp file", e)
            return false
        }
        out = stream
        file = target
        active = true
        recordJob = scope.launch {
            val result = recorder.record(url, stream, onBytes = {}, isActive = { active })
            if (result is StreamRecorder.Result.Failed) {
                Log.w(TAG, "Timeshift recording failed: ${result.reason}")
            }
        }
        return true
    }

    /**
     * Stops the capture and returns the finished temp file, or null when
     * nothing usable was captured (empty file is deleted). The file stays
     * on disk: the caller plays it, then calls [discard].
     */
    suspend fun stop(): File? {
        if (!active && file == null) return null
        active = false
        recorder.cancelCurrent()
        withTimeoutOrNull(8_000) { recordJob?.join() }
        recordJob?.cancel()
        recordJob = null
        try {
            out?.flush()
            out?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Timeshift: closing temp file failed", e)
        }
        out = null
        val finished = file
        file = null
        if (finished == null || !finished.exists() || finished.length() == 0L) {
            try {
                finished?.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Timeshift: deleting empty temp file failed", e)
            }
            return null
        }
        return finished
    }

    /** Cancels any capture and deletes the temp file (Direct / zap / stop). */
    fun discard() {
        active = false
        recorder.cancelCurrent()
        recordJob?.cancel()
        recordJob = null
        try {
            out?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Timeshift: closing temp file on discard failed", e)
        }
        out = null
        val target = file
        file = null
        if (target != null) {
            try {
                target.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Timeshift: deleting temp file failed", e)
            }
        }
    }

    /** Deletes every temp file except the one currently being written. */
    private fun cleanupStale() {
        val current = file
        try {
            dir().listFiles()?.forEach { f ->
                if (f != current) f.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Timeshift: stale cleanup failed", e)
        }
    }
}
