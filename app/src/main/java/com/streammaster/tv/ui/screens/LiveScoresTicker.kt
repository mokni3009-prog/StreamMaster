package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R
import com.streammaster.tv.data.matchcenter.LiveMatch
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite

/**
 * Live-scores ticker (§4.55): one thin permanent strip at the bottom of
 * the player showing today's matches currently in play
 * ("HOME 2 - 1 AWAY • 67'"). It never takes focus and never navigates
 * away from the player. It renders nothing at all when no match is in
 * play, so the player keeps a clean frame outside match hours.
 */
@Composable
fun LiveScoresTicker(
    matches: List<LiveMatch>,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(Color(0x29FFE89E))
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp)
                .background(Color(0xE6140E04))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = stringResource(R.string.ticker_live_label),
                fontSize = 16.sp, fontWeight = FontWeight.Black, color = Gold,
                maxLines = 1
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(matches, key = { it.id }) { match ->
                    Text(
                        text = "${match.homeTeam} ${match.homeScore ?: "–"} - " +
                            "${match.awayScore ?: "–"} ${match.awayTeam} • ${match.statusText}",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
