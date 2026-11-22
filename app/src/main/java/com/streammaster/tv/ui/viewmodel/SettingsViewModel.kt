package com.streammaster.tv.ui.viewmodel

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.BuildConfig
import com.streammaster.tv.data.backup.BackupManager
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.repository.PlaylistRepository
import com.streammaster.tv.data.repository.SettingsRepository
import com.streammaster.tv.data.update.AppUpdateChecker
import com.streammaster.tv.data.update.ServerStatus
import com.streammaster.tv.data.update.ServerStatusChecker
import com.streammaster.tv.data.update.UpdateInfo
import com.streammaster.tv.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsDraft(
    val lang: String = "fr",
    val dns: String = "auto",
    val dnsCustom: String = "",
    val ua: String = "default",
    val uaCustom: String = "",
    val format: String = "auto",
    val engine: String = "exo",
    val epgWindow: String = "24h",
    val autoResume: Boolean = false,
    val sleepMinutes: Int = 0,
    val pin: String = "",
    val loaded: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val appLock: Boolean = false,
    val kidsMode: Boolean = false,
    val frameRateSwitch: Boolean = true,
    val externalPlayer: String = "",
    val recordingFolderUri: String = "",
    val updateUrl: String = "",
    /** Goal alerts (2026-11-22): instant popup for every goal, opt-in. */
    val goalAlerts: Boolean = false,
    /** Maximum speed (2026-11-22): highest rendition + full bandwidth. */
    val maxSpeed: Boolean = true
)

