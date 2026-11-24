package com.streammaster.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.BouquetKind
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.components.NumberPadDialog
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.ManageKind
import com.streammaster.tv.ui.viewmodel.ManageViewModel
import com.streammaster.tv.ui.viewmodel.MyPlaylistViewModel
import com.streammaster.tv.ui.viewmodel.SettingsViewModel
import com.streammaster.tv.ui.components.GoldButton
import kotlinx.coroutines.delay

/**
 * Manage (Gérer la playlist) — M01 mode tabs (56dp, radius 28, 24dp icon
 * + count, gold active), M02 workspace columns as cards (gradient, 2px
 * border, radius 24, 18dp padding, header with count), M03 columns
 * 470dp / 1fr / 430dp, M04 item rows (min 64dp, 38dp order, 46dp icon,
 * 20sp title, 15sp subtitle, badges), M05 bulk-select row kept,
 * M06 actions column (60dp rows, 25dp icons, gold primary, dashed
 * target box, note), M07 bottom save bar (status + hint + discard +
 * 60dp save + dirty badge; save moved out of the header),
 * M08 remote hint, M10 "Nouveau bouquet" as an action, M11 rename
 * inline + keyboard, NumberPadDialog kept.
 */
@Composable
fun ManageScreen(
    navController: NavHostController,
    viewModel: ManageViewModel = hiltViewModel(),
    myPlaylistViewModel: MyPlaylistViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()
    val isDirty by viewModel.dirty.collectAsState()
    // §4.62 — My Playlist (add the selected item(s) to one of its bouquets).
    val myLiveBouquets by myPlaylistViewModel.liveBouquets.collectAsState()
    val myFilmBouquets by myPlaylistViewModel.filmBouquets.collectAsState()
    val mySeriesBouquets by myPlaylistViewModel.seriesBouquets.collectAsState()
    val activePlaylist by myPlaylistViewModel.activePlaylist.collectAsState()
    // Locks (for the "Verrouillé" badge, M04) come from Settings.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val lockedItemIds = settings.lockedItems.toSet()
    val lockedBouquetIds = settings.lockedBouquets.values.flatten().toSet()
    // Initial focus: the first tab (Chaînes TV).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    val context = LocalContext.current
    // Narrow canvas (720p xhdpi = 640dp wide, like Shell.kt:601): the
    // actions column scrolls, the save bar shortens, and rows drop the
    // order number / checkbox and shrink the icon so titles stay readable.
    val narrow = LocalConfiguration.current.screenWidthDp < 700
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to kind ("bouquet"/"channel")
    var renameValue by remember { mutableStateOf("") }
    var numberTarget by remember { mutableStateOf<String?>(null) }
    var newBouquetName by remember { mutableStateOf("") }
    var newBouquetDialog by remember { mutableStateOf(false) }
    // §4.62 — add-to-My-Playlist dialog + new My Playlist bouquet name.
    var addToMyDialog by remember { mutableStateOf(false) }
    var newMyBouquetName by remember { mutableStateOf("") }
    var moveTargetBouquet by remember { mutableStateOf<String?>(null) }
    var keyboardTarget by remember { mutableStateOf<String?>(null) } // "newBouquet" | "rename"
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    // Dialog focus: the inline new-bouquet / rename dialogs are same-window
    // overlays, so focus must move into them on open and back to the first
    // tab on close; the keyboard closes back into the dialog's field.
    val dialogFocus = remember { FocusRequester() }
    // B-M3 — one FocusRequester per action row that opens a dialog, plus
    // the key of the row that opened the current dialog, so closing a
    // dialog returns focus to its opener instead of the first tab.
    // (The rows are in a plain Column, so they are always composed.)
    val actionFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun actionFocus(key: String) = actionFocusRequesters.getOrPut(key) { FocusRequester() }
    var dialogOpenerKey by remember { mutableStateOf<String?>(null) }
    // B-M1 — focus for the first option of the move-to dialog.
    val moveFocus = remember { FocusRequester() }
    LaunchedEffect(newBouquetDialog, renameTarget, addToMyDialog) {
        if (newBouquetDialog || renameTarget != null || addToMyDialog) dialogFocus.requestFocus()
        else {
            dialogOpenerKey?.let { actionFocusRequesters[it]?.requestFocus() } ?: firstFocus.requestFocus()
            dialogOpenerKey = null
        }
    }
    LaunchedEffect(keyboardTarget) {
        if (keyboardTarget == null && (newBouquetDialog || renameTarget != null || addToMyDialog)) dialogFocus.requestFocus()
    }
    // B-M1 — the move-to dialog is a real Dialog (focus is trapped in its
    // window); on open its first option takes focus, on close focus goes
    // back to the "Déplacer" row that opened it.
    LaunchedEffect(moveTargetBouquet) {
        if (moveTargetBouquet != null) {
            moveFocus.requestFocus()
        } else {
            // The dialog window is gone; give the underlying window a beat
            // before returning focus to the opener.
            delay(100)
            dialogOpenerKey?.let { actionFocusRequesters[it]?.requestFocus() } ?: firstFocus.requestFocus()
            dialogOpenerKey = null
        }
    }
    // NumberPadDialog is a real Dialog too: its own dismiss restores
    // focus, but return explicitly to the "Attribuer un numéro" row all
    // the same, so the outcome never depends on window restoration.
    LaunchedEffect(numberTarget) {
        if (numberTarget == null && dialogOpenerKey == "assignNumber") {
            delay(100)
            actionFocusRequesters["assignNumber"]?.requestFocus()
            dialogOpenerKey = null
        }
    }
    // Back closes the keyboard, then the dialog, instead of leaving the screen.
    BackHandler(enabled = keyboardTarget != null || newBouquetDialog || renameTarget != null || addToMyDialog) {
        when {
            keyboardTarget != null -> keyboardTarget = null
            renameTarget != null -> renameTarget = null
            addToMyDialog -> addToMyDialog = false
            newBouquetDialog -> newBouquetDialog = false
        }
    }

    LaunchedEffect(draft.saved) {
        if (draft.saved) {
            showToast(context, context.getString(R.string.manage_saved))
            viewModel.clearSavedFlag()
        }
    }

    // §4.62 — toast for add-to-My-Playlist results.
    val myMessageRes by myPlaylistViewModel.messageRes.collectAsState()
    LaunchedEffect(myMessageRes) {
        myMessageRes?.let {
            showToast(context, context.getString(it))
            myPlaylistViewModel.clearMessage()
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
    val focusId = selectedIds.firstOrNull()
    val focusChannel = draft.channels.firstOrNull { it.id == focusId }
    val focusItem = items.firstOrNull { it.first == focusId }
    // §4.62 — the items the add-to-My-Playlist action targets (the
    // multi-selection when non-empty, else the focused item), their kind,
    // and this tab's My Playlist bouquets. The source subscription is the
    // active one: the draft only ever holds the active playlist's content.
    val addTargetIds = if (selectedIds.isNotEmpty()) selectedIds.toList() else listOfNotNull(focusId)
    val bouquetKind = when (draft.kind) {
        ManageKind.LIVE -> BouquetKind.LIVE
        ManageKind.FILMS -> BouquetKind.FILMS
        ManageKind.SERIES -> BouquetKind.SERIES
    }
    val itemKind = when (draft.kind) {
        ManageKind.LIVE -> ContentKind.LIVE
        ManageKind.FILMS -> ContentKind.FILM
        ManageKind.SERIES -> ContentKind.SERIES
    }
    val myBouquets = when (draft.kind) {
        ManageKind.LIVE -> myLiveBouquets
        ManageKind.FILMS -> myFilmBouquets
        ManageKind.SERIES -> mySeriesBouquets
    }
    val itemsTitleRes = when (draft.kind) {
        ManageKind.LIVE -> R.string.channels
        ManageKind.FILMS -> R.string.stats_films
        ManageKind.SERIES -> R.string.stats_series
    }
    val itemsIconRes = when (draft.kind) {
        ManageKind.LIVE -> R.drawable.ic_tv
        ManageKind.FILMS -> R.drawable.ic_film
        ManageKind.SERIES -> R.drawable.ic_layers
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.manage_title), fontSize = 30.sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
        )
        // M01 — mode tabs with counts.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeTab(
                label = stringResource(R.string.manage_tab_live), iconRes = R.drawable.ic_tv,
                count = draft.channels.size, selected = draft.kind == ManageKind.LIVE,
                modifier = Modifier.focusRequester(firstFocus)
            ) { viewModel.setKind(ManageKind.LIVE) }
            ModeTab(
                label = stringResource(R.string.stats_films), iconRes = R.drawable.ic_film,
                count = draft.films.size, selected = draft.kind == ManageKind.FILMS
            ) { viewModel.setKind(ManageKind.FILMS) }
            ModeTab(
                label = stringResource(R.string.stats_series), iconRes = R.drawable.ic_layers,
                count = draft.series.size, selected = draft.kind == ManageKind.SERIES
            ) { viewModel.setKind(ManageKind.SERIES) }
        }
        // M05 — bulk-select row (kept: a working addition).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(text = stringResource(R.string.select_all)) { viewModel.selectAll(items.map { it.first }) }
            GhostButton(text = stringResource(R.string.deselect_all)) { viewModel.deselectAll() }
            if (selectedIds.isNotEmpty()) {
                GhostButton(text = "${stringResource(R.string.hide)} (${selectedIds.size})") { viewModel.setHidden(selectedIds, true) }
                GhostButton(text = "${stringResource(R.string.show)} (${selectedIds.size})") { viewModel.setHidden(selectedIds, false) }
            }
        }
        // B-S4: exact column weights for the 1472dp reference content
        // width (470:528:430 over 1428dp after the 2×22dp gaps, i.e.
        // 0.8902/1/0.8144) — the old 0.89/1/0.81 gave 470.7/528.9/428.4.
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            // Column 1 — bouquets (M02/M03: 470dp).
            MgCol(
                modifier = Modifier.weight(0.8902f).widthIn(max = 470.dp).fillMaxHeight(),
                iconRes = R.drawable.ic_folder,
                title = stringResource(R.string.bouquets),
                count = kindBouquets.size
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(kindBouquets) { index, bouquet ->
                        val itemCount = when (draft.kind) {
                            ManageKind.LIVE -> draft.channels.count { it.bouquetId == bouquet.id }
                            ManageKind.FILMS -> draft.films.size
                            ManageKind.SERIES -> draft.series.size
                        }
                        ManageRow(
                            ordText = (index + 1).toString(),
                            iconRes = R.drawable.ic_folder,
                            title = bouquet.name,
                            subtitle = stringResource(R.string.mg_elements, itemCount),
                            badges = listOfNotNull(
                                if (bouquet.hidden) stringResource(R.string.mg_hidden) else null,
                                if (bouquet.id in lockedBouquetIds) stringResource(R.string.locked) else null
                            ),
                            selected = bouquet.id == draft.selectedBouquetId
                        ) { viewModel.selectBouquet(bouquet.id) }
                    }
                }
            }
            // Column 2 — items (M02/M03: 1fr).
            MgCol(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                iconRes = itemsIconRes,
                title = stringResource(itemsTitleRes),
                count = items.size
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items) { index, (id, name, hidden) ->
                        val selected = id in selectedIds
                        val channel = draft.channels.firstOrNull { it.id == id }
                        val film = draft.films.firstOrNull { it.id == id }
                        val serie = draft.series.firstOrNull { it.id == id }
                        val ordText = when (draft.kind) {
                            ManageKind.LIVE -> channel?.customNum?.toString()
                                ?: channel?.num?.ifBlank { null } ?: (index + 1).toString()
                            else -> (index + 1).toString()
                        }
                        val subtitle = when (draft.kind) {
                            ManageKind.LIVE -> listOfNotNull(
                                channel?.category?.ifBlank { null },
                                channel?.quality?.ifBlank { null }
                            ).joinToString(" • ")
                            ManageKind.FILMS -> "${film?.year ?: ""} • ${film?.genre ?: ""}"
                            ManageKind.SERIES -> "${stringResource(R.string.seasons_label, serie?.seasons ?: 0)} • ${serie?.genre ?: ""}"
                        }
                        val locked = id in lockedItemIds ||
                            (draft.kind == ManageKind.LIVE && channel?.bouquetId in lockedBouquetIds)
                        ManageRow(
                            ordText = ordText,
                            iconRes = itemsIconRes,
                            title = name,
                            subtitle = subtitle,
                            badges = listOfNotNull(
                                if (channel?.customNum != null) "N° ${channel.customNum}" else null,
                                if (hidden) stringResource(R.string.mg_hidden) else null,
                                if (locked) stringResource(R.string.locked) else null,
                                if (selected) stringResource(R.string.mg_selected) else null
                            ),
                            selected = selected,
                            checked = selected
                        ) { viewModel.toggleSelect(id) }
                    }
                }
            }
            // Column 3 — actions (M02/M03: 430dp, no header: target box first).
            MgCol(
                modifier = Modifier.weight(0.8144f).widthIn(max = 430.dp).fillMaxHeight(),
                iconRes = R.drawable.ic_shield,
                title = stringResource(R.string.mg_target),
                count = selectedIds.size,
                showHeader = false,
                // Actions content ~700dp in LIVE mode: taller than the
                // ~540dp available on the reference (~160dp clipped) and
                // the ~177dp on a narrow canvas, so it scrolls in both
                // cases. In Films/Series mode on the reference it fits
                // (~472dp vs ~540dp) and stays exactly as it was.
                scrollContent = narrow || draft.kind == ManageKind.LIVE
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .dashedBorder(2.dp, BorderGold, 16.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = stringResource(R.string.mg_target), fontSize = 15.sp, color = TextMuted)
                    Text(
                        text = focusItem?.second
                            ?: kindBouquets.firstOrNull { it.id == draft.selectedBouquetId }?.name ?: "—",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                    )
                    Text(
                        text = when {
                            focusItem != null -> "${stringResource(R.string.mg_target_item)} • ${
                                kindBouquets.firstOrNull { it.id == draft.selectedBouquetId }?.name ?: ""
                            }${focusChannel?.customNum?.let { " • N° $it" } ?: ""}${
                                if (selectedIds.size > 1) " • ${stringResource(R.string.mg_sel_count, selectedIds.size)}" else ""
                            }"
                            draft.selectedBouquetId != null -> "${stringResource(R.string.mg_target_bouquet)} • ${
                                stringResource(
                                    R.string.mg_elements,
                                    when (draft.kind) {
                                        ManageKind.LIVE -> draft.channels.count { it.bouquetId == draft.selectedBouquetId }
                                        ManageKind.FILMS -> draft.films.size
                                        ManageKind.SERIES -> draft.series.size
                                    }
                                )
                            }"
                            else -> stringResource(R.string.mg_no_target)
                        },
                        fontSize = 15.sp, color = GoldSoft
                    )
                }
                // M10 — "Nouveau bouquet" is an action here (gold, live only).
                if (draft.kind == ManageKind.LIVE) {
                    MgActRow(
                        label = stringResource(R.string.mg_new_bouquet),
                        iconRes = R.drawable.ic_plus,
                        gold = true,
                        modifier = Modifier.focusRequester(actionFocus("newBouquet"))
                    ) { dialogOpenerKey = "newBouquet"; newBouquetDialog = true }
                }
                // §4.62 — add the selected item(s) to a My Playlist bouquet
                // (reference: the original stays in its subscription).
                // B-X1 — disabled (nothing to add / no active playlist):
                // visible but out of the focus path.
                MgActRow(
                    label = stringResource(R.string.my_playlist_add),
                    iconRes = R.drawable.ic_plus,
                    gold = true,
                    enabled = addTargetIds.isNotEmpty() && activePlaylist != null,
                    modifier = Modifier.focusRequester(actionFocus("addToMy"))
                        .focusProperties { canFocus = addTargetIds.isNotEmpty() && activePlaylist != null }
                ) { dialogOpenerKey = "addToMy"; addToMyDialog = true }
                MgActRow(
                    label = stringResource(if (focusItem?.third == true) R.string.show else R.string.hide),
                    iconRes = if (focusItem?.third == true) R.drawable.ic_eye else R.drawable.ic_eye_off,
                    enabled = focusId != null,
                    modifier = Modifier.focusProperties { canFocus = focusId != null }
                ) { focusId?.let { viewModel.setHiddenSingle(it, focusItem?.third != true) } }
                MgActRow(
                    label = stringResource(R.string.reorder_up),
                    iconRes = R.drawable.ic_chevron_up,
                    enabled = focusId != null,
                    modifier = Modifier.focusProperties { canFocus = focusId != null }
                ) {
                    focusId?.let {
                        if (draft.kind == ManageKind.LIVE) viewModel.reorderChannel(it, -1)
                        else viewModel.reorderBouquet(it, -1)
                    }
                }
                MgActRow(
                    label = stringResource(R.string.reorder_down),
                    iconRes = R.drawable.ic_chevron_down,
                    enabled = focusId != null,
                    modifier = Modifier.focusProperties { canFocus = focusId != null }
                ) {
                    focusId?.let {
                        if (draft.kind == ManageKind.LIVE) viewModel.reorderChannel(it, 1)
                        else viewModel.reorderBouquet(it, 1)
                    }
                }
                if (draft.kind == ManageKind.LIVE) {
                    MgActRow(
                        label = stringResource(R.string.move_to),
                        iconRes = R.drawable.ic_move,
                        enabled = focusId != null && kindBouquets.size >= 2,
                        modifier = Modifier.focusRequester(actionFocus("moveTo"))
                            .focusProperties { canFocus = focusId != null && kindBouquets.size >= 2 }
                    ) { dialogOpenerKey = "moveTo"; moveTargetBouquet = focusId }
                    MgActRow(
                        label = stringResource(R.string.assign_number),
                        iconRes = R.drawable.ic_hash,
                        enabled = focusId != null,
                        modifier = Modifier.focusRequester(actionFocus("assignNumber"))
                            .focusProperties { canFocus = focusId != null }
                    ) { focusId?.let { dialogOpenerKey = "assignNumber"; numberTarget = it } }
                }
                MgActRow(
                    label = stringResource(R.string.rename),
                    iconRes = R.drawable.ic_edit,
                    enabled = focusId != null,
                    modifier = Modifier.focusRequester(actionFocus("rename"))
                        .focusProperties { canFocus = focusId != null }
                ) {
                    focusId?.let {
                        dialogOpenerKey = "rename"
                        renameTarget = it to if (draft.kind == ManageKind.LIVE) "channel" else "bouquet"
                        renameValue = items.firstOrNull { item -> item.first == it }?.second ?: ""
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.mg_note),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldSoft,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
        // M07 — bottom save bar: status + hint + discard + 60dp save + dirty badge.
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0x33E5C158), Color(0x0FE5C158))))
                .border(2.dp, BorderStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDirty) stringResource(R.string.st_dirty) else stringResource(R.string.mg_clean),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                )
                if (!narrow) {
                    Text(
                        text = stringResource(R.string.mg_save_hint),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldSoft
                    )
                }
            }
            if (isDirty) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x2EFF8A70))
                        .border(1.5.dp, Color(0xFFFF8A70), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.st_dirty),
                        fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD9CF)
                    )
                }
            }
            // B-X1 — discarding with nothing to discard is a no-op, so
            // when clean the button leaves the focus path (it stays
            // visible); after a real discard focus moves home first, so
            // the recomposition never strands the remote with no focus.
            GhostButton(
                text = stringResource(R.string.mg_discard_changes),
                modifier = Modifier.focusProperties { canFocus = isDirty }
            ) {
                if (isDirty) {
                    viewModel.discardChanges()
                    showToast(context, context.getString(R.string.mg_discarded))
                    firstFocus.requestFocus()
                }
            }
            GoldSaveButton(
                text = stringResource(if (narrow) R.string.save else R.string.save_changes),
                enabled = isDirty
            ) { viewModel.save() }
        }
        // M08 — remote hint below the save bar.
        RemoteHintBar(
            listOf(
                "↑ ↓" to stringResource(R.string.hint_choose_row),
                "← →" to stringResource(R.string.hint_choose_col),
                "OK" to stringResource(R.string.select)
            )
        )
    }

    // M10 — new bouquet dialog (moved from under the bouquet column).
    if (newBouquetDialog) {
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            // R-B2: plain CardSurface (20dp, theme colour) with the shadow.
            CardSurface {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Text(text = stringResource(R.string.mg_new_bouquet_title), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                Text(text = stringResource(R.string.mg_new_bouquet_hint), fontSize = 16.sp, color = GoldSoft)
                ReadOnlyField(stringResource(R.string.new_bouquet_name), newBouquetName, modifier = Modifier.focusRequester(dialogFocus)) {
                    keyboardTarget = "newBouquet"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.add_bouquet)) {
                        if (newBouquetName.isNotBlank()) {
                            viewModel.addBouquet(newBouquetName)
                            newBouquetName = ""
                        }
                        newBouquetDialog = false
                    }
                    GhostButton(text = stringResource(R.string.cancel)) { newBouquetDialog = false }
                }
                }
            }
        }
    }

    // Rename dialog (inline editor; text entry via the built-in keyboard below).
    renameTarget?.let { (id, kind) ->
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            // R-B2: plain CardSurface (20dp, theme colour) with the shadow.
            CardSurface {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Text(text = stringResource(if (kind == "channel") R.string.rename_channel else R.string.rename_bouquet), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                Text(text = stringResource(R.string.mg_rename_hint), fontSize = 16.sp, color = GoldSoft)
                ReadOnlyField(stringResource(R.string.rename), renameValue, modifier = Modifier.focusRequester(dialogFocus)) { keyboardTarget = "rename" }
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
    }

    // Move-to-bouquet picker (B-M1): a real Dialog, so focus is stolen
    // on open (first bouquet option), trapped inside its window until it
    // closes, and the Back key dismisses it instead of leaving the screen.
    moveTargetBouquet?.let { focusIdValue ->
        Dialog(
            onDismissRequest = { moveTargetBouquet = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            // R-B2: plain CardSurface (20dp, theme colour) with the shadow.
            CardSurface {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                Text(text = stringResource(R.string.move_to), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                // The bouquet list scrolls inside AT MOST the remaining
                // dialog height (the dialog is bounded by the outer Box's
                // 120dp padding), so with >~8 bouquets the list scrolls
                // and "Annuler" stays visible instead of being pushed out.
                // fill = false keeps short lists compact (a plain
                // weight(1f) would stretch the dialog to its max height
                // even with 2 bouquets).
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(kindBouquets) { index, bouquet ->
                        GhostButton(
                            text = bouquet.name,
                            modifier = Modifier.fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(moveFocus) else Modifier)
                        ) {
                            if (selectedIds.size > 1) viewModel.moveSelectedToBouquet(bouquet.id)
                            else viewModel.moveSingleToBouquet(focusIdValue, bouquet.id)
                            moveTargetBouquet = null
                        }
                    }
                }
                GhostButton(text = stringResource(R.string.cancel)) { moveTargetBouquet = null }
            }
            }
        }
        }
    }

    // §4.62 — add to My Playlist: pick one of this kind's bouquets, or
    // create a new bouquet and add the item(s) to it in one step.
    if (addToMyDialog) {
        Box(modifier = Modifier.fillMaxSize().padding(120.dp), contentAlignment = Alignment.Center) {
            // R-B2: plain CardSurface (20dp, theme colour) with the shadow.
            CardSurface {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                Text(text = stringResource(R.string.my_playlist_add), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                Text(
                    text = focusItem?.second ?: kindBouquets.firstOrNull { it.id == draft.selectedBouquetId }?.name ?: "—",
                    fontSize = 16.sp, color = GoldSoft
                )
                if (myBouquets.isEmpty()) {
                    Text(text = stringResource(R.string.my_playlist_no_bouquet), fontSize = 16.sp, color = GoldSoft)
                } else {
                    Text(text = stringResource(R.string.my_playlist_choose_bouquet), fontSize = 16.sp, color = GoldSoft)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(myBouquets) { _, bouquet ->
                            GhostButton(text = bouquet.name, modifier = Modifier.fillMaxWidth()) {
                                activePlaylist?.let {
                                    myPlaylistViewModel.addToBouquet(bouquet.id, itemKind, it.id, addTargetIds)
                                }
                                addToMyDialog = false
                            }
                        }
                    }
                }
                ReadOnlyField(stringResource(R.string.new_bouquet_name), newMyBouquetName, modifier = Modifier.focusRequester(dialogFocus)) {
                    keyboardTarget = "newMyBouquet"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.my_playlist_create_and_add)) {
                        activePlaylist?.let {
                            if (newMyBouquetName.isNotBlank()) {
                                myPlaylistViewModel.createBouquetAndAdd(
                                    newMyBouquetName, bouquetKind, it.id, itemKind, addTargetIds
                                )
                                newMyBouquetName = ""
                            }
                        }
                        addToMyDialog = false
                    }
                    GhostButton(text = stringResource(R.string.cancel)) { addToMyDialog = false }
                }
                }
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
                    showToast(context, context.getString(R.string.number_duplicate, number ?: 0, conflict))
                } else if (number != null) {
                    showToast(context, context.getString(R.string.number_assigned, number, channel?.customName ?: channel?.name ?: ""))
                }
                numberTarget = null
            },
            onCancel = { numberTarget = null }
        )
    }
    // Built-in keyboard sheet for rename / new bouquet / new My Playlist bouquet.
    keyboardTarget?.let { target ->
        val current = when (target) {
            "rename" -> renameValue
            "newMyBouquet" -> newMyBouquetName
            else -> newBouquetName
        }
        val setValue: (String) -> Unit = when (target) {
            "rename" -> { { renameValue = it } }
            "newMyBouquet" -> { { newMyBouquetName = it } }
            else -> { { newBouquetName = it } }
        }
        Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = current, color = GoldWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                BuiltInKeyboard(
                    onKey = { c -> setValue(current + c) },
                    onAction = { action ->
                        when (action) {
                            is KeyboardAction.Char -> Unit // already handled via onKey
                            KeyboardAction.Backspace -> setValue(current.dropLast(1))
                            KeyboardAction.Space -> setValue(current + " ")
                            KeyboardAction.Shift -> { }
                            KeyboardAction.SwitchLanguage -> keyboardLang =
                                if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                            KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                            KeyboardAction.Validate -> keyboardTarget = null
                            KeyboardAction.Close -> keyboardTarget = null
                        }
                    },
                    language = keyboardLang,
                    mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS,
                    onClearAll = { setValue("") }
                )
            }
        }
    }
}

