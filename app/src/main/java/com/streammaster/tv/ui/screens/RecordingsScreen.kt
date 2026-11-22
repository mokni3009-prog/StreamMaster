package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.RecordingStatus
import com.streammaster.tv.player.RecordingState
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.RecordingsViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Enregistrements — instant DVR recordings (method 2): play, stop, delete. */
@Composable
fun RecordingsScreen(
    navController: NavHostController,
    viewModel: RecordingsViewModel = hiltViewModel()
) {
    val recordings by viewModel.recordings.collectAsState()
    val scheduled by viewModel.scheduled.collectAsState()
    val seriesRules by viewModel.seriesRules.collectAsState()
    val recState by viewModel.recordingState.collectAsState()
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    // 1s ticker so the in-progress elapsed time stays live.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val totalBytes = recordings.sumOf { it.sizeBytes }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.recordings_title),
                fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
            )
            if (recordings.isNotEmpty()) {
                Text(text = stringResource(R.string.recordings_total, formatSize(totalBytes)), fontSize = 16.sp, color = TextMuted)
            }
        }

        // Running recording first, with a Stop button.
        val active = recState as? RecordingState.Active
        if (active != null) {
            val elapsedS = ((now - active.startedAtMillis) / 1000).toInt()
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "● REC ${formatDuration(elapsedS * 1000L)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                    Text(text = active.recording.channelName, fontSize = 15.sp, color = TextMuted)
                }
                GoldButton(text = stringResource(R.string.record_stop)) { viewModel.stopRecording() }
            }
        }

        // Scheduled recordings (from the EPG), soonest first.
        if (scheduled.isNotEmpty()) {
            Text(
                text = stringResource(R.string.scheduled_title),
                fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(scheduled) { rec ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = rec.programTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                            Text(
                                text = "${rec.channelName} · ${dateFormat.format(Date(rec.startMillis))} · " +
                                    stringResource(
                                        when (rec.repeat) {
                                            "daily" -> R.string.repeat_daily
                                            "weekly" -> R.string.repeat_weekly
                                            else -> R.string.repeat_once
                                        }
                                    ),
                                fontSize = 15.sp, color = TextMuted
                            )
                        }
                        if (confirmDeleteId == rec.id) {
                            GoldButton(text = stringResource(R.string.confirm_delete)) {
                                viewModel.cancelScheduled(rec)
                                confirmDeleteId = null
                            }
                        } else {
                            GhostButton(text = stringResource(R.string.cancel)) { confirmDeleteId = rec.id }
                        }
                    }
                }
            }
        }

        // Series-link recording rules: every future airing of the title
        // on this channel is recorded automatically (2026-11-22).
        if (seriesRules.isNotEmpty()) {
            Text(
                text = stringResource(R.string.series_rules_title),
                fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(seriesRules) { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "▣ ${rule.programTitle}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                            Text(
                                text = "${rule.channelName} · ${stringResource(R.string.series_rule_auto)}",
                                fontSize = 15.sp, color = TextMuted
                            )
                        }
                        if (confirmDeleteId == rule.id) {
                            GoldButton(text = stringResource(R.string.confirm_delete)) {
                                viewModel.cancelSeriesRule(rule)
                                confirmDeleteId = null
                            }
                        } else {
                            GhostButton(text = stringResource(R.string.cancel)) { confirmDeleteId = rule.id }
                        }
                    }
                }
            }
        }

        if (recordings.isEmpty()) {
            Text(text = stringResource(R.string.no_recordings), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recordings) { recording ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .tvFocus()
                            .clickable {
                                if (recording.status != RecordingStatus.RECORDING) {
                                    navController.navigate(Routes.player("recording", recording.id))
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = recording.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                            val status = if (recording.status == RecordingStatus.RECORDING) {
                                stringResource(R.string.recording_in_progress)
                            } else if (recording.status == RecordingStatus.ERROR) {
                                stringResource(R.string.recording_failed)
                            } else {
                                "${dateFormat.format(Date(recording.startMillis))} · ${formatDuration(recording.durationMs)} · ${formatSize(recording.sizeBytes)}"
                            }
                            Text(text = status, fontSize = 15.sp, color = TextMuted)
                        }
                        if (recording.status != RecordingStatus.RECORDING) {
                            if (confirmDeleteId == recording.id) {
                                GoldButton(text = stringResource(R.string.confirm_delete)) {
                                    viewModel.delete(recording)
                                    confirmDeleteId = null
                                }
                            } else {
                                GhostButton(text = stringResource(R.string.delete)) { confirmDeleteId = recording.id }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824L -> "%.1f Go".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f Mo".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.0f Ko".format(bytes / 1024.0)
        else -> "$bytes o"
    }
}
