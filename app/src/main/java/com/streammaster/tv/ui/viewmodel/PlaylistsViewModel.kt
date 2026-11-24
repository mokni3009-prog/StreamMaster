package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val MAX_PLAYLISTS = 10

data class PlaylistFormState(
    val editing: Playlist? = null,
    val name: String = "",
    val type: com.streammaster.tv.data.model.PlaylistType = com.streammaster.tv.data.model.PlaylistType.XTREAM,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val portalUrl: String = "",
    val mac: String = "",
    val m3uUrl: String = "",
    val xmltvUrl: String = "",
    val showForm: Boolean = false,
    val confirmDelete: Playlist? = null,
    val messageRes: Int? = null
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    val playlists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _form = MutableStateFlow(PlaylistFormState())
    val form: StateFlow<PlaylistFormState> = _form.asStateFlow()

    fun openAdd() = _form.update { PlaylistFormState(showForm = true) }
    fun openEdit(p: Playlist) = _form.update {
        PlaylistFormState(
            editing = p, name = p.name, type = p.type, serverUrl = p.serverUrl, username = p.username,
            password = p.password, portalUrl = p.portalUrl, mac = p.mac, m3uUrl = p.m3uUrl, xmltvUrl = p.xmltvUrl ?: "", showForm = true
        )
    }
    fun closeForm() = _form.update { it.copy(showForm = false, editing = null) }
    fun setName(v: String) = _form.update { it.copy(name = v) }
    fun setType(v: com.streammaster.tv.data.model.PlaylistType) = _form.update { it.copy(type = v) }
    fun setServerUrl(v: String) = _form.update { it.copy(serverUrl = v) }
    fun setUsername(v: String) = _form.update { it.copy(username = v) }
    fun setPassword(v: String) = _form.update { it.copy(password = v) }
    fun setPortalUrl(v: String) = _form.update { it.copy(portalUrl = v) }
    fun setMac(v: String) = _form.update { it.copy(mac = v) }
    fun setM3uUrl(v: String) = _form.update { it.copy(m3uUrl = v) }
    fun setXmltvUrl(v: String) = _form.update { it.copy(xmltvUrl = v) }
    fun askDelete(p: Playlist) = _form.update { it.copy(confirmDelete = p) }
    fun cancelDelete() = _form.update { it.copy(confirmDelete = null) }

    fun saveForm(currentCount: Int) = viewModelScope.launch {
        val f = _form.value
        if (f.editing == null && currentCount >= MAX_PLAYLISTS) {
            _form.update { it.copy(messageRes = com.streammaster.tv.R.string.max_playlists) }
            return@launch
        }
        val base = f.editing ?: Playlist(
            id = java.util.UUID.randomUUID().toString(), name = f.name,
            type = f.type,
            serverUrl = "", username = "", password = "", portalUrl = "", mac = "", m3uUrl = "",
            isActive = false, expiryDate = "", connections = 1, lastUpdate = "", status = "active"
        )
        val updated = base.copy(
            name = f.name.ifBlank { base.name }, type = f.type,
            serverUrl = f.serverUrl, username = f.username,
            password = f.password, portalUrl = f.portalUrl,
            mac = f.mac.trim().uppercase(), m3uUrl = f.m3uUrl,
            xmltvUrl = f.xmltvUrl.takeIf { it.isNotBlank() }
        )
        if (f.editing == null) {
            // Y07 — same path as Login: addPlaylist then refresh; if the
            // refresh fails the playlist is removed again (LoginViewModel).
            val id = playlistRepository.addPlaylist(updated)
            val ok = runCatching { playlistRepository.refreshPlaylist(id) }.getOrDefault(false)
            if (!ok) {
                runCatching { playlistRepository.deletePlaylist(id) }
                _form.update { it.copy(messageRes = com.streammaster.tv.R.string.login_error) }
                return@launch
            }
        } else {
            playlistRepository.updatePlaylist(updated)
            runCatching { playlistRepository.refreshPlaylist(updated.id) }
        }
        _form.update { PlaylistFormState() }
    }

    fun deleteConfirmed() = viewModelScope.launch {
        _form.value.confirmDelete?.let { playlistRepository.deletePlaylist(it.id) }
        _form.update { it.copy(confirmDelete = null) }
    }

    /** L-04 — suspends so the caller can navigate to Loading only after
     *  the activation is committed (was fire-and-forget). */
    suspend fun activate(id: String) { playlistRepository.activatePlaylist(id) }

    fun refresh(id: String) = viewModelScope.launch {
        val ok = playlistRepository.refreshPlaylist(id)
        _form.update { it.copy(messageRes = if (ok) com.streammaster.tv.R.string.refresh_done else com.streammaster.tv.R.string.login_error) }
    }

    fun clearMessage() = _form.update { it.copy(messageRes = null) }
}
