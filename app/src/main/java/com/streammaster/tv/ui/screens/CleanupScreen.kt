package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.LoadingIndicator
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.CleanupViewModel

/** Route for the playlist cleanup screen (§4.36.6). Registered by the nav builder. */
const val CLEANUP_ROUTE = "cleanup"

/**
 * Playlist cleanup UI (§4.36.6): the scanner's report — duplicate groups
 * with a merge button, dead items with a delete button. Reached from
 * Settings (the settings builder adds the row for this route).
 */
@Composable
fun CleanupScreen(
    navController: NavHostController,
    viewModel: CleanupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Focus (2026-11-23, m-11): merging/deleting removes the focused card,
    // disposing the focused node. Focus moves to a live neighbour BEFORE
    // the action; focusRestorer() on the list is the safety net. The
    // initial focus lands on the first action button once the report
    // arrives (a clean report has no focusable element — read-only).
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    val actionFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun actionFocusFor(key: String) = actionFocusRequesters.getOrPut(key) { FocusRequester() }
    val report = state.report
    LaunchedEffect(state.scanning, report) {
        if (!state.scanning && !initialFocusDone &&
            (report.duplicates.isNotEmpty() || report.dead.isNotEmpty())
        ) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    fun dupKey(group: com.streammaster.tv.ui.viewmodel.DuplicateGroup) =
        "dup:" + group.ids.joinToString(",")
    fun deadKey(item: com.streammaster.tv.ui.viewmodel.DeadItem) =
        "dead:${item.id}"
    fun focusAfterDuplicateRemoval(removedIndex: Int) {
        val next = report.duplicates.getOrNull(removedIndex + 1)
            ?: report.duplicates.getOrNull(removedIndex - 1)
        if (next != null) actionFocusFor(dupKey(next)).requestFocus()
        else if (report.dead.isNotEmpty()) actionFocusFor(deadKey(report.dead.first())).requestFocus()
    }
    fun focusAfterDeadRemoval(removedIndex: Int) {
        val next = report.dead.getOrNull(removedIndex + 1)
            ?: report.dead.getOrNull(removedIndex - 1)
        if (next != null) actionFocusFor(deadKey(next)).requestFocus()
        else if (report.duplicates.isNotEmpty()) actionFocusFor(dupKey(report.duplicates.first())).requestFocus()
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeader(stringResource(R.string.ns_cleanup_title), stringResource(R.string.ns_cleanup_subtitle))
        if (state.scanning) {
            LoadingIndicator(stringResource(R.string.ns_cleanup_scanning))
            return@Column
        }
        state.notice?.let { notice ->
            val text = when {
                notice == "unsupported" -> stringResource(R.string.ns_cleanup_unsupported)
                notice.startsWith("merged:") ->
                    stringResource(R.string.ns_cleanup_merged, notice.removePrefix("merged:"))
                notice.startsWith("deleted:") ->
                    stringResource(R.string.ns_cleanup_deleted, notice.removePrefix("deleted:"))
                else -> notice
            }
            Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldBright)
        }
        if (report.duplicates.isEmpty() && report.dead.isEmpty()) {
            Text(text = stringResource(R.string.ns_cleanup_clean), fontSize = 18.sp, color = TextMuted)
            return@Column
        }
        LazyColumn(modifier = Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (report.duplicates.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.ns_cleanup_duplicates, report.duplicates.size))
                }
                itemsIndexed(report.duplicates) { index, group ->
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(text = group.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                                Text(
                                    text = "${group.kind} • ${group.ids.size} ×",
                                    fontSize = 17.sp, color = TextMuted
                                )
                            }
                            GoldButton(
                                text = stringResource(R.string.ns_cleanup_merge),
                                modifier = Modifier.focusRequester(actionFocusFor(dupKey(group)))
                                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            ) {
                                focusAfterDuplicateRemoval(index)
                                viewModel.merge(group)
                            }
                        }
                    }
                }
            }
            if (report.dead.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.ns_cleanup_dead, report.dead.size))
                }
                itemsIndexed(report.dead) { index, item ->
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(text = item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                                Text(text = item.reason, fontSize = 17.sp, color = TextMuted)
                            }
                            GhostButton(
                                text = stringResource(R.string.ns_cleanup_delete),
                                modifier = Modifier.focusRequester(actionFocusFor(deadKey(item)))
                                    .then(if (report.duplicates.isEmpty() && index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            ) {
                                focusAfterDeadRemoval(index)
                                viewModel.deleteDead(item)
                            }
                        }
                    }
                }
            }
        }
    }
}
