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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.viewmodel.LoginTab
import com.streammaster.tv.ui.viewmodel.LoginViewModel
import com.streammaster.tv.ui.components.GoldButton

private enum class LoginField { SERVER, USERNAME, PASSWORD, PORTAL, MAC, M3U, NAME }

/**
 * Login — tabs Xtream / Stalker / M3U (CONTRACT §6).
 * Stalker: portal URL + MAC validated as 6 hex pairs (in the ViewModel).
 * M3U: link field. All fields are read-only; the built-in keyboard
 * (components agent) opens on OK over a field. On success -> Loading.
 */
@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var keyboardField by remember { mutableStateOf<LoginField?>(null) }
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    // Initial focus: the playlist-name field (first field, above the tabs).
    val firstFieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFieldFocus.requestFocus() }

    LaunchedEffect(state.success) {
        if (state.success) {
            navController.navigate(Routes.LOADING) { popUpTo(Routes.LOGIN) { inclusive = true } }
        }
    }

    fun fieldValue(field: LoginField): String = when (field) {
        LoginField.SERVER -> state.serverUrl
        LoginField.USERNAME -> state.username
        LoginField.PASSWORD -> state.password
        LoginField.PORTAL -> state.portalUrl
        LoginField.MAC -> state.mac
        LoginField.M3U -> state.m3uUrl
        LoginField.NAME -> state.playlistName
    }
    fun setField(field: LoginField, value: String) {
        // Typing clears a previous login error (the inline error box below).
        viewModel.clearError()
        when (field) {
            LoginField.SERVER -> viewModel.setServerUrl(value)
            LoginField.USERNAME -> viewModel.setUsername(value)
            LoginField.PASSWORD -> viewModel.setPassword(value)
            LoginField.PORTAL -> viewModel.setPortalUrl(value)
            LoginField.MAC -> viewModel.setMac(value)
            LoginField.M3U -> viewModel.setM3uUrl(value)
            LoginField.NAME -> viewModel.setPlaylistName(value)
        }
    }

    ThemedScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.app_name), fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = Gold)
            Text(text = stringResource(R.string.login_title), fontSize = 26.sp, color = GoldWhite, modifier = Modifier.padding(top = 8.dp))
            // Prototype auth header: subtitle + version line.
            Text(text = stringResource(R.string.login_subtitle), fontSize = 18.sp, color = GoldWhite, modifier = Modifier.padding(top = 8.dp))
            Text(text = stringResource(R.string.login_version_line), fontSize = 16.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))

            // Playlist name FIRST (prototype: the name field sits above the tabs).
            Column(modifier = Modifier.width(640.dp).padding(top = 32.dp)) {
                ReadOnlyField(stringResource(R.string.field_playlist_name), state.playlistName, Modifier.focusRequester(firstFieldFocus)) { keyboardField = LoginField.NAME }
            }

            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 32.dp)) {
                listOf(
                    LoginTab.XTREAM to R.string.login_xtream,
                    LoginTab.STALKER to R.string.login_stalker,
                    LoginTab.M3U to R.string.login_m3u
                ).forEach { (tab, label) ->
                    SortPill(text = stringResource(label), selected = state.tab == tab) { viewModel.setTab(tab) }
                }
            }

            Column(modifier = Modifier.width(640.dp).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (state.tab) {
                    LoginTab.XTREAM -> {
                        ReadOnlyField(stringResource(R.string.field_server), state.serverUrl) { keyboardField = LoginField.SERVER }
                        ReadOnlyField(stringResource(R.string.field_username), state.username) { keyboardField = LoginField.USERNAME }
                        ReadOnlyField(stringResource(R.string.field_password), "•".repeat(state.password.length)) { keyboardField = LoginField.PASSWORD }
                    }
                    LoginTab.STALKER -> {
                        ReadOnlyField(stringResource(R.string.field_portal_url), state.portalUrl) { keyboardField = LoginField.PORTAL }
                        ReadOnlyField(stringResource(R.string.field_mac), state.mac) { keyboardField = LoginField.MAC }
                    }
                    LoginTab.M3U -> {
                        ReadOnlyField(stringResource(R.string.field_m3u_url), state.m3uUrl) { keyboardField = LoginField.M3U }
                    }
                }
                // Inline error box (prototype #login-error) instead of a Toast.
                if (state.errorRes != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF4A1512))
                            .border(1.dp, Danger, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(state.errorRes!!),
                            fontSize = 17.sp, color = GoldWhite
                        )
                    }
                }
                GoldButton(
                    text = (if (state.loading) stringResource(R.string.loading_title) else stringResource(R.string.login_button)).uppercase(),
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { viewModel.submit() }
            }
        }

        // Built-in keyboard sheet (components agent) over the target field.
        keyboardField?.let { field ->
            Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = fieldValue(field), color = GoldWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                    BuiltInKeyboard(
                        onKey = { c -> setField(field, fieldValue(field) + c) },
                        onAction = { action ->
                            when (action) {
                                is KeyboardAction.Char -> Unit // already handled via onKey
                                KeyboardAction.Backspace -> setField(field, fieldValue(field).dropLast(1))
                                KeyboardAction.Space -> setField(field, fieldValue(field) + " ")
                                KeyboardAction.Shift -> { /* layout handles case internally */ }
                                KeyboardAction.SwitchLanguage -> keyboardLang =
                                    if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                                KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                                KeyboardAction.Validate -> keyboardField = null
                                KeyboardAction.Close -> keyboardField = null
                            }
                        },
                        language = keyboardLang,
                        mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        GoldButton(text = stringResource(R.string.validate)) { keyboardField = null }
                        GhostButton(text = stringResource(R.string.back)) { keyboardField = null }
                    }
                }
            }
        }
    }
}
