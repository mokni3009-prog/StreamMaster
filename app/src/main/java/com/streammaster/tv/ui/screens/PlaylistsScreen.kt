package com.streammaster.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.PlaylistsViewModel
import com.streammaster.tv.ui.components.GoldButton

/**
 * Playlists (Profil) — add / edit / delete / activate, max 10, subscription
 * info card (status, expiry, connections, last update) + refresh button.
 */
@Composable
fun PlaylistsScreen(
    navController: NavHostController,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val form by viewModel.form.collectAsState()
    // Initial focus: the Add button (first focusable).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    val context = LocalContext.current
    var keyboardOpen by remember { mutableStateOf(false) }
    var keyboardField by remember { mutableStateOf("name") } // field the built-in keyboard edits
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    LaunchedEffect(form.messageRes) {
        form.messageRes?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(text = stringResource(R.string.playlists_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
            // Prototype count badge "2 / 10".
            Text(
                text = "${playlists.size} / 10",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gold,
                modifier = Modifier.padding(start = 16.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            GoldButton(text = stringResource(R.string.add_playlist), modifier = Modifier.focusRequester(firstFocus)) { viewModel.openAdd() }
        }
        Text(text = stringResource(R.string.playlists_version_line), fontSize = 16.sp, color = TextMuted)
        // Subscription info card for the active playlist
        val active = playlists.firstOrNull { it.isActive } ?: playlists.firstOrNull()
        if (active != null) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = stringResource(R.string.subscription_info), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Gold)
                Text(text = "${stringResource(R.string.status_label)}: ${active.status}", fontSize = 17.sp, color = GoldWhite)
                Text(text = "${stringResource(R.string.expiry_label_short)}: ${active.expiryDate}", fontSize = 17.sp, color = GoldWhite)
                Text(text = "${stringResource(R.string.connections_label)}: ${active.connections}", fontSize = 17.sp, color = GoldWhite)
                Text(text = "${stringResource(R.string.last_update_label)}: ${active.lastUpdate}", fontSize = 17.sp, color = TextMuted)
                GoldButton(text = stringResource(R.string.refresh), modifier = Modifier.padding(top = 8.dp)) {
                    viewModel.refresh(active.id)
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playlists) { playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (playlist.isActive) Gold else CardSurface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = playlist.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (playlist.isActive) GoldDeep else GoldWhite)
                        Text(text = playlist.type.name, fontSize = 15.sp, color = if (playlist.isActive) GoldDeep else TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!playlist.isActive) GhostButton(text = stringResource(R.string.activate)) { viewModel.activate(playlist.id) }
                        GhostButton(text = stringResource(R.string.edit_playlist)) { viewModel.openEdit(playlist) }
                        GhostButton(text = stringResource(R.string.delete)) { viewModel.askDelete(playlist) }
                    }
                }
            }
        }
        if (form.showForm) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = if (form.editing == null) stringResource(R.string.add_playlist) else stringResource(R.string.edit_playlist), fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                ReadOnlyField(stringResource(R.string.field_playlist_name), form.name) { keyboardField = "name"; keyboardOpen = true }
                ReadOnlyField(stringResource(R.string.field_xmltv_url), form.xmltvUrl) { keyboardField = "xmltv"; keyboardOpen = true }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.save)) { viewModel.saveForm(playlists.size) }
                    GhostButton(text = stringResource(R.string.cancel)) { viewModel.closeForm() }
                }
            }
        }
        form.confirmDelete?.let { playlist ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardSurface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = stringResource(R.string.confirm_delete_playlist), fontSize = 18.sp, color = GoldWhite)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GoldButton(text = stringResource(R.string.delete)) { viewModel.deleteConfirmed() }
                    GhostButton(text = stringResource(R.string.cancel)) { viewModel.cancelDelete() }
                }
            }
        }
        if (keyboardOpen) {
            Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    BuiltInKeyboard(
                        onKey = { c ->
                            if (keyboardField == "xmltv") viewModel.setXmltvUrl(form.xmltvUrl + c)
                            else viewModel.setName(form.name + c)
                        },
                        onAction = { action ->
                            when (action) {
                                is KeyboardAction.Char -> Unit // already handled via onKey
                                KeyboardAction.Backspace -> if (keyboardField == "xmltv") viewModel.setXmltvUrl(form.xmltvUrl.dropLast(1)) else viewModel.setName(form.name.dropLast(1))
                                KeyboardAction.Space -> if (keyboardField == "xmltv") viewModel.setXmltvUrl(form.xmltvUrl + " ") else viewModel.setName(form.name + " ")
                                KeyboardAction.Shift -> { }
                                KeyboardAction.SwitchLanguage -> keyboardLang =
                                    if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                                KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                                KeyboardAction.Validate -> keyboardOpen = false
                                KeyboardAction.Close -> keyboardOpen = false
                            }
                        },
                        language = keyboardLang,
                        mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
                    )
                }
            }
        }
    }
}
