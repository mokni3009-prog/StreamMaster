package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.streammaster.tv.data.matchday.DayMatch
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.CardSurfaceMuted
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.MatchDayTab
import com.streammaster.tv.ui.viewmodel.MatchDayViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Route for the Match Day screen (§4.35). The link builder wires this
 *  into the sidebar navigation; the screen itself is standalone. */
const val MATCH_DAY_ROUTE = "match_day"

/**
 * Match Day (§4.35) — today's (and tomorrow's) key matches, grouped by
 * league, with the gold identity of the app. Each card shows the
 * broadcast channels FIRST, the two teams with their logos, kickoff
 * (Tunis time), the stadium when known, a live countdown to kickoff,
 * a reminder bell, and a "Watch" button that opens the broadcast
 * channel in the player. Cancelled matches stay visible, marked
 * "ألغيت", with no watch button.
 */
@Composable
fun MatchDayScreen(
    navController: NavHostController,
    viewModel: MatchDayViewModel = hiltViewModel()
) {
    val feed by viewModel.feed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val nowMillis by viewModel.nowMillis.collectAsState()
    val message by viewModel.message.collectAsState()
    val remindedIds by viewModel.remindedIds.collectAsState()

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    // One-shot messages disappear after 4s.
    LaunchedEffect(message) {
        if (message != null) {
            delay(4_000)
            viewModel.clearMessage()
        }
    }

    val dayFeed = viewModel.dayFeed(tab, feed)
    val updatedText = feed?.updatedAtMillis?.takeIf { it > 0L }?.let { formatTunisHHMM(it) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.match_day_title),
                fontSize = 30.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif, color = GoldWhite
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (updatedText != null) {
                    Text(
                        text = stringResource(R.string.match_day_last_update, updatedText),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMuted
                    )
                }
                GoldButton(text = stringResource(R.string.refresh)) { viewModel.refresh() }
            }
        }

        // Day tabs: today / tomorrow (from the same JSON file).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tab == MatchDayTab.TODAY) {
                GoldButton(
                    text = stringResource(R.string.match_day_today),
                    modifier = Modifier.focusRequester(firstFocus)
                ) { viewModel.selectTab(MatchDayTab.TODAY) }
            } else {
                GhostButton(
                    text = stringResource(R.string.match_day_today),
                    modifier = Modifier.focusRequester(firstFocus)
                ) { viewModel.selectTab(MatchDayTab.TODAY) }
            }
            if (tab == MatchDayTab.TOMORROW) {
                GoldButton(text = stringResource(R.string.match_day_tomorrow)) {
                    viewModel.selectTab(MatchDayTab.TOMORROW)
                }
            } else {
                GhostButton(text = stringResource(R.string.match_day_tomorrow)) {
                    viewModel.selectTab(MatchDayTab.TOMORROW)
                }
            }
        }

        val messageText = when (message) {
            MatchDayViewModel.NEEDS_CHANNEL ->
                stringResource(R.string.match_day_reminder_needs_channel)
            MatchDayViewModel.NO_CHANNEL_WATCH ->
                stringResource(R.string.match_day_no_channel)
            MatchDayViewModel.REMINDER_SET ->
                stringResource(R.string.match_day_reminder_set)
            else -> null
        }
        if (messageText != null) {
            Text(text = messageText, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Gold)
        }

        if (loading && feed == null) {
            Text(
                text = stringResource(R.string.loading),
                fontSize = 18.sp, color = TextMuted, modifier = Modifier.weight(1f)
            )
            return@Column
        }
        if (error && feed == null) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = stringResource(R.string.match_day_error), fontSize = 18.sp, color = TextMuted)
                GoldButton(text = stringResource(R.string.refresh)) { viewModel.refresh() }
            }
            return@Column
        }
        val leagues = dayFeed?.leagues.orEmpty()
        if (leagues.isEmpty()) {
            Text(
                text = stringResource(R.string.match_day_no_matches),
                fontSize = 18.sp, color = TextMuted, modifier = Modifier.weight(1f)
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            leagues.forEach { league ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!league.leagueLogo.isNullOrBlank()) {
                            AsyncImage(
                                model = league.leagueLogo, contentDescription = league.league,
                                contentScale = ContentScale.Fit, modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = league.league,
                            fontSize = 20.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif, color = Gold
                        )
                    }
                }
                items(league.matches) { match ->
                    MatchDayCard(
                        match = match,
                        nowMillis = nowMillis,
                        channel = viewModel.channelFor(match),
                        reminded = match.id in remindedIds,
                        onWatch = { channel ->
                            if (channel != null) {
                                navController.navigate(Routes.player("live", channel.id))
                            } else {
                                viewModel.showNoChannelMessage()
                            }
                        },
                        onRemind = { viewModel.scheduleReminder(match) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchDayCard(
    match: DayMatch,
    nowMillis: Long,
    channel: Channel?,
    reminded: Boolean,
    onWatch: (Channel?) -> Unit,
    onRemind: () -> Unit
) {
    val cancelled = match.status == "cancelled"
    val live = match.status == "live"
    val finished = match.status == "finished"
    val started = match.kickoffMillis > 0L && nowMillis >= match.kickoffMillis

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Broadcast channels FIRST (§4.35: top priority).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (match.channels.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.match_day_channels, match.channels.joinToString(" • ")
                    ),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Keep the row height stable when no channels are listed.
                Text(text = "", fontSize = 16.sp, modifier = Modifier.weight(1f))
            }
            when {
                cancelled -> StatusBadge(
                    text = stringResource(R.string.match_day_cancelled),
                    bg = Danger, fg = GoldWhite
                )
                live -> StatusBadge(
                    text = stringResource(R.string.match_day_live), bg = Gold, fg = GoldDeep
                )
                finished -> StatusBadge(
                    text = stringResource(R.string.match_day_finished),
                    bg = CardSurfaceMuted, fg = TextMuted
                )
            }
        }

        // Teams + kickoff / countdown.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TeamLogo(url = match.homeLogo, name = match.homeTeam)
            Text(
                text = match.homeTeam, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = GoldWhite, modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = match.kickoffText, fontSize = 20.sp,
                    fontWeight = FontWeight.Black, color = GoldWhite
                )
                val countdown = when {
                    cancelled -> stringResource(R.string.match_day_cancelled)
                    live -> stringResource(R.string.match_day_live)
                    finished -> stringResource(R.string.match_day_finished)
                    match.kickoffMillis <= 0L -> ""
                    started -> stringResource(R.string.match_day_started)
                    else -> formatCountdown(match.kickoffMillis - nowMillis)
                }
                if (countdown.isNotEmpty()) {
                    Text(
                        text = countdown, fontSize = 17.sp,
                        fontWeight = FontWeight.Bold, color = Gold
                    )
                }
            }
            Text(
                text = match.awayTeam, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = GoldWhite, modifier = Modifier.weight(1f)
            )
            TeamLogo(url = match.awayLogo, name = match.awayTeam)
        }

        if (!match.stadium.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.match_day_stadium, match.stadium),
                fontSize = 15.sp, color = TextMuted
            )
        }

        // Actions: watch (never for cancelled) + reminder bell.
        if (!cancelled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (channel != null) {
                    GoldButton(text = stringResource(R.string.match_day_watch)) {
                        onWatch(channel)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.match_day_no_channel),
                        fontSize = 15.sp, color = TextMuted,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                if (match.status == "scheduled" && !started) {
                    GhostButton(
                        text = if (reminded) stringResource(R.string.match_day_reminded)
                        else stringResource(R.string.match_day_remind)
                    ) { if (!reminded) onRemind() }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Text(
        text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Team logo (Coil); an empty bordered box when the source has no logo,
 *  so the card never breaks (§4.35). */
@Composable
private fun TeamLogo(url: String?, name: String) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url, contentDescription = name,
            contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp)
        )
    } else {
        Box(
            modifier = Modifier.size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardSurfaceMuted)
                .border(1.dp, BorderGold, RoundedCornerShape(8.dp))
        )
    }
}

/** Remaining time as "HH:MM:SS" (hours can exceed 24 for tomorrow). */
private fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** "HH:MM" in Tunis time, for the "last update" line. */
private fun formatTunisHHMM(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Africa/Tunis")
    }.format(millis)
