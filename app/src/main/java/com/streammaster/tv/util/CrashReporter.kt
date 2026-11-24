package com.streammaster.tv.util

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.streammaster.tv.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SPEC §4.66 — instant automatic crash reports on our own server.
 *
 * A fatal crash is written to a file in the app-private `crash-queue/`
 * directory and uploaded on the next launch; caught exceptions can be
 * reported with [reportCaught]. Reports go to
 * `https://streammaster-crash-reporter.streammaster.workers.dev`
 * (Cloudflare Worker + D1) and are deleted from the queue only after the
 * server answers `{"ok":true}` — a dead network never loses a report.
 *
 * PRIVACY: a report carries ONLY the seven fields the server expects
 * (app version/code, device model, Android version, exception type,
 * message, stack trace). No playlist credentials, no usernames, no
 * passwords, no tokens, no user data of any kind.
 *
 * The reporter must never crash the app itself: every entry point is
 * wrapped in try/catch, and the uncaught handler always chains to the
 * previous handler (or kills the process when there is none).
 *
 * Silent and automatic: no UI, no settings, no user-facing strings.
 */
object CrashReporter {

    private const val ENDPOINT = "https://streammaster-crash-reporter.streammaster.workers.dev"
    private const val QUEUE_DIR_NAME = "crash-queue"

    /** Maximum queued report files; oldest are dropped first. */
    private const val MAX_QUEUE_FILES = 50

    private const val MAX_MESSAGE_CHARS = 1000
    private const val MAX_STACK_CHARS = 8000

    /** Backoff before the single retry of a failed upload within one run. */
    private const val RETRY_DELAY_MS = 2000L

    private const val TIMEOUT_SECONDS = 10L

    private val installed = AtomicBoolean(false)
    private val fileCounter = AtomicLong(0)

    @Volatile
    private var appContext: Context? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Single daemon thread: uploads serialize and never block a caller. */
    private val uploader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "crash-reporter").apply { isDaemon = true }
    }

    /**
     * Installs the uncaught-exception handler and uploads any reports
     * queued by a previous run. Call once from StreamMasterApp.onCreate.
     * Safe to call twice; safe to call before anything else exists.
     */
    fun install(context: Context) {
        try {
            if (!installed.compareAndSet(false, true)) return
            appContext = context.applicationContext
            queueDir()?.mkdirs()
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    enqueue(throwable, "fatal")
                } catch (_: Throwable) {
                    // Never let the reporter interfere with the crash itself.
                }
                try {
                    previous?.uncaughtException(thread, throwable)
                } catch (_: Throwable) {
                    // The previous handler is gone; fall through to kill below.
                }
                // A fatal crash must still terminate the app: the previous
                // handler normally kills the process itself (and never
                // returns); if it returned, do it here.
                Process.killProcess(Process.myPid())
            }
            triggerUpload()
        } catch (_: Throwable) {
            // Reporter must never crash the app.
        }
    }

    /**
     * Reports a caught exception (non-fatal): queues it and uploads the
     * queue. [tag] is a short call-site label, kept inside the message
     * field because the server schema has no tag field.
     */
    fun reportCaught(throwable: Throwable, tag: String = "caught") {
        try {
            enqueue(throwable, tag)
            triggerUpload()
        } catch (_: Throwable) {
            // Reporter must never crash the app.
        }
    }

    private fun triggerUpload() {
        try {
            uploader.execute { uploadQueued() }
        } catch (_: Throwable) {
            // Reporter must never crash the app.
        }
    }

    private fun queueDir(): File? {
        return try {
            appContext?.let { File(it.filesDir, QUEUE_DIR_NAME) }
        } catch (_: Throwable) {
            null
        }
    }

    /** Writes one report file, then drops the oldest files past the cap. */
    private fun enqueue(throwable: Throwable, tag: String) {
        try {
            val dir = queueDir() ?: return
            dir.mkdirs()
            val json = buildReportJson(throwable, tag)
            val file = File(
                dir,
                "crash-${System.currentTimeMillis()}-${fileCounter.incrementAndGet()}.json"
            )
            file.writeText(json)
            enforceCap(dir)
        } catch (_: Throwable) {
            // A failed write is dropped silently; the app must go on.
        }
    }

    private fun enforceCap(dir: File) {
        try {
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
            if (files.size <= MAX_QUEUE_FILES) return
            files.take(files.size - MAX_QUEUE_FILES).forEach { file ->
                runCatching { file.delete() }
            }
        } catch (_: Throwable) {
            // Reporter must never crash the app.
        }
    }

    /**
     * Sends queued reports oldest first. A file is deleted only after the
     * server answers `{"ok":true}`. On failure the file is retried once
     * after a short backoff; if it still fails, this run stops and the
     * file (plus the rest) stays queued for the next launch/report.
     */
    private fun uploadQueued() {
        try {
            val dir = queueDir() ?: return
            val files = dir.listFiles()?.sortedBy { it.name } ?: return
            for (file in files) {
                val json = runCatching { file.readText() }.getOrNull()
                if (json.isNullOrBlank()) {
                    runCatching { file.delete() }
                    continue
                }
                var sent = postReport(json)
                if (!sent) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (_: InterruptedException) {
                        // Interrupted while backing off: retry once anyway.
                    }
                    sent = postReport(json)
                }
                if (sent) {
                    runCatching { file.delete() }
                } else {
                    // Leave this file and the rest queued for the next run.
                    return
                }
            }
        } catch (_: Throwable) {
            // Reporter must never crash the app.
        }
    }

    /** POSTs one report; true only on HTTP 200 with `{"ok":true}`. */
    private fun postReport(json: String): Boolean {
        return try {
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(ENDPOINT).post(body).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val responseBody = response.body?.string().orEmpty()
                responseBody.contains("\"ok\":true")
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * The seven server fields only. The message is the exception message
     * (optionally prefixed with the call-site tag); the stack trace is the
     * full trace via [Log.getStackTraceString]. Both are truncated.
     */
    private fun buildReportJson(throwable: Throwable, tag: String): String {
        val rawMessage = throwable.message ?: ""
        val message = if (tag.isNotBlank()) "[$tag] $rawMessage" else rawMessage
        val errorType = throwable.javaClass.simpleName?.takeIf { it.isNotBlank() } ?: "Unknown"
        val json = JSONObject()
        json.put("app_version", BuildConfig.VERSION_NAME)
        json.put("version_code", BuildConfig.VERSION_CODE)
        json.put("device_model", Build.MODEL ?: "unknown")
        json.put("android_version", Build.VERSION.RELEASE ?: "unknown")
        json.put("error_type", errorType)
        json.put("message", message.take(MAX_MESSAGE_CHARS))
        json.put("stack_trace", Log.getStackTraceString(throwable).take(MAX_STACK_CHARS))
        return json.toString()
    }
}
