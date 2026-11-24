package com.streammaster.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streammaster.tv.R
import com.streammaster.tv.data.drive.DriveClient
import com.streammaster.tv.data.drive.DrivePrefs
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SPEC §4.63 — Google Drive screen (link status + backup to / restore
 * from Drive). Standalone: the navigation builder adds the route and the
 * Settings builder adds the entry point — this file does NOT touch
 * navigation or SettingsScreen.
 *
 * CONTRACT — add to the NavHost (app builder):
 * ```kotlin
 * composable(Routes.DRIVE) { DriveScreen() }
 * ```
 * and pass the app's BackupManager once Drive is linked:
 * ```kotlin
 * DriveScreen(
 *     backupExport = backupManager::export,   // suspend () -> String
 *     backupImport = backupManager::import    // suspend (String) -> Boolean
 * )
 * ```
 * Without those lambdas the backup buttons stay disabled with an
 * explanatory note — Drive link state itself works without them.
 *
 * ### What the coach must still do (blocking — see the task report)
 * 1. Create OAuth credentials in HIS Google Cloud Console (project +
 *    consent screen + Android Client ID with the app SHA-1), enable the
 *    Drive API with the `drive.file` scope.
 * 2. Enter that Client ID below (stored in [DrivePrefs], never hard-coded).
 * 3. Sign in on the device — the sign-in step itself needs the
 *    `play-services-auth` dependency (deliberately NOT added here; only
 *    the Chromecast dependency was authorised), so the Link button
 *    currently reports "sign-in not wired yet" once a Client ID exists.
 * Until then, NO file is uploaded anywhere. APKs are NEVER uploaded to
 * Drive — they stay on GitHub Releases.
 */
