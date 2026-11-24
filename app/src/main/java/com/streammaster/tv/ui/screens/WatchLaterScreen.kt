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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import kotlinx.coroutines.delay
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
    // Initial focus: the first watch-later row. The row only exists once
    // the data has loaded — a LaunchedEffect(Unit) fires before that and
    // the request is lost — so the request is re-tried (once) when the
    // list arrives.
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    // §4.1 — return to the same item: PlayerScreen writes the played id
    // into this entry's savedStateHandle ("focusedId"); items are keyed
    // "kind:refId" here, so either form matches. (Items open the detail
    // page, so a return via the player only restores when the detail
    // page itself wrote the id back — otherwise the first row is used.)
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val restoreId = savedStateHandle?.get<String>("focusedId")
    val hasRestoreTarget = restoreId != null &&
        items.any { it.refId == restoreId || "${it.kind}:${it.refId}" == restoreId }
    val itemFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun focusFor(key: String) = itemFocusRequesters.getOrPut(key) { FocusRequester() }
    val listState = rememberLazyListState()
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty() && !initialFocusDone && !hasRestoreTarget) {
            firstFocus.requestFocus()
            initialFocusDone = true
        }
    }
    LaunchedEffect(items, restoreId) {
        if (focusRestored || initialFocusDone) return@LaunchedEffect
        val target = restoreId?.takeIf { id ->
            items.any { it.refId == id || "${it.kind}:${it.refId}" == id }
        } ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.refId == target || "${it.kind}:${it.refId}" == target }
        if (index > 0) {
            listState.scrollToItem(index)
            delay(100) // let the row compose after the scroll before focusing it
        }
        val key = items[index].let { "${it.kind}:${it.refId}" }
        if (index == 0) firstFocus.requestFocus() else focusFor(key).requestFocus()
        focusRestored = true
        initialFocusDone = true
        savedStateHandle?.remove<String>("focusedId")
    }
    // Focus (2026-11-23, m-10): the confirm button replaces the focused
    // "Supprimer" button, and a delete removes the focused row — both
    // dispose the focused node. The confirm button is re-focused when it
    // appears, and focus moves to a live neighbour BEFORE the row is
    // removed; focusRestorer() on the list is the safety net.
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(confirmDeleteId) {
        if (confirmDeleteId != null) {
            delay(50)
            confirmFocus.requestFocus()
        }
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.watch_later_title),
            fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        if (items.isEmpty()) {
            Text(text = stringResource(R.string.watch_later_empty), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(state = listState, modifier = Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(items) { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .focusRequester(focusFor("${item.kind}:${item.refId}"))
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
                            GoldButton(
                                text = stringResource(R.string.confirm_delete),
                                modifier = Modifier.focusRequester(confirmFocus)
                            ) {
                                val keys = items.map { "${it.kind}:${it.refId}" }
                                val idx = keys.indexOf("${item.kind}:${item.refId}")
                                val next = keys.getOrNull(idx + 1) ?: keys.getOrNull(idx - 1)
                                if (next != null) focusFor(next).requestFocus()
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
