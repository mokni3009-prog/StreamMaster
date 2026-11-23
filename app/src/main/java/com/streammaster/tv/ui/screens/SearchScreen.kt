package com.streammaster.tv.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.SearchResult
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.SearchViewModel
import java.util.Locale

/**
 * Search (SPEC §4.5, prototype `#view-search` / `.search-layout`).
 *
 * Two columns: search panel (1fr) + 400dp sidebar. The panel holds the
 * 62dp search field with its 42dp clear button and the result-count
 * pill, the built-in AZERTY keyboard and the remote hint. The sidebar
 * holds the scope pills ("Rechercher dans"), the recent searches, the
 * "Astuce" card and the voice-search button. Results below are grouped
 * in 3 sections — "Résultats — Chaînes / Films / Séries" — each with a
 * SectionHeader, a count badge and channel cards / posters.
 *
 * Query field is read-only; typing happens on the keyboard. Voice search
 * (SE04, approved) is kept, restyled with the ic_mic icon.
 */
@Composable
fun SearchScreen(
    navController: NavHostController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Initial focus: the first scope pill ("Tout").
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

    val liveResults = state.results.filter { it.kind == ContentKind.LIVE }
    val filmResults = state.results.filter { it.kind == ContentKind.FILM }
    val seriesResults = state.results.filter { it.kind == ContentKind.SERIES }

    // Narrow canvas (720p xhdpi: 640x360 physical dp — LocalConfiguration
    // reports physical dp, so this is true only there; same check as
    // Shell.kt:601). On narrow the 400dp sidebar next to the panel leaves
    // the keyboard 410dp wide (~22dp keys, "Valider" narrower than its
    // label) and the ~664dp panel overflows the 546dp content height with
    // no scrolling. There the panel goes full width and the sidebar moves
    // below it inside a scrollable column; the results keep their
    // LazyColumn(weight(1f)) below. Reference 1920x1080 unchanged.
    val narrow = LocalConfiguration.current.screenWidthDp < 700
    // Hoisted (unconditional) so the narrow branch needs no conditional
    // remember(): the scroll state of the narrow upper column.
    val narrowScrollState = rememberScrollState()
    // Keyboard / sidebar callbacks, hoisted so both layout branches share
    // one wiring (the branches differ only in arrangement + modifiers).
    val onKeyboardKey: (String) -> Unit = { c -> viewModel.setQuery(state.query + c) }
    val onKeyboardAction: (KeyboardAction) -> Unit = { action ->
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
    }
    val onClearQuery: () -> Unit = { viewModel.setQuery("") }
    val onFilterChange: (ContentKind?) -> Unit = { viewModel.setFilter(it) }
    val onPreviousSearch: (String) -> Unit = { viewModel.runPreviousSearch(it) }
    val onVoiceSearch: () -> Unit = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    fun openResult(result: SearchResult) {
        viewModel.submitSearch()
        when (result.kind) {
            ContentKind.LIVE -> navController.navigate(Routes.player("live", result.id))
            ContentKind.FILM -> navController.navigate(Routes.detail("film", result.id))
            ContentKind.SERIES -> navController.navigate(Routes.detail("series", result.id))
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = stringResource(R.string.search_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite)

        // SE01: two columns — search panel (1fr) + 400dp sidebar.
        // Narrow canvas only: panel full width, sidebar below it in a
        // vertically scrollable column (weight(1f), sharing the space
        // equally with the results LazyColumn below). Focused keys and
        // pills scroll themselves into view (scrollIntoView), so the last
        // keyboard row, "Valider", the hint and the whole sidebar stay
        // reachable by remote. The reference branch keeps the old Row.
        if (narrow) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(narrowScrollState),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SearchPanel(
                    query = state.query,
                    resultsCount = state.results.size,
                    keyboardLang = keyboardLang,
                    keyboard123 = keyboard123,
                    onKey = onKeyboardKey,
                    onAction = onKeyboardAction,
                    onClearQuery = onClearQuery,
                    modifier = Modifier.fillMaxWidth()
                )
                SearchSidebar(
                    filter = state.filter,
                    previousSearches = state.previousSearches,
                    onFilterChange = onFilterChange,
                    onPreviousSearch = onPreviousSearch,
                    onVoiceSearch = onVoiceSearch,
                    firstFocus = firstFocus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.Top
            ) {
                SearchPanel(
                    query = state.query,
                    resultsCount = state.results.size,
                    keyboardLang = keyboardLang,
                    keyboard123 = keyboard123,
                    onKey = onKeyboardKey,
                    onAction = onKeyboardAction,
                    onClearQuery = onClearQuery,
                    modifier = Modifier.weight(1f)
                )
                SearchSidebar(
                    filter = state.filter,
                    previousSearches = state.previousSearches,
                    onFilterChange = onFilterChange,
                    onPreviousSearch = onPreviousSearch,
                    onVoiceSearch = onVoiceSearch,
                    firstFocus = firstFocus,
                    modifier = Modifier.width(400.dp)
                )
            }
        }

        // SE05: results in 3 grouped sections, each with a SectionHeader,
        // a count badge and channel cards / posters.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            if (state.query.isNotEmpty() && state.results.isEmpty() && !state.searching) {
                item {
                    Text(text = stringResource(R.string.no_results), fontSize = 18.sp, color = TextMuted)
                }
            }
            if (state.query.isNotEmpty()) {
                item {
                    ResultSection(
                        title = stringResource(R.string.search_results_live),
                        count = liveResults.size
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(liveResults) { result ->
                                ChannelResultCard(result = result, onClick = { openResult(result) })
                            }
                        }
                    }
                }
                item {
                    ResultSection(
                        title = stringResource(R.string.search_results_films),
                        count = filmResults.size
                    ) {
                        LazyRow(
                            modifier = Modifier.height(344.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(filmResults) { result ->
                                PosterTile(
                                    title = result.title,
                                    hue = result.hue,
                                    code = result.title.take(2).uppercase(),
                                    onClick = { openResult(result) }
                                )
                            }
                        }
                    }
                }
                item {
                    ResultSection(
                        title = stringResource(R.string.search_results_series),
                        count = seriesResults.size
                    ) {
                        LazyRow(
                            modifier = Modifier.height(344.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(seriesResults) { result ->
                                PosterTile(
                                    title = result.title,
                                    hue = result.hue,
                                    code = result.title.take(2).uppercase(),
                                    onClick = { openResult(result) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Left panel (.kb-panel): search field + result-count pill, the built-in
 * AZERTY keyboard and the remote hint. Extracted from SearchScreen so the
 * narrow layout can reuse it full-width above the sidebar instead of
 * beside it; the reference call passes Modifier.weight(1f) as before.
 */
@Composable
private fun SearchPanel(
    query: String,
    resultsCount: Int,
    keyboardLang: KeyboardLanguage,
    keyboard123: Boolean,
    onKey: (String) -> Unit,
    onAction: (KeyboardAction) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    0.0f to Color(0xF01E1606),
                    1.0f to Color(0xF50E0A03)
                ),
                panelShape
            )
            .border(2.dp, BorderGold, panelShape)
            .padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // SE02 + SE09: search field row with the result-count pill.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fieldShape = RoundedCornerShape(18.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .clip(fieldShape)
                    .background(Color(0xEB060401))
                    .border(2.dp, GoldBright, fieldShape)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = GoldBright,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = if (query.isEmpty()) stringResource(R.string.search_hint) else query,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (query.isEmpty()) Color(0x73FFE89E) else GoldWhite,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    // .clear-x: 42dp circle.
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x29E5C158))
                            .border(1.5.dp, BorderGold, CircleShape)
                            .tvFocus()
                            .clickable { onClearQuery() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 20.sp, color = GoldWhite)
                    }
                }
            }
            // .search-count pill.
            Text(
                text = pluralStringResource(
                    R.plurals.search_results_count,
                    resultsCount,
                    resultsCount
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = GoldBright,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x2EE5C158))
                    .border(1.5.dp, BorderGold, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        // SE07: built-in keyboard inside the panel.
        BuiltInKeyboard(
            onKey = onKey,
            onAction = onAction,
            language = keyboardLang,
            mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS,
            onClearAll = onClearQuery
        )

        // SE08 (part): remote hint — ↑↓←→/OK/⌫.
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteHintKey(keys = listOf("↑", "↓", "←", "→"), label = stringResource(R.string.hint_navigate))
            RemoteHintKey(keys = listOf("OK"), label = stringResource(R.string.hint_type_letter))
            RemoteHintKey(keys = listOf("⌫"), label = stringResource(R.string.hint_erase))
        }
    }
}

/**
 * Right column (.kb-side, 400dp on the reference): scope pills
 * ("Rechercher dans"), recent searches, the "Astuce" card and the
 * voice-search button. Extracted from SearchScreen; the narrow layout
 * reuses it full-width below the panel. The initial-focus target (the
 * "Tout" pill, firstFocus) is unchanged.
 */
@Composable
private fun SearchSidebar(
    filter: ContentKind?,
    previousSearches: List<String>,
    onFilterChange: (ContentKind?) -> Unit,
    onPreviousSearch: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // SE03: scope pills — "Tout / Chaînes / Films / Séries".
        SideCard(title = stringResource(R.string.search_scope_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SortPill(stringResource(R.string.search_filter_all), filter == null, Modifier.focusRequester(firstFocus)) { onFilterChange(null) }
                SortPill(stringResource(R.string.search_filter_live), filter == ContentKind.LIVE) { onFilterChange(ContentKind.LIVE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SortPill(stringResource(R.string.search_filter_film), filter == ContentKind.FILM) { onFilterChange(ContentKind.FILM) }
                SortPill(stringResource(R.string.search_filter_series), filter == ContentKind.SERIES) { onFilterChange(ContentKind.SERIES) }
            }
        }
        // SE06: recent searches as 46dp chips with a clock icon.
        SideCard(title = stringResource(R.string.previous_searches)) {
            if (previousSearches.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_no_recent),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldSoft
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(previousSearches) { previous ->
                        Row(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .background(Color(0x1AE5C158))
                                .border(1.5.dp, BorderGold, RoundedCornerShape(23.dp))
                                .tvFocus()
                                .clickable { onPreviousSearch(previous) }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_clock),
                                contentDescription = null,
                                tint = GoldSoft,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = previous,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldSoft,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        // SE08: "Astuce" card.
        SideCard(title = stringResource(R.string.search_tip_title)) {
            Text(
                text = stringResource(R.string.search_tip_text),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = GoldSoft,
                lineHeight = 27.sp
            )
        }
        // SE04: voice search — kept, restyled with the ic_mic icon.
        // R-B2: plain CardSurface (20dp) with the shadow; focus/click
        // stay on the outer surface.
        CardSurface(
            modifier = Modifier.fillMaxWidth()
                .tvFocus()
                .clickable { onVoiceSearch() }
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.voice_search),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldWhite
                    )
                }
            }
        }
    }
}