class DriveViewModel(
    private val prefs: DrivePrefs,
    private val client: DriveClient,
    private val backupExport: (suspend () -> String)? = null,
    private val backupImport: (suspend (String) -> Boolean)? = null
) : ViewModel() {

    val driveState = prefs.state

    private val _message = MutableStateFlow<Int?>(null)
    /** String resource of the last status message, if any. */
    val message: StateFlow<Int?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun saveClientId(clientId: String) {
        viewModelScope.launch {
            prefs.setClientId(clientId)
            _message.value = R.string.drive_client_saved
        }
    }

    /**
     * Link step 2 (sign-in) is NOT wired yet: it needs Google Sign-In
     * (play-services-auth) + the coach's Cloud Console Client ID. This
     * reports that honestly instead of pretending to link.
     */
    fun link() {
        viewModelScope.launch {
            val state = prefs.state.first()
            _message.value = when {
                state.needsOAuthSetup -> R.string.drive_need_oauth
                else -> R.string.drive_signin_pending
            }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            client.unlink()
            _message.value = R.string.drive_unlinked
        }
    }

    fun backupToDrive() {
        val export = backupExport ?: run {
            _message.value = R.string.drive_backup_not_wired
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching {
                client.uploadBackup(export())
            }.getOrElse { DriveClient.DriveResult.Error(it.message ?: "") }
            _busy.value = false
            _message.value = when (result) {
                is DriveClient.DriveResult.Ok -> R.string.drive_backup_ok
                DriveClient.DriveResult.NotConfigured -> R.string.drive_need_oauth
                DriveClient.DriveResult.NotLinked -> R.string.drive_not_linked
                is DriveClient.DriveResult.Error -> R.string.drive_error
            }
        }
    }

    fun restoreFromDrive() {
        val import = backupImport ?: run {
            _message.value = R.string.drive_backup_not_wired
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val (result, json) = runCatching { client.downloadBackup() }
                .getOrElse { DriveClient.DriveResult.Error(it.message ?: "") to null }
            _busy.value = false
            _message.value = when {
                result is DriveClient.DriveResult.NotConfigured -> R.string.drive_need_oauth
                result is DriveClient.DriveResult.NotLinked -> R.string.drive_not_linked
                result is DriveClient.DriveResult.Error -> R.string.drive_error
                json.isNullOrBlank() -> R.string.drive_no_backup
                else -> {
                    val ok = runCatching { import(json) }.getOrDefault(false)
                    if (ok) R.string.drive_restore_ok else R.string.drive_restore_failed
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

@Composable
fun DriveScreen(
    modifier: Modifier = Modifier,
    backupExport: (suspend () -> String)? = null,
    backupImport: (suspend (String) -> Boolean)? = null,
    viewModel: DriveViewModel = run {
        val context = LocalContext.current
        val factory = remember(context, backupExport, backupImport) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val prefs = DrivePrefs(context)
                    return DriveViewModel(prefs, DriveClient(prefs), backupExport, backupImport) as T
                }
            }
        }
        viewModel<DriveViewModel>(factory = factory)
    }
) {
    val state by viewModel.driveState.collectAsState(initial = DrivePrefs.DriveState())
    val messageRes by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var clientIdDialogOpen by remember { mutableStateOf(false) }
    // Focus (2026-11-23): initial focus on the Client ID button, and
    // focus returns to it when the dialog closes (m-12).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(clientIdDialogOpen) {
        if (!clientIdDialogOpen) firstFocus.requestFocus()
    }
    // Back closes the dialog first, instead of leaving the screen.
    BackHandler(enabled = clientIdDialogOpen) { clientIdDialogOpen = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        Text(
            text = stringResource(R.string.drive_title),
            color = GoldWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.drive_description),
            color = TextMuted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(24.dp))

        // Link status.
        val statusText = when {
            state.needsOAuthSetup -> stringResource(R.string.drive_status_need_oauth)
            state.linked && state.hasValidToken ->
                stringResource(R.string.drive_status_linked, state.account.ifBlank { "Google" })
            state.linked -> stringResource(R.string.drive_status_token_expired)
            else -> stringResource(R.string.drive_status_not_linked)
        }
        Text(text = statusText, color = GoldWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (state.clientId.isNotBlank()) {
            Text(
                text = stringResource(R.string.drive_client_set),
                color = TextMuted,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        messageRes?.let { res ->
            Text(text = stringResource(res), color = GoldWhite, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        GoldButton(
            text = stringResource(R.string.drive_enter_client_id),
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            onClick = { clientIdDialogOpen = true }
        )
        Spacer(Modifier.height(12.dp))
        if (state.linked) {
            GhostButton(
                text = stringResource(R.string.drive_unlink),
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.unlink() }
            )
        } else {
            GoldButton(
                text = stringResource(R.string.drive_link),
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                onClick = { viewModel.link() }
            )
        }
        Spacer(Modifier.height(24.dp))

        // Backup to / restore from Drive (second destination for BackupManager).
        Text(
            text = stringResource(R.string.drive_backup_title),
            color = GoldWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        GoldButton(
            text = stringResource(R.string.drive_backup_now),
            modifier = Modifier.fillMaxWidth(),
            enabled = state.ready && !busy && backupExport != null,
            onClick = { viewModel.backupToDrive() }
        )
        Spacer(Modifier.height(12.dp))
        GhostButton(
            text = stringResource(R.string.drive_restore_now),
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.restoreFromDrive() }
        )
        if (backupExport == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.drive_backup_not_wired),
                color = TextMuted,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.drive_never_apk),
            color = TextMuted,
            fontSize = 14.sp
        )
    }

    if (clientIdDialogOpen) {
        DriveClientIdDialog(
            initial = state.clientId,
            onSave = { id ->
                viewModel.saveClientId(id)
                clientIdDialogOpen = false
            },
            onDismiss = { clientIdDialogOpen = false }
        )
    }
}

/** TV-remote entry for the OAuth Client ID (built-in keyboard). */
@Composable
private fun DriveClientIdDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS) }
    var language by remember { mutableStateOf(KeyboardLanguage.FR) }

    // Focus (2026-11-23, m-12): a real Dialog — it steals focus on open
    // (the built-in keyboard focuses its first key), traps focus inside
    // while open, and Back / onDismissRequest closes it and returns focus
    // to the button that opened it. Previously this was a plain Column
    // stacked over the screen, so focus wandered freely behind it.
    val cardShape = RoundedCornerShape(30.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 980.dp).fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        0.0f to Color(0xFA221807),
                        1.0f to Color(0xFC0C0903)
                    ),
                    cardShape
                )
                .border(2.5.dp, BorderStrong, cardShape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.drive_client_id_title),
                color = GoldWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.drive_client_id_hint),
                color = TextMuted,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(text = text.ifBlank { "…" }, color = GoldWhite, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            BuiltInKeyboard(
                onKey = { text += it },
                onAction = { action ->
                    when (action) {
                        // Char is already handled via onKey — adding it
                        // again here typed every character twice.
                        is KeyboardAction.Char -> Unit
                        KeyboardAction.Backspace -> text = text.dropLast(1)
                        KeyboardAction.Space -> text += " "
                        KeyboardAction.Shift -> Unit
                        KeyboardAction.SwitchLanguage ->
                            language = if (language == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                        KeyboardAction.Switch123 ->
                            mode = if (mode == KeyboardMode.LETTERS) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
                        KeyboardAction.Validate -> onSave(text)
                        KeyboardAction.Close -> onDismiss()
                    }
                },
                language = language,
                mode = mode
            )
            Spacer(Modifier.height(12.dp))
            GoldButton(
                text = stringResource(R.string.save),
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank(),
                onClick = { onSave(text) }
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = stringResource(R.string.cancel),
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss
            )
        }
    }
}
