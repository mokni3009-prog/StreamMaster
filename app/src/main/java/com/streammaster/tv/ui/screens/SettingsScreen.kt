package com.streammaster.tv.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.AppSettings
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.components.PinDialog
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.PlaylistsViewModel
import com.streammaster.tv.ui.viewmodel.ProfilesViewModel
import com.streammaster.tv.ui.viewmodel.SettingsDraft
import com.streammaster.tv.ui.viewmodel.SettingsViewModel

/**
 * Settings — ST01 two columns (660dp list + detail panel), ST02 detail
 * panel (gold tag + Georgia 36sp title + 20sp paragraph + value box + gold
 * CTA), ST03 rows (54dp gold icon + 21sp title + 16sp desc + value pill),
 * ST04 model row order, ST05 reset (PIN + locks kept, like the prototype),
 * ST06 save bar (status + dirty badge + disabled save + cancel),
 * ST07 remove-PIN ghost button, ST08 locks row -> Parental screen,
 * ST09 playlist / manage rows, ST10 autoResume + sleep selectors,
 * ST13 remote hint, ST14 custom DNS/UA with IPv4 validation.
 * ST11 extra rows (approved after the prototype) are kept after the 12
 * model rows and are NOT deleted.
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
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    val playlists by playlistsViewModel.playlists.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var selector by remember { mutableStateOf<String?>(null) } // dns | ua | format | engine | epg | sleep | lang | externalPlayer | autoResume
    var pinDialog by remember { mutableStateOf(false) }
    var profilePinTarget by remember { mutableStateOf<String?>(null) }
    var confirmDeleteProfileId by remember { mutableStateOf<String?>(null) }
    var keyboardTarget by remember { mutableStateOf<String?>(null) } // "dnsCustom" | "uaCustom" | "updateUrl" | "newProfile"
    // Return focus after the keyboard closes: the focused KbKey is disposed
    // with the keyboard, so without this the D-pad is left with no focus.
    var lastKeyboardTarget by remember { mutableStateOf<String?>(null) }
    val keyboardReturnFocus = remember { FocusRequester() }
    LaunchedEffect(keyboardTarget) {
        if (keyboardTarget == null && lastKeyboardTarget != null) keyboardReturnFocus.requestFocus()
    }
    fun openKeyboard(target: String) {
        keyboardTarget = target
        lastKeyboardTarget = target
    }
    // Back closes the keyboard first, instead of leaving the screen.
    BackHandler(enabled = keyboardTarget != null) { keyboardTarget = null }
    var newProfileName by remember { mutableStateOf("") }
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    var dnsError by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf("lang") }

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
            showToast(context, context.getString(it))
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
    val autoResumeOptions = listOf("yes" to label(R.string.st_yes), "no" to label(R.string.st_no))
    val externalPlayerOptions = buildList {
        add("" to label(R.string.external_internal))
        if (viewModel.vlcInstalled) add("org.videolan.vlc" to label(R.string.external_vlc))
        add("chooser" to label(R.string.external_chooser))
    }

    val lockedCount = settings.lockedItems.size + settings.lockedBouquets.values.sumOf { it.size }
    val activePlaylist = playlists.firstOrNull { it.isActive } ?: playlists.firstOrNull()
    val openText = stringResource(R.string.st_open)
    val onText = stringResource(R.string.st_on)
    val offText = stringResource(R.string.st_off)
    val server = serverStatus
    val serverText = when {
        serverChecking -> "…"
        server == null -> stringResource(R.string.server_not_checked)
        server.online -> stringResource(R.string.server_online, server.latencyMs)
        else -> stringResource(R.string.server_offline, server.detail)
    }
    val update = updateInfo
    val updateText = when {
        checkingUpdate -> stringResource(R.string.checking)
        update == null -> stringResource(R.string.update_not_checked)
        !update.available -> stringResource(R.string.update_up_to_date)
        else -> stringResource(R.string.update_available, update.version)
    }

    // ST04 — the 12 prototype rows, in prototype order.
    val modelRows = listOf(
        SettingRowDef("lang", R.drawable.ic_globe, R.string.language, R.string.st_lang_desc, R.string.st_lang_detail,
            langOptions.firstOrNull { it.first == draft.lang }?.second ?: draft.lang, null, R.string.st_choose) { selector = "lang" },
        SettingRowDef("dns", R.drawable.ic_globe, R.string.dns, R.string.st_dns_desc, R.string.st_dns_detail,
            dnsOptions.firstOrNull { it.first == draft.dns }?.second ?: draft.dns,
            if (draft.dns == "custom") draft.dnsCustom else null, R.string.st_choose) { selector = "dns" },
        SettingRowDef("ua", R.drawable.ic_link, R.string.user_agent, R.string.st_ua_desc, R.string.st_ua_detail,
            uaOptions.firstOrNull { it.first == draft.ua }?.second ?: draft.ua,
            if (draft.ua == "custom") draft.uaCustom else null, R.string.st_choose) { selector = "ua" },
        SettingRowDef("format", R.drawable.ic_play, R.string.format, R.string.st_format_desc, R.string.st_format_detail,
            formatOptions.firstOrNull { it.first == draft.format }?.second ?: draft.format, null, R.string.st_choose) { selector = "format" },
        SettingRowDef("pin", R.drawable.ic_lock, R.string.parental_pin, R.string.st_pin_desc, R.string.st_pin_detail,
            if (settings.pin.isNotEmpty()) stringResource(R.string.st_pin_defined) else stringResource(R.string.st_pin_not_defined),
            if (settings.pin.isNotEmpty()) "••••" else null,
            if (settings.pin.isNotEmpty()) R.string.change_pin else R.string.set_pin) { pinDialog = true },
        SettingRowDef("locks", R.drawable.ic_shield, R.string.st_locks_title, R.string.st_locks_desc, R.string.st_locks_detail,
            if (lockedCount > 0) stringResource(R.string.st_locks_count, lockedCount) else stringResource(R.string.st_locks_none),
            if (settings.pin.isNotEmpty()) stringResource(R.string.st_pin_on) else stringResource(R.string.st_pin_off),
            R.string.st_open_editor) { navController.navigate(Routes.PARENTAL) },
        SettingRowDef("engine", R.drawable.ic_settings, R.string.engine, R.string.st_engine_desc, R.string.st_engine_detail,
            engineOptions.firstOrNull { it.first == draft.engine }?.second ?: draft.engine, null, R.string.st_choose) { selector = "engine" },
        SettingRowDef("epg", R.drawable.ic_calendar, R.string.epg_window, R.string.st_epg_desc, R.string.st_epg_detail,
            epgOptions.firstOrNull { it.first == draft.epgWindow }?.second ?: draft.epgWindow, null, R.string.st_choose) { selector = "epg" },
        SettingRowDef("autoResume", R.drawable.ic_play, R.string.auto_resume, R.string.st_auto_resume_desc, R.string.st_auto_resume_detail,
            if (draft.autoResume) onText else offText, null, R.string.st_choose) { selector = "autoResume" },
        SettingRowDef("sleep", R.drawable.ic_clock, R.string.sleep_timer, R.string.st_sleep_desc, R.string.st_sleep_detail,
            sleepOptions.firstOrNull { it.first == draft.sleepMinutes.toString() }?.second ?: "", null, R.string.st_choose) { selector = "sleep" },
        SettingRowDef("playlist", R.drawable.ic_list, R.string.st_playlist_title, R.string.st_playlist_desc, R.string.st_playlist_detail,
            activePlaylist?.name ?: "—",
            activePlaylist?.let { "Xtream • ${it.expiryDate}" }, R.string.st_open) { navController.navigate(Routes.PLAYLISTS) },
        SettingRowDef("managePl", R.drawable.ic_folder, R.string.st_manage_title, R.string.st_manage_desc, R.string.st_manage_detail,
            openText, null, R.string.st_open) { navController.navigate(Routes.MANAGE) }
    )
    // ST11 — extra rows approved after the prototype, kept after the 12.
    val extraRows = listOf(
        SettingRowDef("autoStart", R.drawable.ic_play, R.string.auto_start, R.string.st_toggle_desc, R.string.st_toggle_desc,
            if (draft.autoStartOnBoot) onText else offText, null, R.string.st_choose) { viewModel.setAutoStartOnBoot(!draft.autoStartOnBoot) },
        SettingRowDef("appLock", R.drawable.ic_lock, R.string.app_lock, R.string.st_toggle_desc, R.string.st_toggle_desc,
            if (draft.appLock) onText else offText, null, R.string.st_choose) { viewModel.setAppLock(!draft.appLock) },
        SettingRowDef("kidsMode", R.drawable.ic_shield, R.string.kids_mode, R.string.st_toggle_desc, R.string.st_toggle_desc,
            if (draft.kidsMode) onText else offText, null, R.string.st_choose) { viewModel.setKidsMode(!draft.kidsMode) },
        SettingRowDef("frameRate", R.drawable.ic_tv, R.string.frame_rate_switch, R.string.st_toggle_desc, R.string.st_toggle_desc,
            if (draft.frameRateSwitch) onText else offText, null, R.string.st_choose) { viewModel.setFrameRateSwitch(!draft.frameRateSwitch) },
        SettingRowDef("goalAlerts", R.drawable.ic_alert, R.string.goal_alerts, R.string.st_toggle_desc, R.string.st_toggle_desc,
            if (draft.goalAlerts) onText else offText, null, R.string.st_choose) { viewModel.setGoalAlerts(!draft.goalAlerts) },
        SettingRowDef("maxSpeed", R.drawable.ic_wifi, R.string.max_speed, R.string.st_toggle_desc, R.string.st_toggle_desc,
            if (draft.maxSpeed) onText else offText, null, R.string.st_choose) { viewModel.setMaxSpeed(!draft.maxSpeed) },
        SettingRowDef("externalPlayer", R.drawable.ic_play, R.string.external_player, R.string.st_external_desc, R.string.st_external_desc,
            externalPlayerOptions.firstOrNull { it.first == draft.externalPlayer }?.second ?: draft.externalPlayer,
            null, R.string.st_choose) { selector = "externalPlayer" },
        SettingRowDef("recFolder", R.drawable.ic_folder, R.string.recording_folder, R.string.st_recording_folder_desc, R.string.st_recording_folder_desc,
            if (draft.recordingFolderUri.isBlank()) stringResource(R.string.recording_folder_default)
            else stringResource(R.string.recording_folder_custom), null, R.string.choose) { folderPickerLauncher.launch(null) },
        SettingRowDef("server", R.drawable.ic_server, R.string.server_status, R.string.st_server_desc, R.string.st_server_desc,
            serverText, null, R.string.check) { viewModel.checkServerStatus() },
        SettingRowDef("updateUrl", R.drawable.ic_link, R.string.update_url, R.string.st_update_desc, R.string.st_update_desc,
            draft.updateUrl.ifBlank { "—" }, null, R.string.st_choose) { openKeyboard("updateUrl") },
        SettingRowDef("update", R.drawable.ic_refresh, R.string.check, R.string.st_update_desc, R.string.st_update_desc,
            updateText, null,
            if (updateInfo?.available == true) R.string.download else R.string.check) {
            val info = updateInfo
            if (info?.available == true && info.downloadUrl.isNotBlank()) viewModel.downloadUpdate(info.downloadUrl)
            else viewModel.checkForUpdate()
        },
        SettingRowDef("backupExport", R.drawable.ic_download, R.string.backup_export, R.string.st_backup_desc, R.string.st_backup_desc,
            openText, null, R.string.st_open) { backupExportLauncher.launch("stream-master-backup.json") },
        SettingRowDef("backupImport", R.drawable.ic_upload, R.string.backup_import, R.string.st_backup_desc, R.string.st_backup_desc,
            openText, null, R.string.st_open) { backupImportLauncher.launch(arrayOf("application/json")) },
        SettingRowDef("battery", R.drawable.ic_battery, R.string.battery_exemption, R.string.st_battery_desc, R.string.st_battery_desc,
            openText, null, R.string.st_open) { activity?.let { viewModel.requestBatteryExemption(it) } }
    )
    val allRows = modelRows + extraRows
    val selectedDef = allRows.firstOrNull { it.key == selectedKey } ?: modelRows.first()
    // ST06 — dirty = draft differs from persisted settings.
    val isDirty = draft.loaded && draft != draftFromSettings(settings)

    // Initial focus (2026-11-23): the language row (first setting), so the
    // remote (D-pad) moves from the first press.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.settings_title), fontSize = 30.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        // ST01 — two columns: 660dp list + detail panel.
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            LazyColumn(
                modifier = Modifier.width(660.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                modelRows.forEach { def ->
                    item {
                        SettingRow(
                            def = def,
                            selected = def.key == selectedKey,
                            modifier = if (def.key == "lang") Modifier.focusRequester(firstFocus) else Modifier,
                            onSelect = { selectedKey = it }
                        )
                    }
                    // ST14 — custom DNS / UA values, shown under their rows.
                    if (def.key == "dns" && draft.dns == "custom") {
                        item { ReadOnlyField(stringResource(R.string.dns_custom), draft.dnsCustom, modifier = if (lastKeyboardTarget == "dnsCustom") Modifier.focusRequester(keyboardReturnFocus) else Modifier) { openKeyboard("dnsCustom") } }
                    }
                    if (def.key == "ua" && draft.ua == "custom") {
                        item { ReadOnlyField(stringResource(R.string.ua_custom), draft.uaCustom, modifier = if (lastKeyboardTarget == "uaCustom") Modifier.focusRequester(keyboardReturnFocus) else Modifier) { openKeyboard("uaCustom") } }
                    }
                }
                // Profiles (2026-11-22): per-person history + watch-later.
                item {
                    Text(
                        text = stringResource(R.string.profiles_title), fontSize = 22.sp,
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                profiles.forEach { profile ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // R-S3: the active profile shows the ic_check
                            // icon instead of a "✓ " text prefix.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (profile.id == activeProfileId) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        tint = GoldBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = profile.name,
                                    fontSize = 18.sp, color = GoldWhite
                                )
                            }
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
                item { ReadOnlyField(stringResource(R.string.profile_add), "", modifier = if (lastKeyboardTarget == "newProfile") Modifier.focusRequester(keyboardReturnFocus) else Modifier) { openKeyboard("newProfile") } }
                items(extraRows) { def ->
                    SettingRow(
                        def = def, selected = def.key == selectedKey,
                        modifier = if (def.key == "updateUrl" && lastKeyboardTarget == "updateUrl") Modifier.focusRequester(keyboardReturnFocus) else Modifier,
                        onSelect = { selectedKey = it }
                    )
                }
            }
            // ST02 — detail panel for the focused/selected row.
            SettingDetailPanel(
                def = selectedDef,
                pinSet = settings.pin.isNotEmpty(),
                onRemovePin = { viewModel.clearPin() },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        // ST06 — save bar: status + dirty badge + reset + cancel + save.
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x33E5C158), Color(0x0FE5C158))
                    )
                )
                .border(2.dp, BorderStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDirty) stringResource(R.string.st_dirty) else stringResource(R.string.st_clean),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                )
                Text(
                    text = stringResource(R.string.st_save_hint),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldSoft
                )
            }
            if (isDirty) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x2EFF8A70))
                        .border(1.5.dp, Color(0xFFFF8A70), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.st_dirty), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD9CF))
                }
            }
            IconGhostButton(text = stringResource(R.string.st_reset), iconRes = R.drawable.ic_x) { confirmReset = true }
            GhostButton(text = stringResource(R.string.discard)) { if (isDirty) viewModel.discardChanges() }
            GoldSaveButton(
                text = stringResource(R.string.save_changes),
                enabled = isDirty
            ) { viewModel.save(activity) }
        }
        // ST13 — remote hint below the save bar.
        RemoteHintBar(
            listOf(
                "↑ ↓" to stringResource(R.string.hint_choose_row),
                "OK" to stringResource(R.string.hint_open_choice),
                "← →" to stringResource(R.string.hint_choose_col)
            )
        )
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
        "autoResume" -> SelectorDialog(
            title = stringResource(R.string.auto_resume),
            options = autoResumeOptions.map { SelectorOption(it.first, it.second) },
            selectedId = if (draft.autoResume) "yes" else "no",
            onSelect = { id -> viewModel.setAutoResume(id == "yes"); selector = null },
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

    // ST05 — reset confirmation (the prototype asks with confirmDialog).
    if (confirmReset) {
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            // R-B2: plain CardSurface (20dp, theme colour) with the shadow.
            CardSurface {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Text(text = stringResource(R.string.st_reset_title), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                Text(text = stringResource(R.string.st_reset_msg), fontSize = 17.sp, color = GoldSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.st_reset)) {
                        viewModel.resetToDefaults()
                        confirmReset = false
                    }
                    GhostButton(text = stringResource(R.string.cancel)) { confirmReset = false }
                }
                }
            }
        }
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
                // ST14 — IPv4 check for a custom DNS value.
                if (target == "dnsCustom") {
                    Text(
                        text = stringResource(R.string.st_custom_dns_hint),
                        color = GoldSoft, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (dnsError) {
                    Text(
                        text = stringResource(R.string.st_invalid_ip),
                        color = Color(0xFFFF8A70), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                BuiltInKeyboard(
                    onKey = { c -> dnsError = false; setValue(current + c) },
                    onAction = { action ->
                        when (action) {
                            is KeyboardAction.Char -> Unit // already handled via onKey
                            KeyboardAction.Backspace -> { dnsError = false; setValue(current.dropLast(1)) }
                            KeyboardAction.Space -> setValue(current + " ")
                            KeyboardAction.Shift -> { }
                            KeyboardAction.SwitchLanguage -> keyboardLang =
                                if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                            KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                            KeyboardAction.Validate -> {
                                if (target == "dnsCustom" && current.isNotBlank() && !isValidIpv4(current)) {
                                    dnsError = true
                                } else {
                                    dnsError = false
                                    if (target == "newProfile") {
                                        profilesViewModel.addProfile(current)
                                        newProfileName = ""
                                    }
                                    keyboardTarget = null
                                }
                            }
                            KeyboardAction.Close -> { dnsError = false; keyboardTarget = null }
                        }
                    },
                    language = keyboardLang,
                    mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS,
                    onClearAll = { dnsError = false; setValue("") }
                )
            }
        }
    }
}

/** One settings row definition (ST03/ST04). */
private data class SettingRowDef(
    val key: String,
    @DrawableRes val iconRes: Int,
    val titleRes: Int,
    val descRes: Int?,
    val detailRes: Int?,
    val value: String,
    val sub: String?,
    val ctaRes: Int,
    val onOpen: () -> Unit
)