/** `.kb-side-card`: 22dp radius, dark card, 1.5px border, gold h4. */
@Composable
private fun SideCard(
    title: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    // R-B2: CardSurface with the card's own 22dp shape, solid background
    // and 1.5px border, so it carries the --card-shadow.
    CardSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        background = SolidColor(Color(0xD1140E04)),
        border = BorderStroke(1.5.dp, BorderGold)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = GoldBright,
                letterSpacing = 1.5.sp
            )
            content()
        }
    }
}

/** One remote-hint group: kbd chip(s) + label. */
@Composable
private fun RemoteHintKey(keys: List<String>, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { key ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x80000000))
                    .border(1.5.dp, BorderGold, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = key, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
            }
        }
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldSoft)
    }
}

/** A result section: SectionHeader + gold count badge + content. */
@Composable
private fun ResultSection(
    title: String,
    count: Int,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = title, modifier = Modifier.weight(1f))
            // .badge-cnt: gold pill with the count.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(GoldGradient)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldDeep
                )
            }
        }
        content()
    }
}

/** Channel result card: hue logo block + title + subtitle. */
@Composable
private fun ChannelResultCard(
    result: SearchResult,
    onClick: () -> Unit
) {
    // R-B2: plain CardSurface (20dp) with the shadow; focus/click stay on
    // the outer surface so the 1.03 scale covers the whole card.
    CardSurface(
        modifier = Modifier
            .width(320.dp)
            .height(120.dp)
            .tvFocus()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.hsv(result.hue.toFloat(), 0.55f, 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = result.title.take(2).uppercase(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = GoldWhite
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GoldWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.subtitle,
                fontSize = 16.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
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
        showToast(context, context.getString(R.string.voice_search_unavailable))
    }
}
