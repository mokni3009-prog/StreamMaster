package com.streammaster.tv.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream

private const val TAG = "StreamMaster"

/**
 * Records a live stream to a .ts stream (DVR, method 2: instant only).
 *
 * The caller (RecordingManager) provides the [OutputStream] — a file in the
 * app-private folder, or a SAF document when the user chose a recording
 * folder — and owns it (opens and closes it). The recorder only writes.
 *
 * Two capture paths, both independent of the playback engine (the recorder
 * fetches the stream URL itself, in parallel with the player):
 *
 * - HLS (.m3u8): the media playlist is polled and every new TS segment is
 *   appended to the stream. Concatenated TS segments form a playable .ts file.
 *   Encrypted segments (#EXT-X-KEY) and fMP4 segments are NOT supported.
 * - Anything else (direct MPEG-TS over HTTP, …): the response body is
 *   stream-copied to the stream until it ends or the recording is stopped.
 *
 * The live HLS window is followed until [isActive] returns false; the
 * caller (RecordingManager) cancels the in-flight call via [cancelCurrent]
 * so a user Stop takes effect immediately instead of waiting for the next
 * segment / the OkHttp read timeout.
 */
class StreamRecorder(private val okHttpClient: OkHttpClient) {

    sealed interface Result {
        data class Done(val bytes: Long) : Result
        data class Failed(val reason: String) : Result
    }

    @Volatile
    private var currentCall: Call? = null

    fun cancelCurrent() {
        currentCall?.cancel()
    }

    suspend fun record(
        url: String,
        out: OutputStream,
        onBytes: (Long) -> Unit,
        isActive: () -> Boolean
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (isHls(url)) recordHls(url, out, onBytes, isActive)
            else recordRaw(url, out, onBytes, isActive)
        } catch (e: Exception) {
            Log.w(TAG, "Recording failed: ${e.message}", e)
            Result.Failed(e.message ?: "error")
        }
    }

    private fun isHls(url: String): Boolean =
        url.substringBefore('?').lowercase().endsWith(".m3u8")

    private suspend fun recordHls(
        url: String,
        out: OutputStream,
        onBytes: (Long) -> Unit,
        isActive: () -> Boolean
    ): Result {
        var playlistUrl = url
        val first = fetchText(playlistUrl) ?: return Result.Failed("playlist")
        if (first.contains("#EXT-X-STREAM-INF")) {
            playlistUrl = pickBestVariant(first, playlistUrl) ?: return Result.Failed("variant")
        }
        // L-13: the set is pruned to the last 1000 segments (a hours-long
        // recording used to keep every segment URL in memory forever), and
        // segments that keep failing (404s) end the recording with Failed
        // instead of polling the playlist silently forever.
        val downloaded = LinkedHashSet<String>()
        var totalBytes = 0L
        var failures = 0
        var segmentFailures = 0
        while (isActive()) {
            val text = fetchText(playlistUrl)
            if (text == null) {
                if (++failures >= 5) return Result.Failed("playlist")
                delay(2000)
                continue
            }
            failures = 0
            if (text.contains("#EXT-X-KEY")) return Result.Failed("encrypted")
            val targetDuration = parseTargetDuration(text)
            for (segment in parseSegments(text, playlistUrl)) {
                if (!isActive()) break
                if (!downloaded.add(segment)) continue
                if (downloaded.size > 1000) {
                    val eldest = downloaded.iterator()
                    if (eldest.hasNext()) { eldest.next(); eldest.remove() }
                }
                val bytes = fetchBytes(segment)
                if (bytes == null) {
                    if (++segmentFailures >= 10) return Result.Failed("segments")
                    continue // skip a missing segment
                }
                segmentFailures = 0
                out.write(bytes)
                totalBytes += bytes.size
                onBytes(totalBytes)
            }
            out.flush()
            delay((targetDuration * 500L).coerceIn(1000L, 6000L))
        }
        return Result.Done(totalBytes)
    }

    private suspend fun recordRaw(
        url: String,
        out: OutputStream,
        onBytes: (Long) -> Unit,
        isActive: () -> Boolean
    ): Result {
        val request = Request.Builder().url(url).build()
        val call = okHttpClient.newCall(request)
        currentCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) return Result.Failed("http ${response.code}")
            val body = response.body ?: return Result.Failed("empty")
            var total = 0L
            val source = body.source()
            val buffer = ByteArray(8192)
            while (isActive()) {
                val read = source.read(buffer) // blocks; OkHttp read timeout applies
                if (read == -1) break
                out.write(buffer, 0, read)
                total += read
                onBytes(total)
            }
            out.flush()
            return Result.Done(total)
        }
    }

    private fun fetchText(url: String): String? {
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        currentCall = call
        return call.execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }

    private fun fetchBytes(url: String): ByteArray? {
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        currentCall = call
        return call.execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }

    /** Master playlist: pick the variant with the highest BANDWIDTH. */
    private fun pickBestVariant(master: String, baseUrl: String): String? {
        val lines = master.lines()
        var bestBandwidth = -1L
        var bestUri: String? = null
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(line)
                    ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val uri = lines.getOrNull(i + 1)?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.startsWith("#") }
                if (uri != null && bw >= bestBandwidth) {
                    bestBandwidth = bw
                    bestUri = uri
                }
            }
        }
        return bestUri?.let { resolve(baseUrl, it) }
    }

    private fun parseSegments(media: String, baseUrl: String): List<String> =
        media.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(baseUrl, it) }

    private fun parseTargetDuration(media: String): Long =
        Regex("#EXT-X-TARGETDURATION:(\\d+)").find(media)
            ?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 6L

    private fun resolve(baseUrl: String, relative: String): String =
        baseUrl.toHttpUrlOrNull()?.resolve(relative)?.toString() ?: relative
}
