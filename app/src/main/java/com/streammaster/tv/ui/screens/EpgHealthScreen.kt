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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.EpgChannelHealth
import com.streammaster.tv.ui.viewmodel.EpgHealthReason
import com.streammaster.tv.ui.viewmodel.EpgHealthViewModel

/** Route for the EPG health screen (§4.41). Registered by the nav builder. */
const val EPG_HEALTH_ROUTE = "epg_health"

private enum class EpgFilter { ALL, WITH, WITHOUT }

/**
 * EPG health screen (§4.41): channels with/without a guide, probable cause
 * of the gap and the fix to try. The "add XMLTV" action opens Profil,
 * where the XMLTV source is configured on the playlist.
 */
@Composable
fun EpgHealthScreen(
    navController: NavHostController,
    viewModel: EpgHealthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var filter by remember { mutableStateOf(EpgFilter.ALL) }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeader(stringResource(R.string.ns_epg_health_title))
        if (state.loading) {
            LoadingIndicator(stringResource(R.string.ns_epg_loading))
            return@Column
        }
        CardSurface(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${stringResource(R.string.ns_epg_with_guide)} : ${state.withGuide}",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldBright
                )
                Text(
                    text = "${stringResource(R.string.ns_epg_without_guide)} : ${state.withoutGuide}",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = if (state.withoutGuide == 0) GoldBright else Danger
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SortPill(stringResource(R.string.ns_epg_filter_all), filter == EpgFilter.ALL) { filter = EpgFilter.ALL }
            SortPill(stringResource(R.string.ns_epg_filter_with), filter == EpgFilter.WITH) { filter = EpgFilter.WITH }
            SortPill(stringResource(R.string.ns_epg_filter_without), filter == EpgFilter.WITHOUT) { filter = EpgFilter.WITHOUT }
        }
        val shown = state.channels.filter {
            when (filter) {
                EpgFilter.ALL -> true
                EpgFilter.WITH -> it.hasGuide
                EpgFilter.WITHOUT -> !it.hasGuide
            }
        }
        if (shown.isEmpty()) {
            Text(text = stringResource(R.string.ns_epg_empty), fontSize = 18.sp, color = TextMuted)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shown) { row ->
                EpgHealthRow(row) { navController.navigate(Routes.PLAYLISTS) }
            }
        }
    }
}

@Composable
private fun EpgHealthRow(row: EpgChannelHealth, onAddXmltv: () -> Unit) {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = row.channelName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                Text(
                    text = if (row.hasGuide) stringResource(R.string.ns_epg_with_guide)
                    else stringResource(R.string.ns_epg_without_guide),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (row.hasGuide) GoldBright else Danger
                )
            }
            if (!row.hasGuide) {
                val (reasonRes, fixRes) = when (row.reason) {
                    EpgHealthReason.NO_EPG_ID -> R.string.ns_epg_reason_no_id to R.string.ns_epg_fix_id
                    EpgHealthReason.PROVIDER_NO_EPG -> R.string.ns_epg_reason_provider to R.string.ns_epg_fix_xmltv
                    EpgHealthReason.XMLTV_DOWN_OR_MISMATCH -> R.string.ns_epg_reason_xmltv to R.string.ns_epg_fix_xmltv
                    EpgHealthReason.PROVIDER_NO_GUIDE -> R.string.ns_epg_reason_unknown to R.string.ns_epg_fix_retry
                    EpgHealthReason.HAS_GUIDE -> R.string.ns_epg_reason_unknown to R.string.ns_epg_fix_retry
                }
                Text(text = stringResource(reasonRes), fontSize = 17.sp, color = TextMuted)
                Text(text = stringResource(fixRes), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GoldBright)
                if (row.reason == EpgHealthReason.PROVIDER_NO_EPG ||
                    row.reason == EpgHealthReason.XMLTV_DOWN_OR_MISMATCH
                ) {
                    GoldButton(text = stringResource(R.string.ns_epg_fix_xmltv)) { onAddXmltv() }
                }
            }
        }
    }
}
