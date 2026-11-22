package com.streammaster.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.ui.components.NumberPadDialog
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.ManageKind
import com.streammaster.tv.ui.viewmodel.ManageViewModel
import com.streammaster.tv.ui.components.GoldButton

/**
 * Manage (Gérer la playlist) — 3 columns: bouquets | items | actions.
 * Remote-only. Multi-select + bulk hide/show/move; rename/reorder single-item;
 * number assignment 1–100 unique with gold N° badge; Save persists the draft.
 */
@Composable
fun ManageScreen(
    navController: NavHostController,
    viewModel: ManageViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to kind ("bouquet"/"channel")
    var renameValue by remember { mutableStateOf("") }
    var numberTarget by remember { mutableStateOf<String?>(null) }
    var newBouquetName by remember { mutableStateOf("") }
    var moveTargetBouquet by remember { mutableStateOf<String?>(null) }
    var keyboardTarget by remember { mutableStateOf<String?>(null) } // "newBouquet" | "rename"
    var keyboardLang by remember { mutableStateOf(com.streammaster.tv.ui.components.KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }

    LaunchedEffect(draft.saved) {
        if (draft.saved) {
            Toast.makeText(context, R.string.manage_saved, Toast.LENGTH_SHORT).show()
            viewModel.clearSavedFlag()
        }
    }

    val kindBouquets = draft.bouquets.filter {
        when (draft.kind) {
            ManageKind.LIVE -> it.kind == BouquetKind.LIVE
            ManageKind.FILMS -> it.kind == BouquetKind.FILMS
            ManageKind.SERIES -> it.kind == BouquetKind.SERIES
        }
    }
    val items: List<Triple<String, String, Boolean>> = when (draft.kind) {
        ManageKind.LIVE -> draft.channels.filter { it.bouquetId == draft.selectedBouquetId }
            .map { Triple(it.id, it.customName ?: it.name, it.hidden) }
        ManageKind.FILMS -> draft.films.map { Triple(it.id, it.title, it.hidden) }
        ManageKind.SERIES -> draft.series.map { Triple(it.id, it.title, it.hidden) }
    }
    val selectedIds = draft.selectedItemIds

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.manage_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
            GoldButton(text = stringResource(R.string.save_changes)) { viewModel.save() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SortPill(stringResource(R.string.nav_live), draft.kind == ManageKind.LIVE) { viewModel.setKind(ManageKind.LIVE) }
            SortPill(stringResource(R.string.stats_films), draft.kind == ManageKind.FILMS) { viewModel.setKind(ManageKind.FILMS) }
            SortPill(stringResource(R.string.stats_series), draft.kind == ManageKind.SERIES) { viewModel.setKind(ManageKind.SERIES) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(text = stringResource(R.string.select_all)) { viewModel.selectAll(items.map { it.first }) }
            GhostButton(text = stringResource(R.string.deselect_all)) { viewModel.deselectAll() }
            if (selectedIds.isNotEmpty()) {
                GhostButton(text = "${stringResource(R.string.hide)} (${selectedIds.size})") { viewModel.setHidden(selectedIds, true) }
                GhostButton(text = "${stringResource(R.string.show)} (${selectedIds.size})") { viewModel.setHidden(selectedIds, false) }
            }
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Column 1 — bouquets
            Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                Text(text = stringResource(R.string.bouquets), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite, modifier = Modifier.padding(bottom = 10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(kindBouquets) { bouquet ->
                        val selected = bouquet.id == draft.selectedBouquetId
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) Gold else CardSurface)
                                .tvFocus()
                                .clickable { viewModel.selectBouquet(bouquet.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = bouquet.name, fontSize = 17.sp, color = if (selected) GoldDeep else GoldWhite, modifier = Modifier.weight(1f))
                            if (bouquet.hidden) Text(text = "◐", color = if (selected) GoldDeep else TextMuted)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    GhostButton(text = stringResource(R.string.add_bouquet)) {
                        if (newBouquetName.isNotBlank()) { viewModel.addBouquet(newBouquetName); newBouquetName = "" }
                    }
                }
                ReadOnlyField(stringResource(R.string.new_bouquet_name), newBouquetName) {
                    keyboardTarget = "newBouquet"
                }
            }
            // Column 2 — items
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(text = stringResource(R.string.channels), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite, modifier = Modifier.padding(bottom = 10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items) { (id, name, hidden) ->
                        val selected = id in selectedIds
                        val channel = draft.channels.firstOrNull { it.id == id }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) Gold else CardSurface)
                                .tvFocus()
                                .clickable { viewModel.toggleSelect(id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = if (selected) "☑" else "☐", fontSize = 18.sp, color = Gold, modifier = Modifier.padding(end = 10.dp))
                            Text(text = name, fontSize = 17.sp, color = if (hidden) TextMuted else GoldWhite, modifier = Modifier.weight(1f))
                            if (channel?.customNum != null) {
                                Text(text = "N° ${channel.customNum}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDeep, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            if (hidden) Text(text = stringResource(R.string.hide), fontSize = 13.sp, color = TextMuted)
                        }
                    }
                }
            }
            // Column 3 — actions (apply to the focused/selected item)
            Column(modifier = Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(R.string.nav_manage), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite, modifier = Modifier.padding(bottom = 4.dp))
                val focusId = selectedIds.firstOrNull()
                GhostButton(text = stringResource(R.string.hide), modifier = Modifier.fillMaxWidth()) {
                    focusId?.let { viewModel.setHiddenSingle(it, true) }
                }
                GhostButton(text = stringResource(R.string.show), modifier = Modifier.fillMaxWidth()) {
                    focusId?.let { viewModel.setHiddenSingle(it, false) }
                }
                GhostButton(text = stringResource(R.string.reorder_up), modifier = Modifier.fillMaxWidth()) {
                    focusId?.let {
                        if (draft.kind == ManageKind.LIVE) viewModel.reorderChannel(it, -1)
                        else viewModel.reorderBouquet(it, -1)
                    }
                }
                GhostButton(text = stringResource(R.string.reorder_down), modifier = Modifier.fillMaxWidth()) {
                    focusId?.let {
                        if (draft.kind == ManageKind.LIVE) viewModel.reorderChannel(it, 1)
                        else viewModel.reorderBouquet(it, 1)
                    }
                }
                GhostButton(text = stringResource(R.string.move_to), modifier = Modifier.fillMaxWidth()) {
                    // Opens the bouquet picker: the first bouquet in the list is
                    // the target; a full picker dialog is shown via moveTargetBouquet.
                    moveTargetBouquet = focusId
                }
                GhostButton(text = stringResource(R.string.rename), modifier = Modifier.fillMaxWidth()) {
                    focusId?.let {
                        renameTarget = it to if (draft.kind == ManageKind.LIVE) "channel" else "bouquet"
                        renameValue = items.firstOrNull { item -> item.first == it }?.second ?: ""
                    }
                }
                if (draft.kind == ManageKind.LIVE) {
                    GhostButton(text = stringResource(R.string.assign_number), modifier = Modifier.fillMaxWidth()) {
                        focusId?.let { numberTarget = it }
                    }
                }
            }
        }
    }

    // Rename dialog (inline editor; text entry via the built-in keyboard below).
    renameTarget?.let { (id, kind) ->
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardSurface).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = stringResource(if (kind == "channel") R.string.rename_channel else R.string.rename_bouquet), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                ReadOnlyField(stringResource(R.string.rename), renameValue) { keyboardTarget = "rename" }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.save)) {
                        if (kind == "channel") viewModel.renameChannel(id, renameValue) else viewModel.renameBouquet(id, renameValue)
                        renameTarget = null
                    }
                    GhostButton(text = stringResource(R.string.cancel)) { renameTarget = null }
                }
            }
        }
    }

    // Move-to-bouquet picker
    moveTargetBouquet?.let { focusId ->
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(CardSurface).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = stringResource(R.string.move_to), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                kindBouquets.forEach { bouquet ->
                    GhostButton(text = bouquet.name, modifier = Modifier.fillMaxWidth()) {
                        if (selectedIds.size > 1) viewModel.moveSelectedToBouquet(bouquet.id)
                        else viewModel.moveSingleToBouquet(focusId, bouquet.id)
                        moveTargetBouquet = null
                    }
                }
                GhostButton(text = stringResource(R.string.cancel)) { moveTargetBouquet = null }
            }
        }
    }

    // Number assignment (components agent NumberPadDialog)
    numberTarget?.let { channelId ->
        val channel = draft.channels.firstOrNull { it.id == channelId }
        NumberPadDialog(
            currentNumber = channel?.customNum,
            onConfirm = { number ->
                val conflict = viewModel.assignNumber(channelId, number)
                if (conflict != null) {
                    Toast.makeText(context, context.getString(R.string.number_duplicate, number ?: 0, conflict), Toast.LENGTH_LONG).show()
                } else if (number != null) {
                    Toast.makeText(context, context.getString(R.string.number_assigned, number, channel?.customName ?: channel?.name ?: ""), Toast.LENGTH_SHORT).show()
                }
                numberTarget = null
            },
            onCancel = { numberTarget = null }
        )
    }
    // Built-in keyboard sheet (components agent) for rename / new bouquet.
    keyboardTarget?.let { target ->
        val current = if (target == "rename") renameValue else newBouquetName
        val setValue: (String) -> Unit = if (target == "rename") { { renameValue = it } } else { { newBouquetName = it } }
        Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = current, color = GoldWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                com.streammaster.tv.ui.components.BuiltInKeyboard(
                    onKey = { c -> setValue(current + c) },
                    onAction = { action ->
                        when (action) {
                            is com.streammaster.tv.ui.components.KeyboardAction.Char -> Unit // already handled via onKey
                            com.streammaster.tv.ui.components.KeyboardAction.Backspace -> setValue(current.dropLast(1))
                            com.streammaster.tv.ui.components.KeyboardAction.Space -> setValue(current + " ")
                            com.streammaster.tv.ui.components.KeyboardAction.Shift -> { }
                            com.streammaster.tv.ui.components.KeyboardAction.SwitchLanguage -> keyboardLang =
                                if (keyboardLang == com.streammaster.tv.ui.components.KeyboardLanguage.FR) com.streammaster.tv.ui.components.KeyboardLanguage.AR else com.streammaster.tv.ui.components.KeyboardLanguage.FR
                            com.streammaster.tv.ui.components.KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                            com.streammaster.tv.ui.components.KeyboardAction.Validate -> keyboardTarget = null
                            com.streammaster.tv.ui.components.KeyboardAction.Close -> keyboardTarget = null
                        }
                    },
                    language = keyboardLang,
                    mode = if (keyboard123) com.streammaster.tv.ui.components.KeyboardMode.SYMBOLS else com.streammaster.tv.ui.components.KeyboardMode.LETTERS
                )
            }
        }
    }
}
