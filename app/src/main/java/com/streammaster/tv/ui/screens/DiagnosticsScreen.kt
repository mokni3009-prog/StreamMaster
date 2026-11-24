package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.DiagnosticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics screen (§4.38): server status, network, session stall/error
 * counts, recent local errors, app version. Local only — no send button,
 * no telemetry of any kind.
 */
@Composable
fun DiagnosticsScreen(
    navController: NavHostController,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Focus (2026-11-23, ص-01): initial focus on the check-server button
    // once the diagnostics have loaded.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.loading) {
        if (!state.loading) firstFocus.requestFocus()
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SectionHeader(stringResource(R.string.ns_diagnostics_title), stringResource(R.string.ns_diagnostics_subtitle))
        if (state.loading) {
            LoadingIndicator()
            return@Column
        }
        CardSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val server = state.serverStatus
                DiagRow(
                    stringResource(R.string.ns_diag_server),
                    when {
                        state.serverName.isBlank() -> stringResource(R.string.ns_diag_server_none)
                        server == null -> stringResource(R.string.ns_diag_offline)
                        server.online -> stringResource(R.string.ns_diag_online)
                        else -> stringResource(R.string.ns_diag_offline)
                    },
                    ok = server?.online == true
                )
                if (server != null) {
                    DiagRow(
                        stringResource(R.string.ns_diag_latency, server.latencyMs, server.detail),
                        "",
                        ok = server.online
                    )
                }
                DiagRow(
                    stringResource(R.string.ns_diag_network),
                    when (state.network) {
                        "wifi" -> stringResource(R.string.ns_diag_network_wifi)
                        "mobile" -> stringResource(R.string.ns_diag_network_mobile)
                        "ethernet" -> stringResource(R.string.ns_diag_network_ethernet)
                        "offline" -> stringResource(R.string.ns_diag_network_offline)
                        else -> stringResource(R.string.ns_diag_network_unknown)
                    },
                    ok = state.network != "offline"
                )
                DiagRow(
                    stringResource(R.string.ns_diag_stalls),
                    state.stallCountSession.toString(),
                    ok = state.stallCountSession == 0
                )
                DiagRow(
                    stringResource(R.string.ns_diag_errors),
                    state.errorCountSession.toString(),
                    ok = state.errorCountSession == 0
                )
                DiagRow(stringResource(R.string.ns_diag_version), state.appVersion, ok = true)
            }
        }
        GoldButton(
            text = if (state.serverChecking) stringResource(R.string.ns_diag_checking)
            else stringResource(R.string.ns_diag_check_server),
            enabled = !state.serverChecking,
            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)
        ) { viewModel.checkServer() }

        SectionHeader(stringResource(R.string.ns_diag_recent_errors))
        if (state.recentErrors.isEmpty()) {
            Text(text = stringResource(R.string.ns_diag_no_errors), fontSize = 18.sp, color = TextMuted)
        } else {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            state.recentErrors.forEach { event ->
                // L-09 — error cards are focusable (read-only, but the
                // remote can scroll the list through them; before, the
                // only focusable element was the check-server button).
                CardSurface(modifier = Modifier.fillMaxWidth().tvFocus()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = fmt.format(Date(event.timestampMillis)),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                        Text(
                            text = event.detail.ifBlank { event.eventType },
                            fontSize = 18.sp, color = GoldWhite
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.ns_diag_no_telemetry),
            fontSize = 16.sp, color = TextMuted
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
        if (value.isNotEmpty()) {
            Text(
                text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = if (ok) GoldBright else Danger
            )
        }
    }
}
