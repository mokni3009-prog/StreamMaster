package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.util.TitleNormalizer
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.util.TrailerOpener
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.theme.tvFocusBorderless
import com.streammaster.tv.ui.viewmodel.EpgViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * EPG — time grid: channels × time 20:00–02:00 (SPEC §4.4), navigable
 * across nights (tonight + the next nights, up to 7 days — E02, kept:
 * the multi-day tabs are the approved post-model behaviour).
 * Model (stream-master.html, renderEPGMatrix / .epg-g-*):
 * - one cell per program, spanning its duration (E05);
 * - hour headers 20:00–02:00, each spanning two 30-min columns (E03);
 * - channel cell: 48dp logo + 18sp name + 15sp gold category (E04);
 * - current program: gold gradient + light-gold border + «HH – HH •
 *   EN DIRECT» line (E06); reminder: «• 🔔» in the time line +
 *   highlighted background (E08); title 17sp / time 13sp (E09).
 * A future program opens the action dialog (reminder / recording —
 * E07, kept: SelectorDialog is the approved post-model behaviour).
 * Channel logos: the model has NO logo URL field (Channel carries only
 * `code`, the 2-letter initials), so the 48dp logo box shows the code
 * on the gold gradient — no Coil load is possible.
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
    val winStart = state.windowStartMillis
    val winEnd = winStart + 6 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    // Program whose action dialog (reminder / record) is open.
    var programAction by remember { mutableStateOf<Pair<com.streammaster.tv.data.model.Channel, com.streammaster.tv.data.model.EpgProgram>?>(null) }

    // Grid geometry (model: 240px channel column + 12 flexible 30-min
    // columns). Content width ≈ 1920 − 300 (sidebar) − padding, so a
    // 100dp slot fits: 240 + 10 + 12×100 + 11×10 = 1560dp.
    val slotW: Dp = 100.dp
    val slotGap: Dp = 10.dp
    val chanW: Dp = 240.dp
    val gridW: Dp = slotW * 12 + slotGap * 11
    // UI scaling (2026-11-23): the grid is 1560dp wide — it overflows the
    // 1472dp content even at the 1920×1080 reference — so the hour header
    // and every channel row share one horizontal scroll state instead of
    // being clipped. The slots keep their exact 100dp design width.
    val gridScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // E01 — section header (.sec-head): gold bar + title + badge.
        // SectionHeader (components/Common.kt) has no badge slot, so the
        // header is built inline here, per the model.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.width(8.dp).height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoldGradient)
            )
            Text(
                text = stringResource(R.string.epg_title), fontSize = 32.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                color = GoldWhite
            )
            Text(
                text = stringResource(R.string.epg_evening_badge), fontSize = 16.sp,
                fontWeight = FontWeight.Black, color = GoldDeep,
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(GoldGradient)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
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
        // E03 — hour header: 20:00–02:00, each label spanning two 30-min columns.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(gridScroll).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(slotGap),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(modifier = Modifier.width(chanW))
            Box(modifier = Modifier.width(gridW).height(30.dp)) {
                for (h in 0 until 6) {
                    val hourStart = winStart + h * 60 * 60 * 1000L
                    Column(
                        modifier = Modifier
                            .offset(x = (slotW * 2 + slotGap) * h)
                            .width(slotW * 2 + slotGap)
                    ) {
                        Text(
                            text = timeFormat.format(Date(hourStart)),
                            fontSize = 15.sp, fontWeight = FontWeight.Black,
                            color = GoldSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                                .background(BorderGold)
                        )
                    }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.channels) { channel ->
                val programs by viewModel.programsFor(channel.id).collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(gridScroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(slotGap)
                ) {
                    // E04 — channel cell (.epg-g-ch): 48dp logo + name 18sp + category 15sp gold.
                    Row(
                        modifier = Modifier.width(chanW).height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x73000000))
                            .border(2.dp, Color(0x3DFFE89E), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GoldGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = channel.code, fontSize = 18.sp,
                                fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                                color = GoldDeep
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.customName ?: channel.name, fontSize = 18.sp,
                                fontWeight = FontWeight.Black, color = GoldWhite,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = channel.category, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, color = GoldBright,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // E05 — one cell per program, width = duration / 30min × slot width.
                    Box(modifier = Modifier.width(gridW).height(64.dp)) {
                        programs
                            .sortedBy { it.startMillis }
                            .forEach { program ->
                                val clipStart = max(program.startMillis, winStart)
                                val clipEnd = minOf(program.endMillis, winEnd)
                                if (clipEnd <= clipStart) return@forEach
                                val slotFloat = (clipStart - winStart) / (30 * 60 * 1000f)
                                val span = max(1, ((clipEnd - clipStart) / (30 * 60 * 1000f)).roundToInt())
                                val cellW = slotW * span + slotGap * (span - 1)
                                val x = (slotW + slotGap) * slotFloat
                                val isReminder = viewModel.isReminder(state.reminders, channel.id, program.title, program.startMillis)
                                val isScheduled = viewModel.isScheduled(scheduled, channel.id, program.title, program.startMillis)
                                val isSeriesRule = viewModel.isSeriesRule(state.seriesRules, channel.id, program.title)
                                val isCurrent = program.startMillis <= now && program.endMillis > now
                                val timeLine = buildString {
                                    append(timeFormat.format(Date(program.startMillis)))
                                    append(" – ")
                                    append(timeFormat.format(Date(program.endMillis)))
                                    if (isCurrent) append(" • ${stringResource(R.string.player_live)}")
                                    if (isReminder) append(" • 🔔")
                                    if (isScheduled) append(" • ●")
                                    if (isSeriesRule) append(" • ▣")
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(x = x)
                                        .width(cellW).height(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .then(
                                            when {
                                                // E06 — current program: gold gradient + light-gold border.
                                                isCurrent -> Modifier.background(
                                                    Brush.linearGradient(
                                                        0.0f to Color(0x4DE5C158),
                                                        1.0f to Color(0x1AE5C158)
                                                    )
                                                ).border(2.dp, GoldBright, RoundedCornerShape(16.dp))
                                                // E08 — reminded program: highlighted background.
                                                isReminder -> Modifier.background(Color(0x29E5C158))
                                                    .border(2.dp, Color(0xFFFFF8D6), RoundedCornerShape(16.dp))
                                                else -> Modifier.background(Color(0x73000000))
                                                    .border(2.dp, Color(0x3DFFE89E), RoundedCornerShape(16.dp))
                                            }
                                        )
                                        .tvFocusBorderless()
                                        .clickable {
                                            if (isCurrent) {
                                                navController.navigate(Routes.player("live", channel.id))
                                            } else {
                                                // Future program: choose reminder and/or recording.
                                                programAction = channel to program
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = timeLine, fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold, color = GoldBright,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = program.title, fontSize = 17.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCurrent) GoldDeep else GoldWhite,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
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
                        showToast(context, context.getString(if (isReminder) R.string.reminder_removed else R.string.reminder_set))
                    }
                    "once", "daily", "weekly" -> {
                        viewModel.scheduleRecording(
                            channel.id, channel.customName ?: channel.name, program, id
                        )
                        showToast(context, context.getString(R.string.recording_scheduled))
                    }
                    "series" -> {
                        viewModel.scheduleSeries(channel, program)
                        showToast(context, context.getString(R.string.series_recording_set))
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
