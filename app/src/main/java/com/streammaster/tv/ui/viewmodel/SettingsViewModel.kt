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
import com.streammaster.tv.data.backup.PinRecovery
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.data.util.SpeedTest
import com.streammaster.tv.data.work.BackupWorker
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
    val maxSpeed: Boolean = true,
    /** §4.36 (batch 2/1): loudness normalisation across channels. */
    val volumeNormalization: Boolean = true,
    /** §4.61: auto-enable an Arabic subtitle track when available. */
    val autoArabicSubs: Boolean = true,
    /** §4.54: do-not-disturb while watching. */
    val dndEnabled: Boolean = false,
    /** §4.36.8: smart alerts before every match of the day. */
    val smartMatchAlerts: Boolean = true,
    /** §4.59: automatic weekly backup. */
    val autoBackupWeekly: Boolean = false,
    /** §4.56: subtitle size (%), colour (hex) and position. */
    val subtitleSize: Int = 100,
    val subtitleColor: String = "#FFFFFF",
    val subtitlePosition: String = "bottom"
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
                    _draft.value = draftFrom(s)
                }
            }
        }
    }

    /** Builds a draft from persisted settings (used on first load, on
     *  discard and on reset). */
    private fun draftFrom(s: AppSettings) = SettingsDraft(
        lang = s.lang, dns = s.dns, dnsCustom = s.dnsCustom,
        ua = s.ua, uaCustom = s.uaCustom, format = s.format,
        engine = s.engine, epgWindow = s.epgWindow, autoResume = s.autoResume,
        sleepMinutes = s.sleepMinutes, pin = s.pin, loaded = true,
        autoStartOnBoot = s.autoStartOnBoot, appLock = s.appLock,
        kidsMode = s.kidsMode, frameRateSwitch = s.frameRateSwitch,
        externalPlayer = s.externalPlayer,
        recordingFolderUri = s.recordingFolderUri, updateUrl = s.updateUrl,
        goalAlerts = s.goalAlerts, maxSpeed = s.maxSpeed,
        volumeNormalization = s.volumeNormalization,
        autoArabicSubs = s.autoArabicSubs, dndEnabled = s.dndEnabled,
        smartMatchAlerts = s.smartMatchAlerts,
        autoBackupWeekly = s.autoBackupWeekly,
        subtitleSize = s.subtitleSize, subtitleColor = s.subtitleColor,
        subtitlePosition = s.subtitlePosition
    )

    /** §4.42 — recovery code issued at the last PIN setup, shown ONCE to
     *  the user (SettingsScreen dialog). Null = none pending. The code
     *  itself is never persisted — only its hash (pinRecoveryHash). */
    private val _recoveryCode = MutableStateFlow<String?>(null)
    val recoveryCode: StateFlow<String?> = _recoveryCode.asStateFlow()

    /** §4.47 — last speed-test result, null = never tested / failed. */
    private val _speedTestResult = MutableStateFlow<SpeedTest.Result?>(null)
    val speedTestResult: StateFlow<SpeedTest.Result?> = _speedTestResult.asStateFlow()
    private val _speedTesting = MutableStateFlow(false)
    val speedTesting: StateFlow<Boolean> = _speedTesting.asStateFlow()
    private val _speedTestError = MutableStateFlow(false)
    val speedTestError: StateFlow<Boolean> = _speedTestError.asStateFlow()
    private val speedTest = SpeedTest()

    /** ST05 — reset to defaults. Like the prototype's resetSettings(): the
     *  PIN and the parental locks are KEPT, everything else returns to the
     *  app defaults (and the draft is reset the same way). */
    fun resetToDefaults() = viewModelScope.launch {
        val keptPin = settings.value.pin
        val keptBouquets = settings.value.lockedBouquets
        val keptItems = settings.value.lockedItems
        val keptProfile = settings.value.activeProfileId
        val keptRecoveryHash = settings.value.pinRecoveryHash
        settingsRepository.update {
            AppSettings(
                pin = keptPin, lockedBouquets = keptBouquets,
                lockedItems = keptItems, activeProfileId = keptProfile,
                pinRecoveryHash = keptRecoveryHash
            )
        }
        _draft.value = SettingsDraft(
            pin = keptPin, loaded = true
        )
        _messageRes.value = com.streammaster.tv.R.string.st_reset_done
    }

    /** ST06 — throw the draft away and reload it from persisted settings. */
    fun discardChanges() {
        _draft.value = draftFrom(settings.value)
    }

    /** ST07 — delete the parental PIN (locks stay stored, but nothing is
     *  enforced without a PIN, like SettingsRepository.isLocked). The
     *  recovery code dies with the PIN (§4.42). */
    fun clearPin() = viewModelScope.launch {
        settingsRepository.update { it.copy(pin = "", pinRecoveryHash = null) }
        _draft.update { it.copy(pin = "") }
        _messageRes.value = com.streammaster.tv.R.string.st_pin_removed
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
    fun setVolumeNormalization(v: Boolean) = _draft.update { it.copy(volumeNormalization = v) }
    fun setAutoArabicSubs(v: Boolean) = _draft.update { it.copy(autoArabicSubs = v) }
    fun setDndEnabled(v: Boolean) = _draft.update { it.copy(dndEnabled = v) }
    fun setSmartMatchAlerts(v: Boolean) = _draft.update { it.copy(smartMatchAlerts = v) }
    fun setAutoBackupWeekly(v: Boolean) = _draft.update { it.copy(autoBackupWeekly = v) }
    fun setSubtitleSize(v: Int) = _draft.update { it.copy(subtitleSize = v) }
    fun setSubtitleColor(v: String) = _draft.update { it.copy(subtitleColor = v) }
    fun setSubtitlePosition(v: String) = _draft.update { it.copy(subtitlePosition = v) }
    fun setExternalPlayer(v: String) = _draft.update { it.copy(externalPlayer = v) }
    fun setRecordingFolderUri(v: String) = _draft.update { it.copy(recordingFolderUri = v) }
    fun setUpdateUrl(v: String) = _draft.update { it.copy(updateUrl = v) }

    /** PIN applies immediately, without waiting for Save. §4.42: setting
     *  a new PIN issues a fresh 6-digit recovery code (shown once via
     *  [recoveryCode]); only its hash is stored. */
    fun setPin(pin: String) = viewModelScope.launch {
        val code = PinRecovery.generateRecoveryCode()
        settingsRepository.update {
            it.copy(pin = pin, pinRecoveryHash = PinRecovery.hash(code))
        }
        _draft.update { it.copy(pin = pin) }
        _recoveryCode.value = code
        _messageRes.value = com.streammaster.tv.R.string.pin_set
    }

    /** Dismisses the one-time recovery-code dialog (the code cannot be
     *  shown again — the user was told to write it down). */
    fun dismissRecoveryCode() { _recoveryCode.value = null }

    /** §4.42 — checks a recovery code against the stored hash. True means
     *  the caller may let the user define a new PIN (which issues a fresh
     *  code). The old hash is NOT cleared here — setPin replaces it. */
    suspend fun verifyRecoveryCode(code: String): Boolean =
        PinRecovery.verify(code, settings.value.pinRecoveryHash)

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
                goalAlerts = d.goalAlerts, maxSpeed = d.maxSpeed,
                volumeNormalization = d.volumeNormalization,
                autoArabicSubs = d.autoArabicSubs, dndEnabled = d.dndEnabled,
                smartMatchAlerts = d.smartMatchAlerts,
                autoBackupWeekly = d.autoBackupWeekly,
                subtitleSize = d.subtitleSize, subtitleColor = d.subtitleColor,
                subtitlePosition = d.subtitlePosition
            )
        }
        // §4.59 — (un)schedule the weekly auto-backup with the saved value.
        if (d.autoBackupWeekly) BackupWorker.enqueue(context)
        else BackupWorker.cancel(context)
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

    /** Backup (2026-11-22): writes the export to the chosen document.
     *  §4.42: a non-blank [password] encrypts the file (AES/GCM); blank
     *  keeps the plain-JSON export as before. The password is never stored. */
    fun exportBackup(uri: Uri, password: String? = null) = viewModelScope.launch {
        val ok = runCatching {
            val json = backupManager.export(password)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: error("no stream")
            true
        }.getOrDefault(false)
        _messageRes.value = if (ok) com.streammaster.tv.R.string.backup_saved
        else com.streammaster.tv.R.string.backup_failed
    }

    /** Restore (2026-11-22): reads a backup and applies it. §4.42: an
     *  encrypted file needs its [password]; a wrong/missing password fails
     *  with the same generic message (no hint about the password). */
    fun importBackup(uri: Uri, password: String? = null) = viewModelScope.launch {
        val ok = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching false
            backupManager.import(text, password)
        }.getOrDefault(false)
        _messageRes.value = if (ok) com.streammaster.tv.R.string.backup_restored
        else com.streammaster.tv.R.string.backup_failed
    }

    /** §4.47 — runs a download speed test (Cloudflare public endpoint);
     *  the result lands in [speedTestResult]. A previous run is cancelled. */
    fun runSpeedTest() = viewModelScope.launch {
        if (_speedTesting.value) return@launch
        _speedTesting.value = true
        _speedTestError.value = false
        try {
            _speedTestResult.value = speedTest.measure()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            _speedTestError.value = true
        } finally {
            _speedTesting.value = false
        }
    }

    fun cancelSpeedTest() {
        speedTest.cancel()
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
