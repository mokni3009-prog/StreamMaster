package com.streammaster.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.Playlist
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.ConfirmDialog
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
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
import com.streammaster.tv.ui.viewmodel.MAX_PLAYLISTS
import com.streammaster.tv.ui.viewmodel.PlaylistsViewModel

/**
 * Playlists (Profil) — Y01 3-column grid (22dp gap), Y02 playlist card
 * (54dp icon + 22sp name + server + ACTIVE/INACTIVE badge + meta
 * Utilisateur/Expiration), Y03 Lancer (gold) + Modifier + Supprimer
 * (danger), Y04 dashed add card (min 290dp, 78dp circle icon),
 * Y05 gold subscription card, Y07 wide add/edit dialog (4 fields +
 * error box + keyboard sheet; save = addPlaylist then refresh, like
 * Login), Y08 delete confirmation dialog.
 *
 * Y09 "My Playlist" is NOT built here: approved after the prototype, it
 * is built after the first APK per the task log — a planned remainder,
 * not a gap.
 */
@Composable
fun PlaylistsScreen(
    navController: NavHostController,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val form by viewModel.form.collectAsState()
    // Initial focus: the Add card (first focusable).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    // Initial focus inside the add/edit dialog: the name field.
    val formFocus = remember { FocusRequester() }
    LaunchedEffect(form.showForm) { if (form.showForm) formFocus.requestFocus() }
    val context = LocalContext.current
    var keyboardField by remember { mutableStateOf("name") } // name | server | user | pass | xmltv
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(form.messageRes) {
        form.messageRes?.let {
            showToast(context, context.getString(it))
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(form.showForm) { if (form.showForm) formError = null }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.playlists_title), fontSize = 30.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
            )
            Text(
                text = "${playlists.size} / 10",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gold,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        // Y05 — subscription card for the active playlist.
        val active = playlists.firstOrNull { it.isActive } ?: playlists.firstOrNull()
        if (active != null) {
            // R-B2: CardSurface with the card's own 18dp shape, gradient
            // and 2px border, so it carries the --card-shadow.
            CardSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                background = Brush.linearGradient(
                    listOf(Color(0x38E5C158), Color(0x0FE5C158))
                ),
                border = BorderStroke(2.dp, BorderStrong)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoldIconBox(iconRes = R.drawable.ic_list, boxSize = 52.dp, iconSize = 26.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = active.name, fontSize = 21.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                        Text(
                            text = "${stringResource(R.string.status_label)}: ${active.status} • " +
                                "${stringResource(R.string.expiry_label_short)}: ${active.expiryDate} • " +
                                "${stringResource(R.string.connections_label)}: ${active.connections}",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                    }
                    GhostButton(text = stringResource(R.string.refresh)) { viewModel.refresh(active.id) }
                }
            }
        }
        // Y01 — 3-column grid.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onLaunch = { viewModel.activate(playlist.id) },
                    onEdit = { viewModel.openEdit(playlist) },
                    onDelete = { viewModel.askDelete(playlist) }
                )
            }
            // Y04 — dashed add card.
            item {
                val canAdd = playlists.size < MAX_PLAYLISTS
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .height(290.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .dashedBorder(2.5.dp, BorderStrong, 24.dp)
                        .background(Color(0x0FE5C158))
                        // The focusRequester must come BEFORE tvFocus
                        // (which owns the focusable).
                        .focusRequester(firstFocus)
                        .tvFocus()
                        .clickable(enabled = canAdd) { viewModel.openAdd() }
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(78.dp)
                            .clip(RoundedCornerShape(39.dp))
                            .background(
                                if (canAdd) GoldGradient
                                else Brush.linearGradient(listOf(Color(0xFFFF9D86), Color(0xFFA33A22)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_plus),
                            contentDescription = null,
                            tint = GoldDeep,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.add_playlist),
                        fontSize = 23.sp, fontWeight = FontWeight.Black, color = GoldWhite
                    )
                    Text(
                        text = stringResource(R.string.pl_add_hint),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GoldBright
                    )
                }
            }
        }
    }

    // Y07 — wide add/edit dialog: 4 fields + error box + keyboard sheet.
    if (form.showForm) {
        Dialog(
            onDismissRequest = { viewModel.closeForm() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // R-B2: CardSurface with the dialog's own 24dp shape and 2px
            // border, so it carries the --card-shadow.
            CardSurface(
                modifier = Modifier.fillMaxWidth(0.85f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, BorderGold)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoldIconBox(iconRes = R.drawable.ic_list, boxSize = 52.dp, iconSize = 26.dp)
                    Text(
                        text = if (form.editing == null) stringResource(R.string.add_playlist)
                        else stringResource(R.string.edit_playlist),
                        fontSize = 22.sp, fontWeight = FontWeight.Black, color = GoldWhite
                    )
                }
                ReadOnlyField(stringResource(R.string.field_playlist_name), form.name, modifier = Modifier.focusRequester(formFocus)) { keyboardField = "name" }
                ReadOnlyField(stringResource(R.string.field_server), form.serverUrl) { keyboardField = "server" }
                ReadOnlyField(stringResource(R.string.field_username), form.username) { keyboardField = "user" }
                ReadOnlyField(stringResource(R.string.field_password), "•".repeat(form.password.length)) { keyboardField = "pass" }
                // Second EPG source (XMLTV) — kept from the current app; the
                // prototype form has the 4 fields above.
                ReadOnlyField(stringResource(R.string.field_xmltv_url), form.xmltvUrl) { keyboardField = "xmltv" }
                formError?.let { errorRes ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x29FF462D))
                            .border(1.5.dp, Color(0xFFFF6A4D), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(text = stringResource(errorRes), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD9CF))
                    }
                }
                val currentFieldValue = when (keyboardField) {
                    "server" -> form.serverUrl
                    "user" -> form.username
                    "pass" -> form.password
                    "xmltv" -> form.xmltvUrl
                    else -> form.name
                }
                val setField: (String) -> Unit = when (keyboardField) {
                    "server" -> viewModel::setServerUrl
                    "user" -> viewModel::setUsername
                    "pass" -> viewModel::setPassword
                    "xmltv" -> viewModel::setXmltvUrl
                    else -> viewModel::setName
                }
                BuiltInKeyboard(
                    onKey = { c -> setField(currentFieldValue + c) },
                    onAction = { action ->
                        when (action) {
                            is KeyboardAction.Char -> Unit // already handled via onKey
                            KeyboardAction.Backspace -> setField(currentFieldValue.dropLast(1))
                            KeyboardAction.Space -> setField(currentFieldValue + " ")
                            KeyboardAction.Shift -> { }
                            KeyboardAction.SwitchLanguage -> keyboardLang =
                                if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                            KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                            KeyboardAction.Validate -> Unit // Save button validates below
                            KeyboardAction.Close -> viewModel.closeForm()
                        }
                    },
                    language = keyboardLang,
                    mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS,
                    onClearAll = { setField("") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.save)) {
                        formError = when {
                            form.name.isBlank() -> R.string.pl_form_error_name
                            form.serverUrl.isBlank() || !form.serverUrl.startsWith("http") -> R.string.pl_form_error_server
                            form.username.isBlank() -> R.string.pl_form_error_user
                            else -> null
                        }
                        if (formError == null) viewModel.saveForm(playlists.size)
                    }
                    GhostButton(text = stringResource(R.string.cancel)) { viewModel.closeForm() }
                }
                }
            }
        }
    }

    // Y08 — delete confirmation via ConfirmDialog (ui/components).
    form.confirmDelete?.let { playlist ->
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist),
            message = "${stringResource(R.string.confirm_delete_playlist)} — ${playlist.name}",
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { viewModel.deleteConfirmed() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }
}

