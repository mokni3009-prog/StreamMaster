package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.HistoriqueViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Historique — watch history (latest 20) + clear-all (direct, no confirmation). */
@Composable
fun HistoriqueScreen(
    navController: NavHostController,
    viewModel: HistoriqueViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    // Initial focus: the first history row (the "clear all" button above
    // it is deliberately NOT the initial focus — it is destructive). The
    // row only exists once the data has loaded — a LaunchedEffect(Unit)
    // fires before that and the request is lost — so the request is
    // re-tried (once) when the history arrives.
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(history.isNotEmpty()) {
        if (history.isNotEmpty() && !initialFocusDone) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Section head with the count badge + the always-visible "Tout effacer"
        // button (prototype `.sec-head`).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                stringResource(R.string.historique_title),
                count = history.size,
                modifier = Modifier.weight(1f)
            )
            GhostButton(text = stringResource(R.string.clear_all)) {
                // Prototype `clearHistory()`: clears immediately, no confirmation.
                viewModel.clearHistory()
            }
        }
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.historique_empty),
                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = GoldSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(40.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(history.take(20)) { index, entry ->
                    // Prototype `.hist-row`: min 64dp, radius 17, black 45%,
                    // 2px border, 46dp gold kind icon, 20sp title, 15sp sub, 16sp time.
                    // The sub line shows the kind only: HistoryEntry carries no
                    // category/genre/current-programme to show (prototype
                    // `histSub` needs the full item, which the entry does not keep).
                    val (kindIcon, kindLabel) = when (entry.kind) {
                        ContentKind.LIVE -> R.drawable.ic_tv to R.string.hist_kind_channel
                        ContentKind.FILM -> R.drawable.ic_film to R.string.hist_kind_film
                        ContentKind.SERIES -> R.drawable.ic_layers to R.string.hist_kind_series
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .defaultMinSize(minHeight = 64.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(Color(0x73000000))
                            .border(2.dp, BorderGold.copy(alpha = 0.24f), RoundedCornerShape(17.dp))
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .tvFocus()
                            .clickable {
                                when (entry.kind) {
                                    ContentKind.LIVE -> navController.navigate(Routes.player("live", entry.refId))
                                    ContentKind.FILM -> navController.navigate(Routes.detail("film", entry.refId))
                                    ContentKind.SERIES -> navController.navigate(Routes.detail("series", entry.refId))
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(GoldGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(kindIcon), contentDescription = null,
                                tint = GoldDeep, modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title, fontSize = 20.sp, fontWeight = FontWeight.Black,
                                color = GoldWhite, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(kindLabel),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldBright,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = dateFormat.format(Date(entry.timestampMillis)),
                            fontSize = 16.sp, fontWeight = FontWeight.Black, color = GoldSoft
                        )
                    }
                }
            }
        }
    }
}
