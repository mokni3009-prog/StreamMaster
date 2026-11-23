package com.streammaster.tv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
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
import com.streammaster.tv.data.util.TitleNormalizer
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.util.TrailerOpener
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.EpgViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * EPG — time grid: channels × time 20:00–02:00, 30-min slots (SPEC §4.4),
 * navigable across nights (tonight + the next nights, up to 7 days).
 * Current program opens the channel; a future program opens an action
 * dialog: reminder 🔔 or scheduled recording ● (2026-11-22).
 */
@Composable
fun EpgScreen(
    navController: NavHostController,
    viewModel: EpgViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // Initial focus: the first day tab.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    val scheduled by viewModel.scheduled.collectAsState()
    // Collecting the catalog starts its load; enrichmentFor() reads its value.
    val catalog by viewModel.catalog.collectAsState()
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val slots = (0 until 12).map { state.windowStartMillis + it * 30 * 60 * 1000L }
    val now = System.currentTimeMillis()
    // Program whose action dialog (reminder / record) is open.
    var programAction by remember { mutableStateOf<Pair<com.streammaster.tv.data.model.Channel, com.streammaster.tv.data.model.EpgProgram>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = stringResource(R.string.epg_title), fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(bottom = 16.dp))
        // Day tabs: tonight + the next nights (up to 7 days, per the EPG window setting).
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..state.maxDayOffset).forEach { offset ->
                val selected = offset == state.selectedDayOffset
                Box(
                    modifier = Modifier
                        .then(if (offset == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Gold else CardSurface)
                        .tvFocus()
                        .clickable { viewModel.selectDay(offset) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayLabel(offset),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (selected) GoldDeep else GoldWhite
                    )
                }
            }
        }
        // Header row: time slots
        Row(modifier = Modifier.fillMaxWidth().padding(start = 220.dp)) {
            slots.forEach { slot ->
                Text(text = timeFormat.format(Date(slot)), fontSize = 14.sp, color = TextMuted, modifier = Modifier.width(110.dp))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(state.channels) { channel ->
                val programs by viewModel.programsFor(channel.id).collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = channel.customName ?: channel.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite, maxLines = 1, modifier = Modifier.width(210.dp))
                    slots.forEach { slot ->
                        val program = programs.firstOrNull { it.startMillis <= slot && it.endMillis > slot }
                        val isReminder = program?.let { viewModel.isReminder(state.reminders, channel.id, it.title, it.startMillis) } ?: false
                        val isScheduled = program?.let { viewModel.isScheduled(scheduled, channel.id, it.title, it.startMillis) } ?: false
                        val isSeriesRule = program?.let { viewModel.isSeriesRule(state.seriesRules, channel.id, it.title) } ?: false
                        val isCurrent = program != null && program.startMillis <= now && program.endMillis > now
                        Box(
                            modifier = Modifier.width(110.dp).height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Gold else CardSurface)
                                .tvFocus()
                                .clickable {
                                    if (program == null) return@clickable
                                    if (isCurrent) {
                                        navController.navigate(Routes.player("live", channel.id))
                                    } else {
                                        // Future program: choose reminder and/or recording.
                                        programAction = channel to program
                                    }
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = (if (isReminder) "🔔 " else "") + (if (isScheduled) "● " else "") + (if (isSeriesRule) "▣ " else "") + (program?.title ?: ""),
                                fontSize = 12.sp, maxLines = 2,
                                color = if (isCurrent) GoldDeep else GoldWhite
                            )
                        }
                    }
                }
            }
        }
    }

    programAction?.let { (channel, program) ->
        val isReminder = viewModel.isReminder(state.reminders, channel.id, program.title, program.startMillis)
        // EPG enrichment (2026-11-22): poster + synopsis + rating from the
        // VOD catalog when the same title exists there; the program's own
        // EPG description is the fallback.
        val enrichment = catalog[TitleNormalizer.normalize(program.title)]
        val enrichedDescription = (enrichment?.description?.takeIf { it.isNotBlank() } ?: program.description).take(240)
        val subtitle = buildString {
            if (enrichment != null && enrichment.rating > 0) {
                append("★ ${"%.1f".format(enrichment.rating)}")
                if (enrichment.genre.isNotBlank()) append(" • ${enrichment.genre}")
                append("\n")
            }
            append(enrichedDescription)
        }.trim()
        SelectorDialog(
            title = program.title,
            options = listOf(
                SelectorOption(
                    "reminder",
                    if (isReminder) stringResource(R.string.reminder_remove) else stringResource(R.string.reminder_add)
                ),
                SelectorOption("once", stringResource(R.string.record_once)),
                SelectorOption("daily", stringResource(R.string.record_daily)),
                SelectorOption("weekly", stringResource(R.string.record_weekly)),
                SelectorOption("series", stringResource(R.string.record_series)),
                SelectorOption("trailer", stringResource(R.string.trailer))
            ),
            selectedId = null,
            subtitle = subtitle,
            posterUrl = enrichment?.posterUrl,
            onSelect = { id ->
                when (id) {
                    "reminder" -> {
                        viewModel.toggleReminder(channel.id, program, isReminder)
                        Toast.makeText(
                            context,
                            if (isReminder) R.string.reminder_removed else R.string.reminder_set,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    "once", "daily", "weekly" -> {
                        viewModel.scheduleRecording(
                            channel.id, channel.customName ?: channel.name, program, id
                        )
                        Toast.makeText(context, R.string.recording_scheduled, Toast.LENGTH_SHORT).show()
                    }
                    "series" -> {
                        viewModel.scheduleSeries(channel, program)
                        Toast.makeText(context, R.string.series_recording_set, Toast.LENGTH_SHORT).show()
                    }
                    "trailer" -> {
                        // Trailer opens YouTube on top; the dialog stays so
                        // the user can still set a reminder/recording after.
                        TrailerOpener.open(context, program.title, enrichment?.trailerUrl)
                    }
                }
                if (id != "trailer") programAction = null
            },
            onDismiss = { programAction = null }
        )
    }
}

/** Day-tab label: tonight / tomorrow night, then the weekday + date. */
@Composable
private fun dayLabel(offset: Int): String = when (offset) {
    0 -> stringResource(R.string.epg_today)
    1 -> stringResource(R.string.epg_tomorrow)
    else -> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(cal.time)
    }
}
