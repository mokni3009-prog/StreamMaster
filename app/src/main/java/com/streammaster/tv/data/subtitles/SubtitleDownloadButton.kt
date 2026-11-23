package com.streammaster.tv.data.subtitles

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.screens.GhostButton
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * SPEC §4.48 — Arabic subtitle download button for DetailScreen.
 *
 * CONTRACT — the Detail builder drops this into the film/series detail
 * page (next to the other action buttons); NOTHING else is needed:
 * ```kotlin
 * SubtitleDownloadButton(
 *     itemType = "film",        // "film" | "series" | "episode" (informational)
 *     itemId = film.id,         // file key: files/subtitles/<itemId>.srt
 *     title = film.title,       // searched on OpenSubtitles
 *     year = film.year          // nullable — pass null when unknown
 * )
 * ```
 * Behaviour: idle → "Sous-titres AR"; tap downloads the most-downloaded
 * Arabic subtitle and saves it as `files/subtitles/<itemId>.srt`
 * (re-download overwrites). Shows downloading / done / not-found states.
 * When no OpenSubtitles API key is saved yet, tap opens the key-entry
 * dialog (built-in TV keyboard, FR letters + 123 symbols) — the key comes
 * from the user's own free OpenSubtitles account and is stored in
 * [SubtitlePrefs]; there is no hard-coded key anywhere.
 *
 * The player engine (§4.61) picks the file up with
 * `context.findLocalSubtitle(itemId)` — see SubtitleDownloader.kt.
 */
@Composable
fun SubtitleDownloadButton(
    itemType: String,
    itemId: String,
    title: String,
    year: Int? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember(context) { SubtitleDownloader(context) }
    val subtitlePrefs = remember(context) { SubtitlePrefs(context) }
    val apiKey by subtitlePrefs.apiKey.collectAsState(initial = "")

    var status by remember(itemId) {
        mutableStateOf(
            if (downloader.hasLocalSubtitle(itemId)) SubtitleStatus.Downloaded
            else SubtitleStatus.Idle
        )
    }
    var keyDialogOpen by remember { mutableStateOf(false) }

    val label = when (status) {
        SubtitleStatus.Idle -> stringResource(R.string.sub_dl_button)
        SubtitleStatus.Downloading -> stringResource(R.string.sub_dl_downloading)
        SubtitleStatus.Downloaded -> stringResource(R.string.sub_dl_done)
        SubtitleStatus.NotFound -> stringResource(R.string.sub_dl_not_found)
        SubtitleStatus.NeedsKey -> stringResource(R.string.sub_dl_need_key)
        SubtitleStatus.NeedsLogin -> stringResource(R.string.sub_dl_need_login)
        SubtitleStatus.Error -> stringResource(R.string.sub_dl_error)
    }

    GhostButton(
        text = label,
        modifier = modifier,
        onClick = {
            if (apiKey.isBlank()) {
                status = SubtitleStatus.NeedsKey
                keyDialogOpen = true
                return@GhostButton
            }
            if (status == SubtitleStatus.Downloading) return@GhostButton
            status = SubtitleStatus.Downloading
            scope.launch {
                status = when (downloader.downloadBest(title, year, itemId)) {
                    is SubtitleDownloader.DownloadResult.Ok -> SubtitleStatus.Downloaded
                    SubtitleDownloader.DownloadResult.NoApiKey -> SubtitleStatus.NeedsKey
                    SubtitleDownloader.DownloadResult.NotFound -> SubtitleStatus.NotFound
                    SubtitleDownloader.DownloadResult.NeedsLogin -> SubtitleStatus.NeedsLogin
                    is SubtitleDownloader.DownloadResult.Error -> SubtitleStatus.Error
                }
                if (status == SubtitleStatus.NeedsKey) keyDialogOpen = true
            }
        }
    )

    if (keyDialogOpen) {
        ApiKeyDialog(
            initialKey = apiKey,
            onSave = { key ->
                scope.launch {
                    subtitlePrefs.setApiKey(key)
                    keyDialogOpen = false
                    status = SubtitleStatus.Idle
                }
            },
            onDismiss = { keyDialogOpen = false }
        )
    }
}

private enum class SubtitleStatus {
    Idle, Downloading, Downloaded, NotFound, NeedsKey, NeedsLogin, Error
}

/** TV-remote key entry: built-in keyboard, no system keyboard needed. */
@Composable
private fun ApiKeyDialog(
    initialKey: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialKey) }
    var mode by remember { mutableStateOf(KeyboardMode.LETTERS) }
    var language by remember { mutableStateOf(KeyboardLanguage.FR) }

    Column(modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        Text(
            text = stringResource(R.string.sub_api_key_title),
            color = GoldWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.sub_api_key_hint),
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
                    is KeyboardAction.Char -> text += action.c
                    KeyboardAction.Backspace -> text = text.dropLast(1)
                    KeyboardAction.Space -> text += " "
                    KeyboardAction.Shift -> Unit // shift handled inside the keyboard
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

/** Kept for callers that only need the file lookup without a downloader. */
fun findLocalSubtitleFor(context: Context, itemId: String) = context.findLocalSubtitle(itemId)
