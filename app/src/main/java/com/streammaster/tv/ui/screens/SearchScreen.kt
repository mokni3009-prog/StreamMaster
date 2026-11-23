package com.streammaster.tv.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.SearchViewModel
import java.util.Locale

/**
 * Search — built-in AZERTY keyboard (components agent), type filter,
 * previous searches. Query field is read-only; typing happens on the keyboard.
 */
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Initial focus: the first filter pill ("Tous").
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Voice search (2026-11-22): system speech recognizer (remote mic).
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) viewModel.setQuery(spoken)
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoiceSearch(context, voiceLauncher)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.search_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
        Text(
            text = if (state.query.isEmpty()) stringResource(R.string.search_hint) else state.query,
            fontSize = 20.sp, color = if (state.query.isEmpty()) TextMuted else GoldWhite,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardSurface).padding(16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SortPill(stringResource(R.string.search_filter_all), state.filter == null, Modifier.focusRequester(firstFocus)) { viewModel.setFilter(null) }
            SortPill(stringResource(R.string.search_filter_live), state.filter == ContentKind.LIVE) { viewModel.setFilter(ContentKind.LIVE) }
            SortPill(stringResource(R.string.search_filter_film), state.filter == ContentKind.FILM) { viewModel.setFilter(ContentKind.FILM) }
            SortPill(stringResource(R.string.search_filter_series), state.filter == ContentKind.SERIES) { viewModel.setFilter(ContentKind.SERIES) }
            GhostButton(text = "🎤 ${stringResource(R.string.voice_search)}") {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        // Results
        if (state.query.isNotEmpty() && state.results.isEmpty() && !state.searching) {
            Text(text = stringResource(R.string.no_results), fontSize = 18.sp, color = TextMuted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(state.results) { result ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardSurface)
                        .tvFocus()
                        .clickable {
                            viewModel.submitSearch()
                            when (result.kind) {
                                ContentKind.LIVE -> navController.navigate(Routes.player("live", result.id))
                                ContentKind.FILM -> navController.navigate(Routes.detail("film", result.id))
                                ContentKind.SERIES -> navController.navigate(Routes.detail("series", result.id))
                            }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = result.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                    Text(text = result.subtitle, fontSize = 15.sp, color = TextMuted)
                }
            }
            if (state.query.isEmpty() && state.previousSearches.isNotEmpty()) {
                item {
                    Text(text = stringResource(R.string.previous_searches), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite, modifier = Modifier.padding(top = 8.dp))
                }
                items(state.previousSearches) { previous ->
                    Text(
                        text = previous, fontSize = 17.sp, color = GoldWhite,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardSurface)
                            .tvFocus()
                            .clickable { viewModel.runPreviousSearch(previous) }
                            .padding(14.dp)
                    )
                }
            }
        }
        // Built-in keyboard at the bottom
        BuiltInKeyboard(
            onKey = { c -> viewModel.setQuery(state.query + c) },
            onAction = { action ->
                when (action) {
                    is KeyboardAction.Char -> Unit // already handled via onKey
                    KeyboardAction.Backspace -> viewModel.setQuery(state.query.dropLast(1))
                    KeyboardAction.Space -> viewModel.setQuery(state.query + " ")
                    KeyboardAction.Shift -> { }
                    KeyboardAction.SwitchLanguage -> keyboardLang =
                        if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                    KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                    KeyboardAction.Validate -> viewModel.submitSearch()
                    KeyboardAction.Close -> { }
                }
            },
            language = keyboardLang,
            mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS
        )
    }
}

private fun launchVoiceSearch(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "")
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        launcher.launch(intent)
    } else {
        Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show()
    }
}