/** Draft rebuilt from persisted settings, for the ST06 dirty check. */
private fun draftFromSettings(s: AppSettings) = SettingsDraft(
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

/** ST14 — strict IPv4 check for a custom DNS value. */
private fun isValidIpv4(value: String): Boolean {
    val parts = value.trim().split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.all { it.isDigit() } &&
            part.length <= 3 && part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

/** ST03 — settings row: 54dp gold icon + 21sp title + 16sp desc + value pill. */
@Composable
private fun SettingRow(
    def: SettingRowDef,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    // R-B2: the row is a CardSurface (same 19dp shape, gradient and 2px
    // border) so it carries the --card-shadow; focus/click stay on the
    // outer surface so the 1.03 scale covers the whole card.
    CardSurface(
        modifier = modifier.fillMaxWidth()
            .tvFocus()
            .clickable { def.onOpen() }
            .onFocusChanged { if (it.isFocused) onSelect(def.key) },
        shape = RoundedCornerShape(19.dp),
        background = if (selected) Brush.linearGradient(listOf(Color(0x47E5C158), Color(0x14E5C158)))
        else Brush.linearGradient(listOf(Color(0xFF1C1406), Color(0xFF0E0A03))),
        border = BorderStroke(2.dp, if (selected) BorderStrong else BorderGold)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GoldIconBox(iconRes = def.iconRes, boxSize = 54.dp, iconSize = 26.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(text = stringResource(def.titleRes), fontSize = 21.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                def.descRes?.let {
                    Text(text = stringResource(it), fontSize = 16.sp, color = GoldSoft)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x8A000000))
                    .border(1.5.dp, BorderGold, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = def.value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
            }
        }
    }
}

/** ST02 — detail panel for the selected row. */
@Composable
private fun SettingDetailPanel(
    def: SettingRowDef,
    pinSet: Boolean,
    onRemovePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    // R-B2: CardSurface with the panel's own 24dp shape, gradient and 2px
    // border, so it carries the --card-shadow.
    CardSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        background = Brush.linearGradient(listOf(Color(0xFF1E1606), Color(0xFF0D0903))),
        border = BorderStroke(2.dp, BorderGold)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Text(
            text = stringResource(R.string.nav_settings).uppercase(),
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold
        )
        Text(
            text = stringResource(def.titleRes), fontSize = 36.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        Text(
            text = stringResource(def.detailRes ?: def.descRes ?: R.string.st_toggle_desc),
            fontSize = 20.sp, color = GoldSoft
        )
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x8A000000))
                .border(1.5.dp, BorderGold, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = stringResource(R.string.st_current_value), fontSize = 15.sp, color = TextMuted)
            Text(text = def.value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
            def.sub?.let { Text(text = it, fontSize = 16.sp, color = GoldSoft) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoldButton(text = stringResource(def.ctaRes)) { def.onOpen() }
            // ST07 — remove the PIN (only when one is set).
            if (def.key == "pin" && pinSet) {
                IconGhostButton(
                    text = stringResource(R.string.st_remove_pin),
                    iconRes = R.drawable.ic_trash
                ) { onRemovePin() }
            }
        }
        }
    }
}

