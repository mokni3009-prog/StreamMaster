package com.streammaster.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.RecordingStatus
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StreamMaster"

/** State of the single instant recording (DVR method 2 — one at a time). */
sealed interface RecordingState {
    data object Idle : RecordingState
    data class Active(val recording: Recording, val bytes: Long, val startedAtMillis: Long) : RecordingState
}

/**
 * Owns instant DVR recording (method 2): one recording at a time, started
 * by the REC button while watching a live channel, stopped by the same
 * button, by the foreground-service notification, or from the Enregistrements
 * screen. The recording keeps running in the background (foreground service)
 * after the user leaves the player.
 *
 * The recorder fetches the channel URL itself (StreamRecorder), in parallel
 * with the player — so recording a channel while watching another one needs
 * a second connection from the provider (Xtream max_connections). Files are
 * written to the app-private external folder `recordings/` by default, or to
 * the user-chosen folder (SAF tree URI in settings, 2026-11-22) when set —
 * in that case the recording's filePath is the document's content:// URI.
 */
@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val recordingRepository: RecordingRepository,
    settingsStore: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder = StreamRecorder(okHttpClient)

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    val recordings = recordingRepository.observeRecordings()

    private val active = AtomicBoolean(false)
    @Volatile private var current: Recording? = null

    /** User-chosen recording folder (SAF tree URI), "" = app-private folder. */
    private val _folderUri = MutableStateFlow("")

    init {
        scope.launch { settingsStore.settings.collect { _folderUri.value = it.recordingFolderUri } }
    }

    fun recordingsDir(): File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    /** Starts an instant recording of [channel]. Returns false when one is
     *  already running. */
    fun start(channel: Channel): Boolean {
        if (active.get()) return false
        val now = System.currentTimeMillis()
        val displayName = channel.customName ?: channel.name
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val fileName = "${sanitize(displayName)}_$stamp.ts"
        // User-chosen SAF folder (2026-11-22) or the app-private folder.
        val path: String = runCatching {
            val folderUri = _folderUri.value
            if (folderUri.isNotBlank()) {
                val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                val doc = tree?.createFile("video/mp2t", fileName)
                doc?.uri?.toString()
            } else null
        }.getOrNull() ?: File(recordingsDir(), fileName).absolutePath
        val recording = Recording(
            id = UUID.randomUUID().toString(),
            channelId = channel.id,
            channelName = displayName,
            title = "$displayName — ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(now))}",
            startMillis = now,
            durationMs = 0L,
            filePath = path,
            sizeBytes = 0L,
            status = RecordingStatus.RECORDING
        )
        current = recording
        active.set(true)
        _state.value = RecordingState.Active(recording, 0L, now)
        scope.launch {
            recordingRepository.saveRecording(recording)
            val intent = Intent(context, RecordingService::class.java).apply {
                putExtra(RecordingService.EXTRA_RECORDING_ID, recording.id)
                putExtra(RecordingService.EXTRA_URL, channel.streamUrl)
                putExtra(RecordingService.EXTRA_PATH, path)
                putExtra(RecordingService.EXTRA_TITLE, displayName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        return true
    }

    /** Called by [RecordingService] once it is foregrounded. [path] is a
     *  file path or a content:// URI (user-chosen folder). */
    fun runRecording(recordingId: String, url: String, path: String, onDone: () -> Unit) {
        val recording = current?.takeIf { it.id == recordingId } ?: return
        scope.launch {
            val isSaf = path.startsWith("content://")
            val stream = runCatching {
                if (isSaf) context.contentResolver.openOutputStream(Uri.parse(path))
                else FileOutputStream(File(path))
            }.getOrNull() ?: return@launch
            var lastEmit = 0L
            val result = try {
                recorder.record(
                    url, stream,
                    onBytes = { bytes ->
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 1000L) { // throttle state emissions to 1/s
                            lastEmit = now
                            val cur = _state.value
                            if (cur is RecordingState.Active) _state.value = cur.copy(bytes = bytes)
                        }
                    },
                    isActive = { active.get() }
                )
            } finally {
                runCatching { stream.close() }
            }
            active.set(false)
            finishRecording(recording, path, result)
            onDone()
        }
    }

    /** Stops the running recording (idempotent; no-op when idle). */
    fun stop() {
        if (!active.getAndSet(false)) return
        recorder.cancelCurrent()
    }

    private suspend fun finishRecording(
        recording: Recording,
        path: String,
        result: StreamRecorder.Result
    ) {
        val now = System.currentTimeMillis()
        val size = if (path.startsWith("content://")) {
            DocumentFile.fromSingleUri(context, Uri.parse(path))?.length() ?: 0L
        } else {
            val file = File(path)
            if (file.exists()) file.length() else 0L
        }
        val done = recording.copy(
            durationMs = now - recording.startMillis,
            sizeBytes = size,
            status = if (result is StreamRecorder.Result.Done) RecordingStatus.DONE else RecordingStatus.ERROR
        )
        // An empty capture is a failed capture: drop the file and the row.
        if (size <= 0L) {
            deletePath(path)
            recordingRepository.deleteRecording(recording.id)
            Log.w(TAG, "Recording discarded (empty): ${recording.title} — $result")
        } else {
            recordingRepository.saveRecording(done)
            Log.i(TAG, "Recording saved: ${done.title} (${size / 1024} KB)")
        }
        current = null
        _state.value = RecordingState.Idle
    }

    /** Deletes the recording file and its database row. */
    fun deleteRecording(recording: Recording) {
        scope.launch {
            deletePath(recording.filePath)
            recordingRepository.deleteRecording(recording.id)
        }
    }

    private fun deletePath(path: String) {
        if (path.startsWith("content://")) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete() }
        } else {
            runCatching { File(path).delete() }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(48).trim('_').ifEmpty { "chaine" }
}
