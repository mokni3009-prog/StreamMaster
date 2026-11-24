package com.streammaster.tv.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.R
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.data.model.PlaylistType
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.EpgRepository
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.update.ServerStatus
import com.streammaster.tv.data.update.ServerStatusChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class OnboardingUiState(
    /** 0 = language, 1 = playlist, 2 = connection test, 3 = background sync. */
    val step: Int = 0,
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
    val testResult: ServerStatus? = null,
    val testDone: Boolean = false,
    val syncing: Boolean = false,
    val syncDone: Boolean = false,
    val finished: Boolean = false
)

/**
 * First-run wizard (§4.39): language → playlist → connection test with a
 * plain-language error explanation → background sync.
 *
 * [SettingsStore.setFirstRunDone] exists (SettingsStore.kt) and the wizard
 * calls it when it finishes; the engine routes Splash to the onboarding
 * wizard route when it is false. The playlist add + sync mirrors
 * [LoginViewModel.submit] so the two paths cannot drift apart.
 */
@HiltViewModel
class OnboardingWizardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsStore: SettingsStore,
    private val playlistRepository: PlaylistRepository,
    private val serverStatusChecker: ServerStatusChecker,
    private val epgRepository: EpgRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var savedPlaylist: Playlist? = null

    fun setTab(tab: LoginTab) = _state.update { it.copy(tab = tab, errorRes = null) }
    fun setServerUrl(v: String) = _state.update { it.copy(serverUrl = v) }
    fun setUsername(v: String) = _state.update { it.copy(username = v) }
    fun setPassword(v: String) = _state.update { it.copy(password = v) }
    fun setPortalUrl(v: String) = _state.update { it.copy(portalUrl = v) }
    fun setMac(v: String) = _state.update { it.copy(mac = v) }
    fun setM3uUrl(v: String) = _state.update { it.copy(m3uUrl = v) }
    fun setPlaylistName(v: String) = _state.update { it.copy(playlistName = v) }
    fun goToStep(step: Int) = _state.update { it.copy(step = step, errorRes = null) }

    /** Language applies immediately (same call as SettingsViewModel.save). */
    fun setLang(lang: String) = viewModelScope.launch {
        settingsRepository.update { it.copy(lang = lang) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    /** Validates the active tab, saves the playlist and syncs it at once. */
    fun submitPlaylist() {
        val s = _state.value
        val error = when (s.tab) {
            LoginTab.XTREAM -> if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank())
                R.string.login_error_url else null
            LoginTab.STALKER -> when {
                s.portalUrl.isBlank() -> R.string.login_error_url
                !MAC_REGEX.matches(s.mac.trim()) -> R.string.login_error_mac
                else -> null
            }
            LoginTab.M3U -> if (s.m3uUrl.isBlank() || !s.m3uUrl.startsWith("http"))
                R.string.login_error_url else null
        }
        if (error != null) {
            _state.update { it.copy(errorRes = error) }
            return
        }
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
                val synced = runCatching { playlistRepository.refreshPlaylist(id) }.getOrDefault(false)
                if (!synced) {
                    runCatching { playlistRepository.deletePlaylist(id) }
                    _state.update { it.copy(loading = false, errorRes = R.string.login_error) }
                    return@launch
                }
                savedPlaylist = playlist.copy(id = id)
                _state.update { it.copy(loading = false, step = 2) }
                testConnection()
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, errorRes = R.string.login_error) }
            }
        }
    }

    /** Connection test with a plain-language result (§4.39 step 3). */
    fun testConnection() = viewModelScope.launch {
        val playlist = savedPlaylist ?: runCatching { playlistRepository.getActive() }.getOrNull()
            ?: return@launch
        savedPlaylist = playlist
        _state.update { it.copy(testDone = false, testResult = null) }
        val result = runCatching { serverStatusChecker.check(playlist) }.getOrNull()
        _state.update { it.copy(testResult = result, testDone = true) }
    }

    /** Background sync (EPG), then mark the first run done (§4.39 step 4). */
    fun finishSync() = viewModelScope.launch {
        _state.update { it.copy(syncing = true, step = 3) }
        val playlist = savedPlaylist ?: runCatching { playlistRepository.getActive() }.getOrNull()
        if (playlist != null) {
            runCatching { epgRepository.refreshEpg(playlist.id) }
        }
        runCatching { settingsStore.setFirstRunDone(true) }
        _state.update { it.copy(syncing = false, syncDone = true, finished = true) }
    }
}
