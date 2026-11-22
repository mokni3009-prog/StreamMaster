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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Downloads — offline VOD files: progress, play, pause/resume, delete. */
@Composable
fun DownloadsScreen(
    navController: NavHostController,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.downloads_title),
            fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        if (downloads.isEmpty()) {
            Text(text = stringResource(R.string.downloads_empty), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(downloads) { download ->
                    val pct = if (download.totalBytes > 0) {
                        (download.downloadedBytes * 100 / download.totalBytes).toInt().coerceIn(0, 100)
                    } else 0
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
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
