package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeader(stringResource(R.string.ns_cleanup_title), stringResource(R.string.ns_cleanup_subtitle))
        if (state.scanning) {
            LoadingIndicator(stringResource(R.string.ns_cleanup_scanning))
            return@Column
        }
        val report = state.report
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (report.duplicates.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.ns_cleanup_duplicates, report.duplicates.size))
                }
                items(report.duplicates) { group ->
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
                            GoldButton(text = stringResource(R.string.ns_cleanup_merge)) { viewModel.merge(group) }
                        }
                    }
                }
            }
            if (report.dead.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.ns_cleanup_dead, report.dead.size))
                }
                items(report.dead) { item ->
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(text = item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                                Text(text = item.reason, fontSize = 17.sp, color = TextMuted)
                            }
                            GhostButton(text = stringResource(R.string.ns_cleanup_delete)) { viewModel.deleteDead(item) }
                        }
                    }
                }
            }
        }
    }
}
