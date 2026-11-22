package com.streammaster.tv.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.player.remote.RemoteServerManager
import com.streammaster.tv.ui.util.generateQrBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phone web remote (2026-11-22): the Télécommande screen — enable/disable
 * the local server, show the pairing QR code + URL + 6-digit code, and
 * regenerate the code. The server itself starts/stops automatically from
 * the persisted setting (see RemoteServerManager).
 */
@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val remoteServerManager: RemoteServerManager
) : ViewModel() {
    val remoteEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.remoteEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val remoteCode: StateFlow<String> = settingsRepository.settings
        .map { it.remoteCode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val running: StateFlow<Boolean> = remoteServerManager.running
    val localIp: StateFlow<String> = remoteServerManager.localIp
    val port: StateFlow<Int> = remoteServerManager.port

    /** Pairing URL (QR + text); "" while the server is not running. */
    val pairingUrl: StateFlow<String> = combine(
        remoteCode, localIp, port, running
    ) { code, ip, p, run ->
        if (!run || ip.isEmpty() || p == 0 || code.isBlank()) "" else "http://$ip:$p/?code=$code"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val qrBitmap: StateFlow<Bitmap?> = pairingUrl
        .map { url ->
            if (url.isEmpty()) null
            else withContext(Dispatchers.Default) { generateQrBitmap(url) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update {
            it.copy(
                remoteEnabled = enabled,
                remoteCode = if (it.remoteCode.isBlank()) newCode() else it.remoteCode
            )
        }
    }

    fun regenerateCode() = viewModelScope.launch {
        settingsRepository.update { it.copy(remoteCode = newCode()) }
    }

    private fun newCode(): String = (100000..999999).random().toString()
}
