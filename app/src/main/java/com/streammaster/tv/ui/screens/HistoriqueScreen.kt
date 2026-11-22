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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.HistoriqueViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.streammaster.tv.ui.components.GoldButton

/** Historique — watch history (latest 20) + clear-all. */
@Composable
fun HistoriqueScreen(
    navController: NavHostController,
    viewModel: HistoriqueViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.historique_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
            if (history.isNotEmpty()) {
                GhostButton(text = stringResource(R.string.clear_all)) { confirmClear = true }
            }
        }
        if (confirmClear) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.confirm_clear_history), fontSize = 18.sp, color = GoldWhite, modifier = Modifier.padding(end = 8.dp))
                GoldButton(text = stringResource(R.string.delete)) { viewModel.clearHistory(); confirmClear = false }
                GhostButton(text = stringResource(R.string.cancel)) { confirmClear = false }
            }
        }
        if (history.isEmpty()) {
            Text(text = stringResource(R.string.historique_empty), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history.take(20)) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .tvFocus()
                            .clickable {
                                when (entry.kind) {
                                    ContentKind.LIVE -> navController.navigate(Routes.player("live", entry.refId))
                                    ContentKind.FILM -> navController.navigate(Routes.detail("film", entry.refId))
                                    ContentKind.SERIES -> navController.navigate(Routes.detail("series", entry.refId))
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = entry.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                        Text(text = dateFormat.format(Date(entry.timestampMillis)), fontSize = 15.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}
