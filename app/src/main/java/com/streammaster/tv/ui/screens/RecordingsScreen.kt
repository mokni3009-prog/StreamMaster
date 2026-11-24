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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
    val hasActiveRecording = recState is RecordingState.Active
    val focusOnScheduled = !hasActiveRecording && scheduled.isNotEmpty()
    val focusOnRule = !hasActiveRecording && !focusOnScheduled && seriesRules.isNotEmpty()
    val focusOnRecording = !hasActiveRecording && !focusOnScheduled && !focusOnRule && recordings.isNotEmpty()
    // Initial focus: the Stop button while recording, else the first
    // scheduled / rule / recorded row's first focusable element. The
    // target only exists once the data has loaded — a LaunchedEffect(Unit)
    // fires before that and the request is lost — so the request is
    // re-tried (once) when the target section appears.
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    // §4.1 — return to the same item: PlayerScreen writes the played id
    // into this entry's savedStateHandle ("focusedId") when exiting the
    // player (PlayerScreen.kt:194). When it names a recording still in the
    // list, focus that row (scrolling it into view) instead of the default
    // first focusable element. Null on a fresh entry (not a return).
    val restoreId = navController.currentBackStackEntry?.savedStateHandle?.get<String>("focusedId")
    val hasRestoreTarget = restoreId != null && recordings.any { it.id == restoreId }
    val restoreFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun focusFor(id: String) = restoreFocusRequesters.getOrPut(id) { FocusRequester() }
    val listState = rememberLazyListState()
    // Focus (2026-11-23, m-08): the confirm button replaces the focused
    // "Annuler"/"Supprimer" button, and a delete removes the focused row
    // — both dispose the focused node. The confirm button is re-focused
    // when it appears, and focus is moved to a live neighbour BEFORE the
    // row is removed; focusRestorer() on the list is the safety net.
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(confirmDeleteId) {
        if (confirmDeleteId != null) {
            delay(50)
            confirmFocus.requestFocus()
        }
    }
    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun rowFocusFor(id: String) = rowFocusRequesters.getOrPut(id) { FocusRequester() }
    fun focusNeighbour(ids: List<String>, removedId: String): Boolean {
        val idx = ids.indexOf(removedId)
        val next = ids.getOrNull(idx + 1) ?: ids.getOrNull(idx - 1)
        if (next != null) {
            rowFocusFor(next).requestFocus()
            return true
        }
        return false
    }
    val focusTarget = when {
        hasActiveRecording -> "active"
        focusOnScheduled -> "scheduled"
        focusOnRule -> "rule"
        focusOnRecording -> "recording"
        else -> null
    }
    LaunchedEffect(focusTarget) {
        if (focusTarget != null && !initialFocusDone && !hasRestoreTarget) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    LaunchedEffect(recordings, restoreId) {
        if (initialFocusDone) return@LaunchedEffect
        val target = restoreId?.takeIf { id -> recordings.any { it.id == id } } ?: return@LaunchedEffect
        val index = recordings.indexOfFirst { it.id == target }
        if (index > 0) {
            // Absolute LazyColumn index: title (1) + active-recording row
            // (0/1) + scheduled section (header + rows) + rules section
            // (header + rows) + the recording's own position.
            val lazyIndex = 1 +
                (if (recState is RecordingState.Active) 1 else 0) +
                (if (scheduled.isNotEmpty()) 1 + scheduled.size else 0) +
                (if (seriesRules.isNotEmpty()) 1 + seriesRules.size else 0) +
                index
            listState.scrollToItem(lazyIndex)
            delay(100) // let the row compose after the scroll before focusing it
        }
        if (index == 0 && focusOnRecording) firstFocus.requestFocus()
        else focusFor(target).requestFocus()
        initialFocusDone = true
    }

    val active = recState as? RecordingState.Active
    // 2026-11-23: one LazyColumn for the whole screen (was an outer Column
    // with three nested LazyColumns). The first nested list took all remaining
    // height, so when two sections were populated the later ones got zero
    // height and were unreachable — on 1080p as well as on 720p.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.recordings_title),
                    fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
                )
                if (recordings.isNotEmpty()) {
                    Text(text = stringResource(R.string.recordings_total, formatSize(totalBytes)), fontSize = 16.sp, color = TextMuted)
                }
            }
        }

        // Running recording first, with a Stop button.
        if (active != null) {
            val elapsedS = ((now - active.startedAtMillis) / 1000).toInt()
            item {
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
                    GoldButton(
                        text = stringResource(R.string.record_stop),
                        modifier = if (hasActiveRecording) Modifier.focusRequester(firstFocus) else Modifier
                    ) {
                        // The active row disappears on stop: move focus to
                        // the first live row before stopping (m-08c).
                        if (scheduled.isNotEmpty()) rowFocusFor(scheduled.first().id).requestFocus()
                        else if (seriesRules.isNotEmpty()) rowFocusFor(seriesRules.first().id).requestFocus()
                        else if (recordings.isNotEmpty()) rowFocusFor(recordings.first().id).requestFocus()
                        viewModel.stopRecording()
                    }
                }
            }
        }

        // Scheduled recordings (from the EPG), soonest first.
        if (scheduled.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.scheduled_title),
                    fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
                )
            }
            itemsIndexed(scheduled) { index, rec ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        // The row itself is focusable (no click of its
                        // own — its action lives in the Annuler button);
                        // without this the D-pad can only land on the
                        // buttons and never on the row.
                        // ص-19: the initial focus lands on the row itself,
                        // not on its Annuler button (OK by mistake would
                        // start cancelling the scheduled recording).
                        .then(if (focusOnScheduled && index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusRequester(rowFocusFor(rec.id))
                        .tvFocus()
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
                        GoldButton(
                            text = stringResource(R.string.confirm_delete),
                            modifier = Modifier.focusRequester(confirmFocus)
                        ) {
                            if (!focusNeighbour(scheduled.map { it.id }, rec.id)) {
                                if (seriesRules.isNotEmpty()) rowFocusFor(seriesRules.first().id).requestFocus()
                                else if (recordings.isNotEmpty()) rowFocusFor(recordings.first().id).requestFocus()
                            }
                            viewModel.cancelScheduled(rec)
                            confirmDeleteId = null
                        }
                    } else {
                        GhostButton(text = stringResource(R.string.cancel)) { confirmDeleteId = rec.id }
                    }
                }
            }
        }

        // Series-link recording rules: every future airing of the title
        // on this channel is recorded automatically (2026-11-22).
        if (seriesRules.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.series_rules_title),
                    fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
                )
            }
            itemsIndexed(seriesRules) { index, rule ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        // The row itself is focusable (no click of its
                        // own — its action lives in the Annuler button);
                        // without this the D-pad can only land on the
                        // buttons and never on the row.
                        // ص-19: the initial focus lands on the row itself,
                        // not on its Annuler button.
                        .then(if (focusOnRule && index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusRequester(rowFocusFor(rule.id))
                        .tvFocus()
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
                        GoldButton(
                            text = stringResource(R.string.confirm_delete),
                            modifier = Modifier.focusRequester(confirmFocus)
                        ) {
                            if (!focusNeighbour(seriesRules.map { it.id }, rule.id)) {
                                if (recordings.isNotEmpty()) rowFocusFor(recordings.first().id).requestFocus()
                                else if (scheduled.isNotEmpty()) rowFocusFor(scheduled.first().id).requestFocus()
                            }
                            viewModel.cancelSeriesRule(rule)
                            confirmDeleteId = null
                        }
                    } else {
                        GhostButton(text = stringResource(R.string.cancel)) { confirmDeleteId = rule.id }
                    }
                }
            }
        }

        if (recordings.isEmpty()) {
            item {
                Text(text = stringResource(R.string.no_recordings), fontSize = 18.sp, color = TextMuted)
            }
        } else {
            itemsIndexed(recordings) { index, recording ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        .then(
                            when {
                                focusOnRecording && index == 0 -> Modifier.focusRequester(firstFocus)
                                recording.id == restoreId -> Modifier.focusRequester(focusFor(recording.id))
                                else -> Modifier
                            }
                        )
                        .focusRequester(rowFocusFor(recording.id))
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
                            GoldButton(
                                text = stringResource(R.string.confirm_delete),
                                modifier = Modifier.focusRequester(confirmFocus)
                            ) {
                                if (!focusNeighbour(recordings.map { it.id }, recording.id)) {
                                    if (scheduled.isNotEmpty()) rowFocusFor(scheduled.first().id).requestFocus()
                                    else if (seriesRules.isNotEmpty()) rowFocusFor(seriesRules.first().id).requestFocus()
                                }
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
