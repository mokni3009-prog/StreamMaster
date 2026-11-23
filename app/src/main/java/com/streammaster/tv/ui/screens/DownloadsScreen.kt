package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.DownloadStatus
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.DownloadsViewModel
import kotlinx.coroutines.delay

/** Downloads — offline VOD files: progress, play, pause/resume, delete. */
@Composable
fun DownloadsScreen(
    navController: NavHostController,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    // Initial focus: the first download row. The row only exists once the
    // data has loaded — a LaunchedEffect(Unit) fires before that and the
    // request is lost — so the request is re-tried (once) when the list
    // arrives.
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    // §4.1 — return to the same item: PlayerScreen writes the played id
    // into this entry's savedStateHandle ("focusedId") when exiting the
    // player (PlayerScreen.kt:194). When it names a download still in the
    // list, focus that row (scrolling it into view) instead of the default
    // first row. Null on a fresh entry (not a return).
    val restoreId = navController.currentBackStackEntry?.savedStateHandle?.get<String>("focusedId")
    val hasRestoreTarget = restoreId != null && downloads.any { it.id == restoreId }
    val restoreFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun focusFor(id: String) = restoreFocusRequesters.getOrPut(id) { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(downloads.isNotEmpty()) {
        if (downloads.isNotEmpty() && !initialFocusDone && !hasRestoreTarget) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    LaunchedEffect(downloads, restoreId) {
        if (initialFocusDone) return@LaunchedEffect
        val target = restoreId?.takeIf { id -> downloads.any { it.id == id } } ?: return@LaunchedEffect
        val index = downloads.indexOfFirst { it.id == target }
        if (index > 0) {
            listState.scrollToItem(index)
            delay(100) // let the row compose after the scroll before focusing it
        }
        if (index == 0) firstFocus.requestFocus()
        else focusFor(target).requestFocus()
        initialFocusDone = true
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.downloads_title),
            fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        if (downloads.isEmpty()) {
            Text(text = stringResource(R.string.downloads_empty), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(downloads) { index, download ->
                    val pct = if (download.totalBytes > 0) {
                        (download.downloadedBytes * 100 / download.totalBytes).toInt().coerceIn(0, 100)
                    } else 0
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .then(
                                when {
                                    index == 0 -> Modifier.focusRequester(firstFocus)
                                    download.id == restoreId -> Modifier.focusRequester(focusFor(download.id))
                                    else -> Modifier
                                }
                            )
                            .tvFocus()
                            .clickable {
                                if (download.status == DownloadStatus.DONE) {
                                    navController.navigate(Routes.player("download", download.id))
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = download.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                            val statusText = when (download.status) {
                                DownloadStatus.DOWNLOADING -> "${stringResource(R.string.download_downloading)} · $pct%"
                                DownloadStatus.QUEUED -> stringResource(R.string.download_queued)
                                DownloadStatus.PAUSED -> stringResource(R.string.download_paused)
                                DownloadStatus.DONE -> formatSize(download.downloadedBytes)
                                DownloadStatus.ERROR -> stringResource(R.string.download_error)
                            }
                            Text(text = statusText, fontSize = 15.sp, color = TextMuted)
                            if (download.status == DownloadStatus.DOWNLOADING ||
                                download.status == DownloadStatus.QUEUED
                            ) {
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = Gold, trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (download.status) {
                                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                                    GhostButton(text = "⏸") { viewModel.pause(download) }
                                }
                                DownloadStatus.PAUSED -> {
                                    GoldButton(text = "▶") { viewModel.resume(download) }
                                }
                                DownloadStatus.ERROR -> {
                                    GoldButton(text = stringResource(R.string.download_retry)) { viewModel.retry(download) }
                                }
                                DownloadStatus.DONE -> { /* play via the row click */ }
                            }
                            if (confirmDeleteId == download.id) {
                                GoldButton(text = stringResource(R.string.confirm_delete)) {
                                    viewModel.delete(download)
                                    confirmDeleteId = null
                                }
                            } else {
                                GhostButton(text = stringResource(R.string.delete)) { confirmDeleteId = download.id }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f Go".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f Mo".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f Ko".format(bytes / 1024.0)
        else -> "$bytes o"
    }
}
