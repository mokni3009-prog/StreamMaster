package com.streammaster.tv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.matchday.DayFeed
import com.streammaster.tv.data.matchday.DayMatch
import com.streammaster.tv.data.matchday.MatchDayApi
import com.streammaster.tv.data.matchday.MatchDayFeed
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.model.Reminder
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Which day tab is shown. */
enum class MatchDayTab { TODAY, TOMORROW }

/**
 * Match Day (§4.35): downloads the static `match-day.json` produced by
 * the external script (today + tomorrow), matches each broadcast channel
 * against the user's subscriptions (normalized name), and drives the
 * live countdown. "Watch" opens the matched channel in the player;
 * the bell schedules a reminder 15 minutes before kickoff.
 */
@HiltViewModel
class MatchDayViewModel @Inject constructor(
    private val api: MatchDayApi,
    private val contentRepository: ContentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _feed = MutableStateFlow<MatchDayFeed?>(null)
    val feed: StateFlow<MatchDayFeed?> = _feed.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private val _tab = MutableStateFlow(MatchDayTab.TODAY)
    val tab: StateFlow<MatchDayTab> = _tab.asStateFlow()

    /** Ticks every second while the screen is alive — drives the countdown. */
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    /** One-shot message shown under the header (e.g. channel not found). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Match ids the user already set a reminder for (this session). */
    private val _remindedIds = MutableStateFlow<Set<String>>(emptySet())
    val remindedIds: StateFlow<Set<String>> = _remindedIds.asStateFlow()

    /** All live channels of the active playlist, for name matching. */
    private var channels: List<Channel> = emptyList()

    private var tickerJob: Job? = null

    init {
        refresh()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                _nowMillis.value = System.currentTimeMillis()
            }
        }
    }

    fun selectTab(tab: MatchDayTab) {
        _tab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = false
            runCatching { contentRepository.observeChannels().first() }
                .onSuccess { channels = it }
            val result = runCatching { api.fetch() }.getOrNull()
            if (result == null) {
                _error.value = true
            } else {
                _feed.value = result
            }
            _loading.value = false
        }
    }

    fun dayFeed(tab: MatchDayTab, feed: MatchDayFeed?): DayFeed? = when (tab) {
        MatchDayTab.TODAY -> feed?.today
        MatchDayTab.TOMORROW -> feed?.tomorrow
    }

    /**
     * Finds the subscription channel broadcasting this match: the first
     * channel in `match.channels` (script priority order) that exists in
     * the user's playlists, matched on the normalized name. Null when
     * none of the broadcast channels is in the subscriptions.
     */
    fun channelFor(match: DayMatch): Channel? {
        if (channels.isEmpty()) return null
        for (broadcast in match.channels) {
            val wanted = normalize(broadcast)
            if (wanted.isBlank()) continue
            // 1. Exact normalized name.
            channels.firstOrNull { normalize(it.displayName()) == wanted }?.let { return it }
            // 2. One contains the other (e.g. "beinsports1" vs "beinsports1hd").
            channels.firstOrNull {
                val have = normalize(it.displayName())
                have.isNotEmpty() && (have.contains(wanted) || wanted.contains(have))
            }?.let { return it }
        }
        return null
    }

    /** The channel's display name: custom name when the user renamed it. */
    private fun Channel.displayName(): String =
        customName?.takeIf { it.isNotBlank() } ?: name

    /**
     * Reminder 15 minutes before kickoff, via the existing ReminderScheduler
     * (same alarm + notification + deep link as program reminders).
     * Needs a matched channel: without one the notification could not
     * open anything. Returns false when no channel was found.
     */
    fun scheduleReminder(match: DayMatch): Boolean {
        val channel = channelFor(match) ?: run {
            _message.value = NEEDS_CHANNEL
            return false
        }
        if (match.kickoffMillis <= 0L || match.kickoffMillis <= System.currentTimeMillis()) {
            return false
        }
        val reminder = Reminder(
            channelId = channel.id,
            programTitle = "${match.homeTeam} - ${match.awayTeam}",
            startMillis = match.kickoffMillis - 15 * 60 * 1000L
        )
        ReminderScheduler.scheduleReminder(context, reminder)
        _remindedIds.value = _remindedIds.value + match.id
        _message.value = REMINDER_SET
        return true
    }

    /** Called by the screen when "Watch" is tapped without a channel. */
    fun showNoChannelMessage() {
        _message.value = NO_CHANNEL_WATCH
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** Message keys — the screen resolves them to localized strings. */
        const val NEEDS_CHANNEL = "needs_channel"
        const val NO_CHANNEL_WATCH = "no_channel_watch"
        const val REMINDER_SET = "reminder_set"
    }
}

/**
 * Normalizes a channel name for matching: lowercase, drop separators,
 * drop quality tokens (hd / fhd / 4k / uhd / sd / tv), strip a leading or
 * trailing quality marker glued to a token ("hd1" -> "1"), and unify
 * "beinsport" / "beinsports". So "beIN SPORTS 1", "beIN Sports HD1" and
 * "Bein Sport 1 HD" all normalize to "beinsports1".
 */
fun normalizeChannelName(raw: String): String {
    val qualityTokens = setOf("hd", "fhd", "4k", "uhd", "sd", "tv", "channel", "chaine")
    val tokens = raw.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .mapNotNull { token ->
            var t = token
            if (t.isBlank()) return@mapNotNull null
            if (t in qualityTokens) return@mapNotNull null
            for (q in listOf("fhd", "uhd", "4k", "hd")) {
                if (t.startsWith(q) && t.length > q.length) { t = t.removePrefix(q); break }
            }
            for (q in listOf("fhd", "uhd", "4k", "hd")) {
                if (t.endsWith(q) && t.length > q.length) { t = t.removeSuffix(q); break }
            }
            t.ifBlank { null }
        }
    return tokens.joinToString("").replace("beinsport", "beinsports")
}

/** Short alias used inside the ViewModel. */
private fun normalize(raw: String): String = normalizeChannelName(raw)
