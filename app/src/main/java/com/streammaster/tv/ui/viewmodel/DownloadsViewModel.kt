package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Download
import com.streammaster.tv.data.repository.DownloadRepository
import com.streammaster.tv.player.VodDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Downloads — offline VOD files: play, pause/resume, delete (2026-11-22). */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val downloadManager: VodDownloadManager
) : ViewModel() {

    val downloads: StateFlow<List<Download>> = downloadRepository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pause(download: Download) = downloadManager.pause(download.id)

    fun resume(download: Download) = downloadManager.resume(download.id)

    fun delete(download: Download) = viewModelScope.launch {
        downloadManager.delete(download.id)
    }

    fun retry(download: Download) = downloadManager.resume(download.id)
}