/** M04 — workspace row: 38dp order + 46dp icon + 20sp title + 15sp subtitle + badges. */
@Composable
private fun ManageRow(
    ordText: String,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badges: List<String>,
    selected: Boolean,
    checked: Boolean? = null,
    onClick: () -> Unit
) {
    // Narrow canvas: drop the 38dp order number and the visible 28dp
    // checkbox (selection is still shown by the gold background/border)
    // and shrink the icon 46dp -> 36dp, so the title column gets ~102dp
    // back; on the reference the row stays as it was.
    val narrow = LocalConfiguration.current.screenWidthDp < 700
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .then(if (selected) Modifier.background(GoldGradient) else Modifier.background(Color(0x73000000)))
            .border(2.dp, if (selected) BorderStrong else BorderGold, RoundedCornerShape(17.dp))
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!narrow) {
            Box(
                modifier = Modifier.size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Color(0x381A1100) else Color(0xFF2A2113)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ordText, fontSize = 16.sp, fontWeight = FontWeight.Black,
                    color = if (selected) GoldDeep else Gold
                )
            }
        }
        if (checked != null && !narrow) {
            // R-S2: icon checkbox (Feather ic_check) instead of the "☑"/"☐"
            // text glyphs: 2px gold border when checked, ic_check inside.
            Box(
                modifier = Modifier.size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        2.dp,
                        if (checked) Gold else BorderGold,
                        RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = if (selected) GoldDeep else Gold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        GoldIconBox(iconRes = iconRes, boxSize = if (narrow) 36.dp else 46.dp, iconSize = if (narrow) 18.dp else 24.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = if (selected) GoldDeep else GoldWhite
            )
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, fontSize = 15.sp, color = if (selected) GoldDeep else GoldSoft)
            }
        }
        badges.forEach { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0x381A1100) else Color(0xFF2A2113))
                    .border(1.dp, BorderGold, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) GoldDeep else GoldBright
                )
            }
        }
    }
}
