package com.streammaster.tv.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.components.PinDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.ProfilesViewModel
import com.streammaster.tv.ui.viewmodel.SettingsViewModel
import com.streammaster.tv.ui.components.GoldButton

/**
 * Settings — language (AR/EN/FR, default FR), DNS list+custom, UA list+custom,
 * format, engine, EPG window, autoResume, sleep timer, parental PIN + locks.
 * PIN applies immediately; the rest are saved with the Save button.
 */
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val messageRes by viewModel.messageRes.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val serverChecking by viewModel.serverChecking.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val checkingUpdate by viewModel.checkingUpdate.collectAsState()
    val profilesViewModel: ProfilesViewModel = hiltViewModel()
    val profiles by profilesViewModel.profiles.collectAsState()
    val activeProfileId by profilesViewModel.activeProfileId.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var selector by remember { mutableStateOf<String?>(null) } // dns | ua | format | engine | epg | sleep | lang | externalPlayer
    var pinDialog by remember { mutableStateOf(false) }
    var profilePinTarget by remember { mutableStateOf<String?>(null) }
    var confirmDeleteProfileId by remember { mutableStateOf<String?>(null) }
    var keyboardTarget by remember { mutableStateOf<String?>(null) } // "dnsCustom" | "uaCustom" | "updateUrl" | "newProfile"
    var newProfileName by remember { mutableStateOf("") }
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }

    // SAF launchers (2026-11-22): recording folder, backup export/import.
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setRecordingFolderUri(uri.toString())
        }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }

    LaunchedEffect(messageRes) {
        messageRes?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    @Composable
    fun label(res: Int) = stringResource(res)
    val dnsOptions = listOf(
        "auto" to label(R.string.dns_auto), "8.8.8.8" to label(R.string.dns_google),
        "1.1.1.1" to label(R.string.dns_cloudflare), "9.9.9.9" to label(R.string.dns_quad9),
        "custom" to label(R.string.dns_custom_label)
    )
    val uaOptions = listOf(
        "default" to label(R.string.ua_default), "exo" to label(R.string.ua_chrome_tv),
        "vlc" to label(R.string.ua_vlc), "custom" to label(R.string.ua_custom_label)
    )
    val formatOptions = listOf(
        "auto" to label(R.string.format_auto), "hls" to label(R.string.format_hls),
        "ts" to label(R.string.format_ts), "dash" to label(R.string.format_dash), "rtmp" to label(R.string.format_rtmp)
    )
    val engineOptions = listOf(
        "exo" to label(R.string.engine_exo), "vlc" to label(R.string.engine_vlc), "auto" to label(R.string.engine_auto)
    )
    val epgOptions = listOf(
        "24h" to label(R.string.epg_24h), "48h" to label(R.string.epg_48h), "7d" to label(R.string.epg_7d)
    )
    val sleepOptions = listOf(
        "0" to label(R.string.sleep_off), "15" to label(R.string.sleep_15), "30" to label(R.string.sleep_30),
        "60" to label(R.string.sleep_60), "90" to label(R.string.sleep_90), "120" to label(R.string.sleep_120)
    )
    val langOptions = listOf("fr" to label(R.string.language_fr), "en" to label(R.string.language_en), "ar" to label(R.string.language_ar))
    val externalPlayerOptions = buildList {
        add("" to label(R.string.external_internal))
        if (viewModel.vlcInstalled) add("org.videolan.vlc" to label(R.string.external_vlc))
        add("chooser" to label(R.string.external_chooser))
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(text = stringResource(R.string.settings_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(bottom = 8.dp))
        }
        item { SettingRow(stringResource(R.string.language), langOptions.firstOrNull { it.first == draft.lang }?.second ?: draft.lang) { selector = "lang" } }
        item { SettingRow(stringResource(R.string.dns), dnsOptions.firstOrNull { it.first == draft.dns }?.second ?: draft.dns) { selector = "dns" } }
        if (draft.dns == "custom") {
            item { ReadOnlyField(stringResource(R.string.dns_custom), draft.dnsCustom) { keyboardTarget = "dnsCustom" } }
        }
        item { SettingRow(stringResource(R.string.user_agent), uaOptions.firstOrNull { it.first == draft.ua }?.second ?: draft.ua) { selector = "ua" } }
        if (draft.ua == "custom") {
            item { ReadOnlyField(stringResource(R.string.ua_custom), draft.uaCustom) { keyboardTarget = "uaCustom" } }
        }
        item { SettingRow(stringResource(R.string.format), formatOptions.firstOrNull { it.first == draft.format }?.second ?: draft.format) { selector = "format" } }
        item { SettingRow(stringResource(R.string.engine), engineOptions.firstOrNull { it.first == draft.engine }?.second ?: draft.engine) { selector = "engine" } }
        item { SettingRow(stringResource(R.string.epg_window), epgOptions.firstOrNull { it.first == draft.epgWindow }?.second ?: draft.epgWindow) { selector = "epg" } }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.auto_resume), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.autoResume) "✓" else "—") { viewModel.setAutoResume(!draft.autoResume) }
            }
        }
        item { SettingRow(stringResource(R.string.sleep_timer), sleepOptions.firstOrNull { it.first == draft.sleepMinutes.toString() }?.second ?: "") { selector = "sleep" } }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = stringResource(R.string.parental_pin), fontSize = 18.sp, color = GoldWhite)
                    Text(
                        text = if (settings.pin.isNotEmpty()) "••••" else stringResource(R.string.set_pin),
                        fontSize = 15.sp, color = TextMuted
                    )
                }
                GoldButton(text = stringResource(R.string.set_pin)) { pinDialog = true }
            }
        }
        item {
            Text(
                text = stringResource(R.string.locked) + ": " +
                    if (settings.lockedItems.isEmpty() && settings.lockedBouquets.isEmpty()) "—"
                    else "${settings.lockedItems.size} item(s), ${settings.lockedBouquets.size} bouquet(s)",
                fontSize = 15.sp, color = TextMuted
            )
        }
        item {
            if (activity != null) {
                GhostButton(text = stringResource(R.string.battery_exemption)) { viewModel.requestBatteryExemption(activity) }
            }
        }
        // Profiles (2026-11-22): per-person history + watch-later.
        item {
            Text(text = stringResource(R.string.profiles_title), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(top = 12.dp))
        }
        profiles.forEach { profile ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (profile.id == activeProfileId) "✓ " else "") + profile.name,
                        fontSize = 18.sp, color = GoldWhite
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(text = stringResource(R.string.profile_pin)) { profilePinTarget = profile.id }
                        if (profile.id != "default") {
                            if (confirmDeleteProfileId == profile.id) {
                                GoldButton(text = stringResource(R.string.confirm_delete)) {
                                    profilesViewModel.deleteProfile(profile.id)
                                    confirmDeleteProfileId = null
                                }
                            } else {
                                GhostButton(text = stringResource(R.string.delete)) { confirmDeleteProfileId = profile.id }
                            }
                        }
                    }
                }
            }
        }
        item { ReadOnlyField(stringResource(R.string.profile_add), "") { keyboardTarget = "newProfile" } }
        // New toggles (2026-11-22).
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.auto_start), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.autoStartOnBoot) "✓" else "—") { viewModel.setAutoStartOnBoot(!draft.autoStartOnBoot) }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.app_lock), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.appLock) "✓" else "—") { viewModel.setAppLock(!draft.appLock) }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.kids_mode), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.kidsMode) "✓" else "—") { viewModel.setKidsMode(!draft.kidsMode) }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.frame_rate_switch), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.frameRateSwitch) "✓" else "—") { viewModel.setFrameRateSwitch(!draft.frameRateSwitch) }
            }
        }
        // Goal alerts (2026-11-22): opt-in, toggled here or in Match Center.
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.goal_alerts), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.goalAlerts) "✓" else "—") { viewModel.setGoalAlerts(!draft.goalAlerts) }
            }
        }
        // Maximum speed (2026-11-22): highest rendition + full bandwidth.
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.max_speed), fontSize = 18.sp, color = GoldWhite)
                GhostButton(text = if (draft.maxSpeed) "✓" else "—") { viewModel.setMaxSpeed(!draft.maxSpeed) }
            }
        }
        item { SettingRow(stringResource(R.string.external_player), externalPlayerOptions.firstOrNull { it.first == draft.externalPlayer }?.second ?: draft.externalPlayer) { selector = "externalPlayer" } }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.recording_folder) + ": " +
                        if (draft.recordingFolderUri.isBlank()) stringResource(R.string.recording_folder_default)
                        else stringResource(R.string.recording_folder_custom),
                    fontSize = 18.sp, color = GoldWhite
                )
                GoldButton(text = stringResource(R.string.choose)) { folderPickerLauncher.launch(null) }
            }
        }
        // Server status (2026-11-22).
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val status = serverStatus
                Text(
                    text = stringResource(R.string.server_status) + ": " + when {
                        serverChecking -> "…"
                        status == null -> stringResource(R.string.server_not_checked)
                        status.online -> stringResource(R.string.server_online, status.latencyMs)
                        else -> stringResource(R.string.server_offline, status.detail)
                    },
                    fontSize = 18.sp, color = GoldWhite
                )
                GoldButton(text = stringResource(R.string.check)) { viewModel.checkServerStatus() }
            }
        }
        // App updates (2026-11-22).
        item { ReadOnlyField(stringResource(R.string.update_url), draft.updateUrl) { keyboardTarget = "updateUrl" } }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val info = updateInfo
                Text(
                    text = when {
                        checkingUpdate -> stringResource(R.string.checking)
                        info == null -> stringResource(R.string.update_not_checked)
                        !info.available -> stringResource(R.string.update_up_to_date)
                        else -> stringResource(R.string.update_available, info.version)
                    },
                    fontSize = 18.sp, color = GoldWhite
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info?.available == true && info.downloadUrl.isNotBlank()) {
                        GoldButton(text = stringResource(R.string.download)) { viewModel.downloadUpdate(info.downloadUrl) }
                    }
                    GoldButton(text = stringResource(R.string.check)) { viewModel.checkForUpdate() }
                }
            }
        }
        // Backup / restore (2026-11-22).
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoldButton(text = stringResource(R.string.backup_export)) {
                    backupExportLauncher.launch("stream-master-backup.json")
                }
                GoldButton(text = stringResource(R.string.backup_import)) {
                    backupImportLauncher.launch(arrayOf("application/json"))
                }
            }
        }
        item {
            GoldButton(text = stringResource(R.string.save), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                viewModel.save(activity)
            }
        }
    }

    when (selector) {
        "lang" -> SelectorDialog(
            title = stringResource(R.string.language),
            options = langOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.lang,
            onSelect = { id -> viewModel.setLang(id); selector = null },
            onDismiss = { selector = null }
        )
        "dns" -> SelectorDialog(
            title = stringResource(R.string.dns),
            options = dnsOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.dns,
            onSelect = { id -> viewModel.setDns(id); selector = null },
            onDismiss = { selector = null }
        )
        "ua" -> SelectorDialog(
            title = stringResource(R.string.user_agent),
            options = uaOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.ua,
            onSelect = { id -> viewModel.setUa(id); selector = null },
            onDismiss = { selector = null }
        )
        "format" -> SelectorDialog(
            title = stringResource(R.string.format),
            options = formatOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.format,
            onSelect = { id -> viewModel.setFormat(id); selector = null },
            onDismiss = { selector = null }
        )
        "engine" -> SelectorDialog(
            title = stringResource(R.string.engine),
            options = engineOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.engine,
            onSelect = { id -> viewModel.setEngine(id); selector = null },
            onDismiss = { selector = null }
        )
        "epg" -> SelectorDialog(
            title = stringResource(R.string.epg_window),
            options = epgOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.epgWindow,
            onSelect = { id -> viewModel.setEpgWindow(id); selector = null },
            onDismiss = { selector = null }
        )
        "sleep" -> SelectorDialog(
            title = stringResource(R.string.sleep_timer),
            options = sleepOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.sleepMinutes.toString(),
            onSelect = { id -> viewModel.setSleepMinutes(id.toInt()); selector = null },
            onDismiss = { selector = null }
        )
        "externalPlayer" -> SelectorDialog(
            title = stringResource(R.string.external_player),
            options = externalPlayerOptions.map { SelectorOption(it.first, it.second) }, selectedId = draft.externalPlayer,
            onSelect = { id -> viewModel.setExternalPlayer(id); selector = null },
            onDismiss = { selector = null }
        )
    }

    if (pinDialog) {
        PinDialog(
            title = stringResource(R.string.set_pin),
            onComplete = { pin -> viewModel.setPin(pin); pinDialog = false },
            onCancel = { pinDialog = false }
        )
    }

    if (profilePinTarget != null) {
        PinDialog(
            title = stringResource(R.string.profile_pin_title),
            onComplete = { pin -> profilesViewModel.setProfilePin(profilePinTarget!!, pin); profilePinTarget = null },
            onCancel = { profilePinTarget = null }
        )
    }

    keyboardTarget?.let { target ->
        val current = when (target) {
            "dnsCustom" -> draft.dnsCustom
            "uaCustom" -> draft.uaCustom
            "updateUrl" -> draft.updateUrl
            else -> newProfileName // newProfile: typed name lives in local state
        }
        val setValue: (String) -> Unit = when (target) {
            "dnsCustom" -> viewModel::setDnsCustom
            "uaCustom" -> viewModel::setUaCustom
            "updateUrl" -> viewModel::setUpdateUrl
            else -> { v -> newProfileName = v }
        }
        Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = current, color = GoldWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                BuiltInKeyboard(
                    onKey = { c -> setValue(current + c) },
                    onAction = { action ->
                        when (action) {
                            is KeyboardAction.Char -> Unit // already handled via onKey
                            KeyboardAction.Backspace -> setValue(current.dropLast(1))
                            KeyboardAction.Space -> setValue(current + " ")
                            KeyboardAction.Shift -> { }
                            KeyboardAction.SwitchLanguage -> keyboardLang =
                                if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                            KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                            KeyboardAction.Validate -> {
                                if (target == "newProfile") {
                                    profilesViewModel.addProfile(current)
                                    newProfileName = ""
                                }
                                keyboardTarget = null
                            }
                            KeyboardAction.Close -> keyboardTarget = null
                        }
                    },
                    language = keyboardLang,
                    mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(com.streammaster.tv.ui.theme.CardSurface)
            .tvFocus()
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 18.sp, color = GoldWhite)
        Text(text = value, fontSize = 16.sp, color = com.streammaster.tv.ui.theme.Gold)
    }
}
