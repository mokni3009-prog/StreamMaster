package com.streammaster.tv.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.RecordingStatus
import com.streammaster.tv.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "avi", "mov", "ts", "m2ts", "webm", "flv", "wmv", "mpg", "mpeg", "3gp"
)

data class UsbFileItem(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?
)

data class UsbFilesUiState(
    val files: List<UsbFileItem> = emptyList(),
    val folderName: String = "",
    val loading: Boolean = false
)

/**
 * USB local files (§4.52). Browsing is SAF (folder tree or single video
 * file); playback reuses the existing local-file path: the engine already
 * plays `content://` URIs through [PlaybackItem.RecordingItem]
 * (PlayerManagerImpl), so the picked file is saved as a [Recording] and
 * opened with the player route kind "recording". It then also appears in
 * the Recordings screen, where it can be deleted — a dedicated local-file
 * PlaybackItem would need Models.kt, which is outside this builder's scope.
 */
@HiltViewModel
class UsbFilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UsbFilesUiState())
    val state: StateFlow<UsbFilesUiState> = _state.asStateFlow()

    fun listFolder(treeUri: Uri) = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val files = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList<UsbFileItem>()
            root.listFiles()
                .filter { it.isFile && isVideo(it) }
                .mapNotNull { doc ->
                    val uri = doc.uri
                    UsbFileItem(
                        uri = uri.toString(),
                        name = doc.name ?: uri.lastPathSegment ?: uri.toString(),
                        sizeBytes = doc.length(),
                        mimeType = doc.type
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
        _state.update {
            it.copy(
                files = files,
                folderName = DocumentFile.fromTreeUri(context, treeUri)?.name ?: "",
                loading = false
            )
        }
    }

    fun pickSingleFile(uri: Uri) = viewModelScope.launch {
        val item = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val doc = DocumentFile.fromSingleUri(context, uri) ?: return@withContext null
            UsbFileItem(
                uri = uri.toString(),
                name = doc.name ?: uri.lastPathSegment ?: uri.toString(),
                sizeBytes = doc.length(),
                mimeType = doc.type
            )
        } ?: return@launch
        _state.update { it.copy(files = listOf(item), folderName = "") }
    }

    /**
     * Saves the picked file as a Recording and returns its id for the
     * player route ("recording"). Returns null if the save failed.
     * L-23 — reuses the Recording row already saved for this file
     * instead of creating a new one on every play (each play used to
     * add a phantom row to the Recordings screen).
     */
    suspend fun preparePlayback(item: UsbFileItem): String? {
        val existing = runCatching {
            recordingRepository.observeRecordings().first()
                .firstOrNull { it.filePath == item.uri }
        }.getOrNull()
        if (existing != null) return existing.id
        val recording = Recording(
            id = UUID.randomUUID().toString(),
            channelId = "usb",
            channelName = item.name,
            title = item.name,
            startMillis = System.currentTimeMillis(),
            durationMs = 0L,
            filePath = item.uri,
            sizeBytes = item.sizeBytes,
            status = RecordingStatus.DONE
        )
        return runCatching {
            recordingRepository.saveRecording(recording)
            recording.id
        }.getOrNull()
    }

    private fun isVideo(doc: DocumentFile): Boolean {
        val type = doc.type ?: ""
        if (type.startsWith("video/")) return true
        val ext = doc.name?.substringAfterLast('.', "")?.lowercase() ?: ""
        return ext in VIDEO_EXTENSIONS
    }
}