/** Shared gold icon box (54dp by default): gold gradient + dark icon. */
@Composable
internal fun GoldIconBox(
    @DrawableRes iconRes: Int,
    boxSize: Dp = 54.dp,
    iconSize: Dp = 26.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(16.dp))
            .background(GoldGradient),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = GoldDeep,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Ghost button with a leading icon (reset, remove PIN…). */
@Composable
internal fun IconGhostButton(
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // R-B2: CardSurface (plain 20dp) so the button carries the card
    // shadow; focus/click stay on the outer surface.
    CardSurface(
        modifier = modifier.tvFocus().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(20.dp)
            )
            Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
        }
    }
}

/** Gold save button, 60dp like the prototype's .btn-save. */
@Composable
internal fun GoldSaveButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .then(
                if (enabled) Modifier.background(GoldGradient)
                else Modifier.background(Color(0xFF3A2F16))
            )
            .border(2.dp, if (enabled) BorderStrong else BorderGold, RoundedCornerShape(30.dp))
            .tvFocus()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) GoldDeep else TextMuted,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** Remote hint bar: gold key chips + labels. */
@Composable
internal fun RemoteHintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x8A000000))
            .border(1.5.dp, BorderGold, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hints.forEach { (key, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(GoldGradient)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = key, fontSize = 16.sp, fontWeight = FontWeight.Black, color = GoldDeep)
                }
                Text(text = label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GoldSoft)
            }
        }
    }
}
