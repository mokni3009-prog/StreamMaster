package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class LoginTab { XTREAM, STALKER, M3U }

data class LoginUiState(
    val tab: LoginTab = LoginTab.XTREAM,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val portalUrl: String = "",
    val mac: String = "",
    val m3uUrl: String = "",
    val playlistName: String = "",
    val loading: Boolean = false,
    val errorRes: Int? = null,
    val success: Boolean = false
)

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setTab(tab: LoginTab) = _state.update { it.copy(tab = tab, errorRes = null) }
    fun setServerUrl(v: String) = _state.update { it.copy(serverUrl = v) }
    fun setUsername(v: String) = _state.update { it.copy(username = v) }
    fun setPassword(v: String) = _state.update { it.copy(password = v) }
    fun setPortalUrl(v: String) = _state.update { it.copy(portalUrl = v) }
    fun setMac(v: String) = _state.update { it.copy(mac = v) }
    fun setM3uUrl(v: String) = _state.update { it.copy(m3uUrl = v) }
    fun setPlaylistName(v: String) = _state.update { it.copy(playlistName = v) }
    fun clearError() = _state.update { it.copy(errorRes = null) }

    /** Validates the active tab, then persists the playlist and syncs it
     *  from the server at once, so the Loading screen that follows already
     *  has content and goes home. If the sync fails the playlist is removed
     *  again so a retry starts clean. On success -> Loading screen. */
    fun submit() {
        val s = _state.value
        val error = when (s.tab) {
            LoginTab.XTREAM -> if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank())
                com.streammaster.tv.R.string.login_error_url else null
            LoginTab.STALKER -> when {
                s.portalUrl.isBlank() -> com.streammaster.tv.R.string.login_error_url
                !MAC_REGEX.matches(s.mac.trim()) -> com.streammaster.tv.R.string.login_error_mac
                else -> null
            }
            LoginTab.M3U -> if (s.m3uUrl.isBlank() || !s.m3uUrl.startsWith("http"))
                com.streammaster.tv.R.string.login_error_url else null
        }
        if (error != null) { _state.update { it.copy(errorRes = error) }; return }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorRes = null) }
            try {
                val playlist = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = s.playlistName.ifBlank {
                        when (s.tab) {
                            LoginTab.XTREAM -> s.username
                            LoginTab.STALKER -> "Stalker"
                            LoginTab.M3U -> "M3U"
                        }
                    },
                    type = when (s.tab) {
                        LoginTab.XTREAM -> PlaylistType.XTREAM
                        LoginTab.STALKER -> PlaylistType.STALKER
                        LoginTab.M3U -> PlaylistType.M3U
                    },
                    serverUrl = s.serverUrl, username = s.username, password = s.password,
                    portalUrl = s.portalUrl, mac = s.mac.trim().uppercase(),
                    m3uUrl = s.m3uUrl, isActive = true,
                    expiryDate = "", connections = 1, lastUpdate = "", status = "active"
                )
                val id = playlistRepository.addPlaylist(playlist)
                // Sync the new playlist at once: without this the Loading
                // screen that follows waits for content that never arrives
                // (the only other refresh is the 12h periodic worker).
                val synced = runCatching { playlistRepository.refreshPlaylist(id) }
                    .getOrDefault(false)
                if (!synced) {
                    runCatching { playlistRepository.deletePlaylist(id) }
                    _state.update { it.copy(loading = false, errorRes = com.streammaster.tv.R.string.login_error) }
                    return@launch
                }
                _state.update { it.copy(loading = false, success = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, errorRes = com.streammaster.tv.R.string.login_error) }
            }
        }
    }
}