/**
 * Settings: the parental PIN applies immediately (setPin); everything else
 * is edited in a draft and persisted with Save.
 *
 * LANGUAGE CHOICE (documented as required by CONTRACT §6): the language is
 * applied with AppCompatDelegate.setApplicationLocales(...) and then the
 * current Activity is recreated, so the new locale takes effect across the
 * whole app without a process restart. The locale is also stored in
 * SettingsRepository (lang) so it survives restarts.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
    private val serverStatusChecker: ServerStatusChecker,
    private val appUpdateChecker: AppUpdateChecker,
    private val backupManager: BackupManager,
    private val playerManager: PlayerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _draft = MutableStateFlow(SettingsDraft())
    val draft: StateFlow<SettingsDraft> = _draft.asStateFlow()
    private val _messageRes = MutableStateFlow<Int?>(null)
    val messageRes: StateFlow<Int?> = _messageRes.asStateFlow()

    /** Server status (2026-11-22): last check result, null = never checked. */
    private val _serverStatus = MutableStateFlow<ServerStatus?>(null)
    val serverStatus: StateFlow<ServerStatus?> = _serverStatus.asStateFlow()
    private val _serverChecking = MutableStateFlow(false)
    val serverChecking: StateFlow<Boolean> = _serverChecking.asStateFlow()

    /** App update check (2026-11-22): last result, null = never checked. */
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    /** Whether VLC is installed (external player option). */
    val vlcInstalled: Boolean =
        runCatching { context.packageManager.getPackageInfo("org.videolan.vlc", 0); true }
            .getOrDefault(false)

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                if (!_draft.value.loaded) {
                    _draft.value = SettingsDraft(
                        lang = s.lang, dns = s.dns, dnsCustom = s.dnsCustom,
                        ua = s.ua, uaCustom = s.uaCustom, format = s.format,
                        engine = s.engine, epgWindow = s.epgWindow, autoResume = s.autoResume,
                        sleepMinutes = s.sleepMinutes, pin = s.pin, loaded = true,
                        autoStartOnBoot = s.autoStartOnBoot, appLock = s.appLock,
                        kidsMode = s.kidsMode, frameRateSwitch = s.frameRateSwitch,
                        externalPlayer = s.externalPlayer,
                        recordingFolderUri = s.recordingFolderUri, updateUrl = s.updateUrl,
                        goalAlerts = s.goalAlerts, maxSpeed = s.maxSpeed
                    )
                }
            }
        }
    }

    fun setLang(v: String) = _draft.update { it.copy(lang = v) }
    fun setDns(v: String) = _draft.update { it.copy(dns = v) }
    fun setDnsCustom(v: String) = _draft.update { it.copy(dnsCustom = v) }
    fun setUa(v: String) = _draft.update { it.copy(ua = v) }
    fun setUaCustom(v: String) = _draft.update { it.copy(uaCustom = v) }
    fun setFormat(v: String) = _draft.update { it.copy(format = v) }
    fun setEngine(v: String) = _draft.update { it.copy(engine = v) }
    fun setEpgWindow(v: String) = _draft.update { it.copy(epgWindow = v) }
    fun setAutoResume(v: Boolean) = _draft.update { it.copy(autoResume = v) }
    fun setSleepMinutes(v: Int) = _draft.update { it.copy(sleepMinutes = v) }
    fun setAutoStartOnBoot(v: Boolean) = _draft.update { it.copy(autoStartOnBoot = v) }
    fun setAppLock(v: Boolean) = _draft.update { it.copy(appLock = v) }
    fun setKidsMode(v: Boolean) = _draft.update { it.copy(kidsMode = v) }
    fun setFrameRateSwitch(v: Boolean) = _draft.update { it.copy(frameRateSwitch = v) }
    fun setGoalAlerts(v: Boolean) = _draft.update { it.copy(goalAlerts = v) }
    fun setMaxSpeed(v: Boolean) = _draft.update { it.copy(maxSpeed = v) }
    fun setExternalPlayer(v: String) = _draft.update { it.copy(externalPlayer = v) }
    fun setRecordingFolderUri(v: String) = _draft.update { it.copy(recordingFolderUri = v) }
    fun setUpdateUrl(v: String) = _draft.update { it.copy(updateUrl = v) }

    /** PIN applies immediately, without waiting for Save. */
    fun setPin(pin: String) = viewModelScope.launch {
        settingsRepository.setPin(pin)
        _draft.update { it.copy(pin = pin) }
        _messageRes.value = com.streammaster.tv.R.string.pin_set
    }

    fun save(activity: Activity?) = viewModelScope.launch {
        val d = _draft.value
        val previousLang = settings.value.lang
        settingsRepository.update { s ->
            s.copy(
                lang = d.lang, dns = d.dns, dnsCustom = d.dnsCustom,
                ua = d.ua, uaCustom = d.uaCustom, format = d.format,
                engine = d.engine, epgWindow = d.epgWindow,
                autoResume = d.autoResume, sleepMinutes = d.sleepMinutes,
                autoStartOnBoot = d.autoStartOnBoot, appLock = d.appLock,
                kidsMode = d.kidsMode, frameRateSwitch = d.frameRateSwitch,
                externalPlayer = d.externalPlayer,
                recordingFolderUri = d.recordingFolderUri, updateUrl = d.updateUrl,
                goalAlerts = d.goalAlerts, maxSpeed = d.maxSpeed
            )
        }
        playerManager.setSleepTimer(d.sleepMinutes)
        _messageRes.value = com.streammaster.tv.R.string.settings_saved
        if (d.lang != previousLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(d.lang))
            activity?.recreate()
        }
    }

    /** Server status check (2026-11-22): pings the active playlist's server. */
    fun checkServerStatus() = viewModelScope.launch {
        if (_serverChecking.value) return@launch
        _serverChecking.value = true
        val playlist = playlistRepository.observePlaylists().first()
            .firstOrNull { it.isActive } ?: playlistRepository.observePlaylists().first().firstOrNull()
        _serverStatus.value = playlist?.let { serverStatusChecker.check(it) }
        _serverChecking.value = false
    }

    /** App update check (2026-11-22): reads the update JSON URL from settings. */
    fun checkForUpdate() = viewModelScope.launch {
        if (_checkingUpdate.value) return@launch
        _checkingUpdate.value = true
        val url = settingsRepository.settings.first().updateUrl
        _updateInfo.value = appUpdateChecker.check(url, BuildConfig.VERSION_CODE)
        _checkingUpdate.value = false
    }

    /** Downloads the APK with DownloadManager (2026-11-22). The system
     *  notifies when it is done; tapping it installs the update. The user
     *  must allow "install unknown apps" for Stream Master once. */
    fun downloadUpdate(downloadUrl: String) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Stream Master")
                .setDescription("Mise à jour")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "stream-master.apk")
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            _messageRes.value = com.streammaster.tv.R.string.update_downloading
        }.onFailure {
            _messageRes.value = com.streammaster.tv.R.string.update_failed
        }
    }

    /** Backup (2026-11-22): writes the JSON export to the chosen document. */
    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            val json = backupManager.export()
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: error("no stream")
            true
        }.getOrDefault(false)
        _messageRes.value = if (ok) com.streammaster.tv.R.string.backup_saved
        else com.streammaster.tv.R.string.backup_failed
    }

    /** Restore (2026-11-22): reads a backup JSON and applies it. */
    fun importBackup(uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching false
            backupManager.import(json)
        }.getOrDefault(false)
        _messageRes.value = if (ok) com.streammaster.tv.R.string.backup_restored
        else com.streammaster.tv.R.string.backup_failed
    }

    fun clearMessage() { _messageRes.value = null }

    /** Long-playback support (CONTRACT §5): request the battery-optimisation
     *  exemption. NOTE: agent 3 has not delivered PlaybackService (or its
     *  requestBatteryExemption helper) yet, so this opens the system
     *  battery-optimisation exemption screen directly for this app. */
    fun requestBatteryExemption(activity: Activity) {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:" + context.packageName)
            )
            activity.startActivity(intent)
        } catch (_: Exception) {
            activity.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }
}
