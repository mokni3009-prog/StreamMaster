package com.streammaster.tv.ui.screens

import androidx.compose.foundation.clickable
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
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.TonightPick
import com.streammaster.tv.ui.viewmodel.WatchTonightViewModel

/** Route for the "Quoi regarder ce soir ?" screen (§4.36). Registered by the nav builder. */
const val WATCH_TONIGHT_ROUTE = "watch_tonight"

/**
 * "Quoi regarder ce soir ?" (§4.36): asks how much time the user has,
 * offers 3–4 picks that fit. Tapping a pick plays it (live) or opens its
 * detail page (film / series).
 */
@Composable
fun WatchTonightScreen(
    navController: NavHostController,
    viewModel: WatchTonightViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Focus (2026-11-23, ص-05): initial focus on the first time pill.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SectionHeader(stringResource(R.string.ns_tonight_title))
        Text(
            text = stringResource(R.string.ns_tonight_time),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(30, 60, 90, 120, 180).forEach { minutes ->
                SortPill(
                    stringResource(R.string.ns_tonight_minutes, minutes),
                    state.availableMinutes == minutes,
                    modifier = if (minutes == 30) Modifier.focusRequester(firstFocus) else Modifier
                ) { viewModel.setAvailableMinutes(minutes) }
            }
        }
        if (!state.loading) {
            if (state.picks.isEmpty()) {
                Text(text = stringResource(R.string.ns_tonight_empty), fontSize = 18.sp, color = TextMuted)
            } else {
                SectionHeader(stringResource(R.string.ns_tonight_picks, state.picks.size))
                state.picks.forEach { pick ->
                    TonightPickRow(pick) {
                        when (pick.kind) {
                            ContentKind.LIVE -> navController.navigate(Routes.player("live", pick.id))
                            ContentKind.FILM -> navController.navigate(Routes.detail("film", pick.id))
                            ContentKind.SERIES -> navController.navigate(Routes.detail("series", pick.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TonightPickRow(pick: TonightPick, onClick: () -> Unit) {
    // Focus (2026-11-23, ص-20): the whole row is focusable and clickable,
    // not just the small action button on the right.
    CardSurface(
        modifier = Modifier.fillMaxWidth().tvFocus().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = pick.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                Text(text = pick.subtitle, fontSize = 18.sp, color = TextMuted)
            }
            GhostButton(
                text = if (pick.kind == ContentKind.LIVE) stringResource(R.string.ns_tonight_live)
                else if (pick.kind == ContentKind.SERIES) stringResource(R.string.ns_tonight_episode)
                else "${pick.durationMin} min"
            ) { onClick() }
        }
    }
}
