package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.streammaster.tv.R
import com.streammaster.tv.data.matchcenter.LiveMatch
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.MatchCenterViewModel

/** Match Center — live football scores (ESPN scoreboard, 2026-11-22). */
@Composable
fun MatchCenterScreen(
    navController: NavHostController,
    viewModel: MatchCenterViewModel = hiltViewModel()
) {
    val matches by viewModel.matches.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val goalAlerts by viewModel.goalAlerts.collectAsState()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.match_center_title),
                fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Goal alerts (2026-11-22): opt-in toggle, on/off any time.
                GhostButton(
                    text = if (goalAlerts) "✓ " + stringResource(R.string.goal_alerts)
                    else stringResource(R.string.goal_alerts)
                ) { viewModel.toggleGoalAlerts() }
                GoldButton(text = stringResource(R.string.refresh)) { viewModel.refresh() }
            }
        }
        MatchCenterList(matches = matches, loading = loading, modifier = Modifier.weight(1f))
    }
}

/** Shared match list: sidebar screen and the in-player Scores dialog. */
@Composable
fun MatchCenterList(
    matches: List<LiveMatch>,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    if (loading && matches.isEmpty()) {
        Text(text = stringResource(R.string.loading), fontSize = 18.sp, color = TextMuted, modifier = modifier)
        return
    }
    if (matches.isEmpty()) {
        Text(text = stringResource(R.string.matches_none), fontSize = 18.sp, color = TextMuted, modifier = modifier)
        return
    }
    val byLeague = matches.groupBy { it.leagueName.ifBlank { "Football" } }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        byLeague.forEach { (league, leagueMatches) ->
            item {
                Text(
                    text = league,
                    fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = Gold
                )
            }
            items(leagueMatches) { match ->
                MatchRow(match)
            }
        }
    }
}

@Composable
private fun MatchRow(match: LiveMatch) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Home team
        if (!match.homeLogo.isNullOrBlank()) {
            AsyncImage(
                model = match.homeLogo, contentDescription = match.homeTeam,
                contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = match.homeTeam, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = GoldWhite, modifier = Modifier.weight(1f)
        )
        // Score / status
        val scoreText = if (match.homeScore != null && match.awayScore != null) {
            "${match.homeScore} - ${match.awayScore}"
        } else "vs"
        Text(
            text = scoreText, fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite
        )
        // Away team
        Text(
            text = match.awayTeam, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = GoldWhite, modifier = Modifier.weight(1f)
        )
        if (!match.awayLogo.isNullOrBlank()) {
            AsyncImage(
                model = match.awayLogo, contentDescription = match.awayTeam,
                contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp)
            )
        }
        // Live minute / kickoff time / FT
        val statusColor = if (match.state == "in") GoldDeep else TextMuted
        val statusBg = if (match.state == "in") Gold else CardSurface
        Text(
            text = match.statusText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = statusColor,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(statusBg).padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
