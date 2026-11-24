package com.streammaster.tv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.ContentKind
import com.streammaster.tv.data.model.EpgProgram
import com.streammaster.tv.data.model.Reminder
import com.streammaster.tv.data.model.ScheduledRecording
import com.streammaster.tv.data.model.SeriesRecordingRule
import com.streammaster.tv.data.prefs.SettingsStore
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.ScheduledRecordingRepository
import com.streammaster.tv.data.repository.SeriesRecordingRepository
import com.streammaster.tv.data.util.TitleNormalizer
import com.streammaster.tv.data.work.RecordingScheduler
import com.streammaster.tv.data.work.SeriesLinkScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class EpgUiState(
    val channels: List<Channel> = emptyList(),
    /** channelId -> programs inside the selected night's window */
    val programsByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val windowStartMillis: Long = 0L,
    val selectedDayOffset: Int = 0,
    val maxDayOffset: Int = 1,
    /** Active series-link recording rules (2026-11-22). */
    val seriesRules: List<SeriesRecordingRule> = emptyList()
)

/** One VOD catalog entry matched to an EPG program title (2026-11-22). */
data class CatalogMatch(
    val title: String,
    val posterUrl: String?,
    val description: String,
    val rating: Double,
    val genre: String,
    val trailerUrl: String?,
    val kind: ContentKind,
    val refId: String
)

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val scheduledRecordingRepository: ScheduledRecordingRepository,
    private val seriesRecordingRepository: SeriesRecordingRepository,
    private val seriesLinkScanner: SeriesLinkScanner,
    @ApplicationContext private val appContext: Context,
    settingsStore: SettingsStore
) : ViewModel() {
    /** Navigable nights, from the EPG window setting: 24h = tonight +
     *  tomorrow night, 48h = +1 night, 7d = a full week.
     *  Keep in sync with EpgRepositoryImpl's window mapping. */
    private val daysFlow: StateFlow<Int> = settingsStore.settings
        .map { daysForWindow(it.epgWindow) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    private val _selectedDay = MutableStateFlow(0)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    /** Selected night's window: 20:00 -> 02:00 next day, 30-min slots (SPEC §4.4). */
    private val windowFlow: StateFlow<Pair<Long, Long>> = _selectedDay
        .map { windowForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), windowForDay(0))

    val state: StateFlow<EpgUiState> = combine(
        contentRepository.observeChannels(),
        contentRepository.observeReminders(),
        seriesRecordingRepository.observeRules(),
        _selectedDay,
        daysFlow
    ) { channels, reminders, rules, day, days ->
        // Programs are loaded per channel in the screen via programsFor(); this
        // state carries the window + reminders; per-channel flows avoid one giant query.
        EpgUiState(
            channels = channels.filter { !it.hidden },
            reminders = reminders,
            seriesRules = rules.filter { it.active },
            windowStartMillis = windowForDay(day).first,
            selectedDayOffset = day,
            maxDayOffset = (days - 1).coerceAtLeast(0)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EpgUiState(windowStartMillis = windowForDay(0).first))

    fun selectDay(offset: Int) {
        val max = (daysFlow.value - 1).coerceAtLeast(0)
        if (offset > max) {
            // M-04: the settings window may not have arrived yet
            // (daysFlow still holds its initial 2) — keep the request
            // pending instead of silently clamping it away; the
            // collector in init applies it once the window grows.
            pendingDayOffset = offset
            _selectedDay.value = max
        } else {
            pendingDayOffset = null
            _selectedDay.value = offset
        }
    }

    /** L-01: one cached StateFlow per channel, created once — the screen
     *  calls programsFor() for every visible row on every recomposition,
     *  and a fresh flow each time re-subscribes to Room and flickers. */
    private val programsFlows = mutableMapOf<String, StateFlow<List<EpgProgram>>>()
    fun programsFor(channelId: String): StateFlow<List<EpgProgram>> =
        programsFlows.getOrPut(channelId) {
            windowFlow.flatMapLatest { (start, end) ->
                contentRepository.observeEpg(channelId, start, end)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    /** Day tab tapped before the EPG window setting arrived (see M-04). */
    private var pendingDayOffset: Int? = null

    init {
        viewModelScope.launch {
            daysFlow.collect { days ->
                val pending = pendingDayOffset ?: return@collect
                val max = (days - 1).coerceAtLeast(0)
                if (pending <= max) {
                    pendingDayOffset = null
                    _selectedDay.value = pending
                }
            }
        }
    }

    /** Current program opens the channel; a future program toggles a reminder 🔔. */
    fun toggleReminder(channelId: String, program: EpgProgram, isReminder: Boolean) = viewModelScope.launch {
        if (isReminder) contentRepository.removeReminder(channelId, program.title, program.startMillis)
        else contentRepository.setReminder(channelId, program.title, program.startMillis)
    }

    /** Scheduled recordings (2026-11-22). */
    val scheduled: StateFlow<List<ScheduledRecording>> = scheduledRecordingRepository.observeScheduled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * EPG enrichment (2026-11-22): the provider's own VOD catalog, keyed by
     * normalized title, so a program airing on live TV shows the poster's
     * art, synopsis, rating and trailer when the same title exists in
     * Films/Séries. First entry wins; entries without a poster never
     * replace one with a poster.
     */
    val catalog: StateFlow<Map<String, CatalogMatch>> = combine(
        contentRepository.observeFilms(),
        contentRepository.observeSeries()
    ) { films, series ->
        val map = LinkedHashMap<String, CatalogMatch>()
        fun put(key: String, match: CatalogMatch) {
            if (key.isBlank()) return
            val old = map[key]
            if (old == null || (old.posterUrl.isNullOrBlank() && !match.posterUrl.isNullOrBlank())) {
                map[key] = match
            }
        }
        for (f in films) {
            put(
                TitleNormalizer.normalize(f.title),
                CatalogMatch(f.title, f.posterUrl, f.description, f.rating, f.genre, f.trailerUrl, ContentKind.FILM, f.id)
            )
        }
        for (s in series) {
            put(
                TitleNormalizer.normalize(s.title),
                CatalogMatch(s.title, s.posterUrl, s.description, s.rating, s.genre, s.trailerUrl, ContentKind.SERIES, s.id)
            )
        }
        map
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun scheduleRecording(channelId: String, channelName: String, program: EpgProgram, repeat: String) =
        viewModelScope.launch {
            val rec = ScheduledRecording(
                id = "sr-${channelId}-${program.startMillis}",
                channelId = channelId, channelName = channelName, programTitle = program.title,
                startMillis = program.startMillis, endMillis = program.endMillis,
                repeat = repeat, status = "scheduled"
            )
            scheduledRecordingRepository.save(rec)
            RecordingScheduler.schedule(appContext, rec)
        }

    fun cancelScheduledRecording(id: String) = viewModelScope.launch {
        val rec = scheduledRecordingRepository.getScheduled(id) ?: return@launch
        RecordingScheduler.cancel(appContext, rec)
        scheduledRecordingRepository.delete(id)
    }

    fun isScheduled(scheduled: List<ScheduledRecording>, channelId: String, title: String, startMillis: Long): Boolean =
        scheduled.any { it.channelId == channelId && it.programTitle == title && it.startMillis == startMillis }

    /** Series-link recording (2026-11-22): record every future airing of
     *  this program title on this channel. The scanner runs immediately so
     *  the upcoming airings are scheduled without waiting for the 6h tick. */
    fun scheduleSeries(channel: Channel, program: EpgProgram) = viewModelScope.launch {
        val normalized = TitleNormalizer.normalize(program.title)
        if (normalized.isBlank()) return@launch
        val rule = SeriesRecordingRule(
            id = "srl-${channel.id}-${normalized.hashCode()}",
            channelId = channel.id, channelName = channel.customName ?: channel.name,
            programTitle = program.title, normalizedTitle = normalized,
            createdMillis = System.currentTimeMillis(), active = true
        )
        seriesRecordingRepository.save(rule)
        seriesLinkScanner.scan()
    }

    fun cancelSeriesRule(id: String) = viewModelScope.launch {
        seriesRecordingRepository.delete(id)
    }

    fun isSeriesRule(rules: List<SeriesRecordingRule>, channelId: String, title: String): Boolean {
        val normalized = TitleNormalizer.normalize(title)
        return rules.any { it.channelId == channelId && it.normalizedTitle == normalized }
    }

    /** A reminder is one program on one day: the same title repeats nightly,
     *  so the start time is part of the identity. */
    fun isReminder(reminders: List<Reminder>, channelId: String, title: String, startMillis: Long): Boolean =
        reminders.any { it.channelId == channelId && it.programTitle == title && it.startMillis == startMillis }

    private fun windowForDay(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to start + 6 * 60 * 60 * 1000L
    }

    companion object {
        fun daysForWindow(window: String): Int = when (window) {
            "48h" -> 3
            "7d" -> 7
            else -> 2
        }
    }
}