/** Y02 — playlist card. */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val server = playlist.serverUrl.ifBlank { playlist.portalUrl.ifBlank { playlist.m3uUrl } }.ifBlank { "—" }
    // R-B2: CardSurface with the card's own 24dp shape, gradient and 2px
    // border, so it carries the --card-shadow.
    CardSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        background = Brush.linearGradient(listOf(Color(0xFF1E1606), Color(0xFF0D0903))),
        border = BorderStroke(2.dp, if (playlist.isActive) BorderStrong else BorderGold)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GoldIconBox(iconRes = R.drawable.ic_list, boxSize = 54.dp, iconSize = 26.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = playlist.name, fontSize = 22.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                Text(text = server, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldBright)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (playlist.isActive) Modifier.background(GoldGradient)
                        else Modifier.background(Color(0x1AFFFFFF))
                    )
                    .border(1.dp, if (playlist.isActive) Color(0xFFFFFFFF) else BorderGold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(if (playlist.isActive) R.string.pl_active_badge else R.string.pl_inactive_badge),
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                    color = if (playlist.isActive) GoldDeep else GoldSoft
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x29FFE89E)))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text(text = stringResource(R.string.pl_user), fontSize = 14.sp, color = TextMuted)
                    Text(
                        text = playlist.username.ifBlank { "—" },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                    )
                }
                Column {
                    Text(text = stringResource(R.string.expiry_label_short), fontSize = 14.sp, color = TextMuted)
                    Text(
                        text = playlist.expiryDate.ifBlank { "—" },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x29FFE89E)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton(text = stringResource(R.string.pl_launch), modifier = Modifier.weight(1f)) { onLaunch() }
            GhostButton(text = stringResource(R.string.edit_playlist)) { onEdit() }
            DangerGhostButton(text = stringResource(R.string.delete)) { onDelete() }
        }
        }
    }
}

/** Danger ghost button (delete): red border/text on a red tint. */
@Composable
private fun DangerGhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x1FFF6A4D))
            .border(2.dp, Color(0xFFFF8A70), RoundedCornerShape(20.dp))
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC7BA))
    }
}
