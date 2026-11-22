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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.WatchLaterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "À voir plus tard" — the active profile's watch-later list (2026-11-22). */
@Composable
fun WatchLaterScreen(
    navController: NavHostController,
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.watch_later_title),
            fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        if (items.isEmpty()) {
            Text(text = stringResource(R.string.watch_later_empty), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .tvFocus()
                            .clickable {
                                when (item.kind) {
                                    ContentKind.FILM -> navController.navigate(Routes.detail("film", item.refId))
                                    ContentKind.SERIES -> navController.navigate(Routes.detail("series", item.refId))
                                    else -> Unit
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                            Text(
                                text = dateFormat.format(Date(item.addedMillis)),
                                fontSize = 15.sp, color = TextMuted
                            )
                        }
                        if (confirmDeleteId == "${item.kind}:${item.refId}") {
                            GoldButton(text = stringResource(R.string.confirm_delete)) {
                                viewModel.remove(item)
                                confirmDeleteId = null
                            }
                        } else {
                            GhostButton(text = stringResource(R.string.delete)) { confirmDeleteId = "${item.kind}:${item.refId}" }
                        }
                    }
                }
            }
        }
    }
}
