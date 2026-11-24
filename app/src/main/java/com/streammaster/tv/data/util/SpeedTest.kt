package com.streammaster.tv.data.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * §4.47 — download speed test (2026-11-23).
 *
 * Downloads a test file over the OkHttp stack already in the app and
 * measures bytes / time. No paid server, no API key: the default URL is
 * Cloudflare's public speed-test endpoint, which serves an arbitrary
 * number of bytes (`__down?bytes=N`) for exactly this purpose. The
 * download stops at [MAX_DURATION_MS] (30 s) so a slow link does not
 * hang the test — the result is computed from the bytes actually read.
 *
 * Cancellable: [cancel] (or coroutine cancellation) aborts the download
 * and [measure] throws [CancellationException].
 */
class SpeedTest(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    data class Result(
        /** Measured download speed, megabits per second. */
        val mbps: Double,
        val bytesDownloaded: Long,
        val durationMs: Long
    )

    companion object {
        /** 20 MB requested; the read loop also stops at [MAX_DURATION_MS]. */
        const val TEST_BYTES = 20_000_000L

        /** Cloudflare public speed-test endpoint (free, no key). */
        const val DEFAULT_TEST_URL = "https://speed.cloudflare.com/__down?bytes=$TEST_BYTES"

        /** Hard cap for one test run. */
        const val MAX_DURATION_MS = 30_000L

        /** Minimum time before a result is accepted (avoids a 1-chunk fluke). */
        const val MIN_DURATION_MS = 2_000L
    }

    /**
     * LG-20: the cancel flag used to be ONE shared field — two concurrent
     * measures on the same instance cancelled each other. Each measure now
     * owns its flag; cancel() targets the latest run.
     */
    @Volatile
    private var currentCancel: AtomicBoolean? = null

    fun cancel() {
        currentCancel?.set(true)
    }

    suspend fun measure(
        url: String = DEFAULT_TEST_URL,
        onProgress: ((bytesRead: Long) -> Unit)? = null
    ): Result = withContext(Dispatchers.IO) {
        val cancelled = AtomicBoolean(false)
        currentCancel = cancelled
        try {
            val request = Request.Builder().url(url).build()
            val startMs = System.currentTimeMillis()
            var bytesRead = 0L
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("speed test HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("empty speed test body")
                val buffer = ByteArray(32 * 1024)
                body.byteStream().use { stream ->
                    while (true) {
                        coroutineContext.ensureActive()
                        if (cancelled.get()) throw CancellationException("speed test cancelled")
                        val elapsed = System.currentTimeMillis() - startMs
                        if (elapsed >= MAX_DURATION_MS) break
                        val read = stream.read(buffer)
                        if (read < 0) break
                        bytesRead += read
                        onProgress?.invoke(bytesRead)
                    }
                }
            }
            var durationMs = System.currentTimeMillis() - startMs
            if (durationMs < MIN_DURATION_MS) {
                // A tiny/fast download still counts, but do not divide by ~0.
                durationMs = MIN_DURATION_MS
            }
            val mbps = bytesRead * 8.0 / (durationMs / 1000.0) / 1_000_000.0
            Result(mbps = mbps, bytesDownloaded = bytesRead, durationMs = durationMs)
        } finally {
            if (currentCancel === cancelled) currentCancel = null
        }
    }
}
