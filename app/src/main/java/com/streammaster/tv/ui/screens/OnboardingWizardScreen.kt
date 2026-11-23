package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.components.LoadingIndicator
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.LoginTab
import com.streammaster.tv.ui.viewmodel.OnboardingWizardViewModel

/** Route for the first-run wizard (§4.39). Registered by the nav builder. */
const val ONBOARDING_WIZARD_ROUTE = "onboarding_wizard"

/**
 * First-run wizard (§4.39): language → playlist (Xtream/Stalker/M3U, same
 * fields as the login screen) → connection test with a plain-language error
 * → background sync. On completion the app goes to Loading/Home.
 */
@Composable
fun OnboardingWizardScreen(
    navController: NavHostController,
    viewModel: OnboardingWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var keyboardField by remember { mutableStateOf<String?>(null) }
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    var pickedLang by remember { mutableStateOf("fr") }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            navController.navigate(Routes.LOADING) {
                popUpTo(ONBOARDING_WIZARD_ROUTE) { inclusive = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(bottom = if (keyboardField != null) 320.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SectionHeader(
                stringResource(R.string.ns_onboarding_title),
                stringResource(R.string.ns_onboarding_step, state.step + 1, 4)
            )
            when (state.step) {
                0 -> {
                    Text(text = stringResource(R.string.ns_onboarding_lang), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SortPill("Français", pickedLang == "fr") { pickedLang = "fr"; viewModel.setLang("fr") }
                        SortPill("العربية", pickedLang == "ar") { pickedLang = "ar"; viewModel.setLang("ar") }
                        SortPill("English", pickedLang == "en") { pickedLang = "en"; viewModel.setLang("en") }
                    }
                    Text(
                        text = stringResource(R.string.ns_onboarding_lang),
                        fontSize = 16.sp, color = TextMuted
                    )
                    GoldButton(text = stringResource(R.string.ns_onboarding_next), modifier = Modifier.fillMaxWidth()) {
                        viewModel.goToStep(1)
                    }
                }
                1 -> {
                    Text(text = stringResource(R.string.ns_onboarding_playlist), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SortPill("Xtream", state.tab == LoginTab.XTREAM) { viewModel.setTab(LoginTab.XTREAM) }
                        SortPill("Stalker", state.tab == LoginTab.STALKER) { viewModel.setTab(LoginTab.STALKER) }
                        SortPill("M3U", state.tab == LoginTab.M3U) { viewModel.setTab(LoginTab.M3U) }
                    }
                    when (state.tab) {
                        LoginTab.XTREAM -> {
                            ReadOnlyField(stringResource(R.string.field_server), state.serverUrl) { keyboardField = "server" }
                            ReadOnlyField(stringResource(R.string.field_username), state.username) { keyboardField = "user" }
                            ReadOnlyField(stringResource(R.string.field_password), state.password) { keyboardField = "pass" }
                        }
                        LoginTab.STALKER -> {
                            ReadOnlyField(stringResource(R.string.field_portal_url), state.portalUrl) { keyboardField = "portal" }
                            ReadOnlyField(stringResource(R.string.field_mac), state.mac) { keyboardField = "mac" }
                        }
                        LoginTab.M3U -> {
                            ReadOnlyField(stringResource(R.string.field_m3u_url), state.m3uUrl) { keyboardField = "m3u" }
                        }
                    }
                    ReadOnlyField(stringResource(R.string.field_playlist_name), state.playlistName) { keyboardField = "name" }
                    if (state.errorRes != null) {
                        Text(text = stringResource(state.errorRes!!), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Danger)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GhostButton(text = stringResource(R.string.ns_onboarding_back)) { viewModel.goToStep(0) }
                        GoldButton(
                            text = if (state.loading) stringResource(R.string.ns_onboarding_adding)
                            else stringResource(R.string.ns_onboarding_add),
                            enabled = !state.loading,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.submitPlaylist() }
                    }
                }
                2 -> {
                    Text(text = stringResource(R.string.ns_onboarding_test), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                    if (!state.testDone) {
                        LoadingIndicator()
                    } else {
                        val result = state.testResult
                        if (result != null && result.online) {
                            Text(
                                text = stringResource(R.string.ns_onboarding_test_ok, result.detail),
                                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldBright
                            )
                            GoldButton(text = stringResource(R.string.ns_onboarding_next), modifier = Modifier.fillMaxWidth()) {
                                viewModel.finishSync()
                            }
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.ns_onboarding_test_fail,
                                    result?.detail ?: "unreachable"
                                ),
                                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Danger
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                GhostButton(text = stringResource(R.string.ns_onboarding_back)) { viewModel.goToStep(1) }
                                GoldButton(text = stringResource(R.string.ns_onboarding_retry), modifier = Modifier.weight(1f)) {
                                    viewModel.testConnection()
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text(text = stringResource(R.string.ns_onboarding_sync), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                    if (state.syncing || !state.syncDone) {
                        LoadingIndicator(stringResource(R.string.ns_onboarding_syncing))
                    } else {
                        Text(text = stringResource(R.string.ns_onboarding_sync_done), fontSize = 19.sp, color = GoldBright)
                    }
                }
            }
        }

        // Built-in keyboard sheet (same pattern as LoginScreen).
        keyboardField?.let { field ->
            Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = wizardFieldValue(state, field),
                        color = GoldWhite, fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BuiltInKeyboard(
                        onKey = { c -> viewModel.setWizardField(field, wizardFieldValue(state, field) + c) },
                        onAction = { action ->
                            when (action) {
                                is KeyboardAction.Char -> Unit
                                KeyboardAction.Backspace -> viewModel.setWizardField(field, wizardFieldValue(state, field).dropLast(1))
                                KeyboardAction.Space -> viewModel.setWizardField(field, wizardFieldValue(state, field) + " ")
                                KeyboardAction.Shift -> { /* layout handles case internally */ }
                                KeyboardAction.SwitchLanguage -> keyboardLang =
                                    if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                                KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                                KeyboardAction.Validate -> keyboardField = null
                                KeyboardAction.Close -> keyboardField = null
                            }
                        },
                        language = keyboardLang,
                        mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS,
                        onClearAll = { viewModel.setWizardField(field, "") }
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

private fun wizardFieldValue(state: com.streammaster.tv.ui.viewmodel.OnboardingUiState, field: String): String =
    when (field) {
        "server" -> state.serverUrl
        "user" -> state.username
        "pass" -> state.password
        "portal" -> state.portalUrl
        "mac" -> state.mac
        "m3u" -> state.m3uUrl
        "name" -> state.playlistName
        else -> ""
    }

/** Sets one wizard text field by key (used by the built-in keyboard). */
internal fun OnboardingWizardViewModel.setWizardField(field: String, value: String) {
    when (field) {
        "server" -> setServerUrl(value)
        "user" -> setUsername(value)
        "pass" -> setPassword(value)
        "portal" -> setPortalUrl(value)
        "mac" -> setMac(value)
        "m3u" -> setM3uUrl(value)
        "name" -> setPlaylistName(value)
    }
}
