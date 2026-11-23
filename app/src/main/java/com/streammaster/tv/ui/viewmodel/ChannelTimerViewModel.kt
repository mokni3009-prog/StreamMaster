package com.streammaster.tv.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.db.ChannelTimerDao
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.work.ChannelTimer
import com.streammaster.tv.data.work.ChannelTimerScheduler
import com.streammaster.tv.data.work.toEntity
import com.streammaster.tv.data.work.toModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class ChannelTimerUiState(
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val hour: Int = 20,
    val minute: Int = 0,
    val notice: String? = null
)

/**
 * Channel timer UI (§4.58) — the screen part only.
 *
 * Uses the real [ChannelTimerScheduler] contract from `data/work/`:
 * [ChannelTimer] is `(id, channelId, channelName, triggerMillis)` and the
 * scheduler owns the exact AlarmManager alarm + the automatic switch via
 * the `streammaster://player/live/{channelId}` deep link. This ViewModel
 * converts the picked hour/minute into the next occurrence (today, or
 * tomorrow if the time already passed).
 *
 * Persistence: timers live in Room (`channel_timers`, via
 * [ChannelTimerDao] — provided by AppModule, same direct-DAO-injection
 * pattern as CleanupViewModel/DetailViewModel), NOT in memory, so they
 * survive an app restart and [ChannelTimerScheduler.observeTimers] /
 * BootReceiver find the rows. The list below is that DAO flow, mapped
 * through the scheduler's canonical [toModel] bridge; the displayed
 * model id is `"db:<rowId>"`, which is also the alarm identity.
 */
@HiltViewModel
class ChannelTimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    contentRepository: ContentRepository,
    private val channelTimerDao: ChannelTimerDao
) : ViewModel() {

    /**
     * Programmed timers, live from Room via the scheduler.
     * Ordering note: the injected [ContentRepository] singleton is built
     * (and its init{} runs ChannelTimerScheduler.install(db)) before this
     * ViewModel's properties initialise, so observeTimers() already sees
     * the installed database here instead of its empty fallback.
     */
    val timers: StateFlow<List<ChannelTimer>> =
        ChannelTimerScheduler.observeTimers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(ChannelTimerUiState())
    val state: StateFlow<ChannelTimerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            contentRepository.observeChannels().collect { channels ->
                _state.update { it.copy(channels = channels.filter { !it.hidden }) }
            }
        }
    }

    fun selectChannel(channel: Channel) = _state.update { it.copy(selectedChannel = channel, notice = null) }

    fun setHour(hour: Int) = _state.update { it.copy(hour = hour.coerceIn(0, 23)) }

    fun setMinute(minute: Int) = _state.update { it.copy(minute = minute.coerceIn(0, 59)) }

    /**
     * Schedules the timer; returns false when no channel is picked.
     * The Room write + alarm scheduling run in viewModelScope (DAO calls
     * are suspend); the "scheduled:..." notice is set only once both
     * succeeded, so the screen never confirms a timer that was not saved.
     */
    fun schedule(): Boolean {
        val s = _state.value
        val channel = s.selectedChannel ?: run {
            _state.update { it.copy(notice = "pick_channel") }
            return false
        }
        val hour = s.hour
        val minute = s.minute
        val timer = ChannelTimer(
            id = UUID.randomUUID().toString(),
            channelId = channel.id,
            channelName = channel.name,
            triggerMillis = nextTriggerMillis(hour, minute)
        )
        viewModelScope.launch {
            val scheduled = runCatching {
                // 1. Persist first: toEntity(UUID id) -> entity id 0, which
                // Room auto-generates on insert (upsert returns Unit).
                channelTimerDao.upsert(toEntity(timer))
                // Re-read the row to learn the generated id: the alarm MUST
                // be scheduled under the persisted "db:<rowId>" identity —
                // otherwise cancel() (which receives "db:<rowId>" from the
                // list above) and BootReceiver's re-arm would address a
                // different PendingIntent than the one scheduled here.
                // getAll() is ORDER BY triggerAtMillis ASC; lastOrNull
                // prefers the just-inserted row if the same channel+time
                // was already programmed once.
                val persisted = channelTimerDao.getAll()
                    .lastOrNull { it.channelId == timer.channelId && it.triggerAtMillis == timer.triggerMillis }
                    ?: return@runCatching false
                // 2. Schedule the alarm under the persisted identity.
                ChannelTimerScheduler.schedule(context, toModel(persisted))
                true
            }.getOrDefault(false)
            if (scheduled) {
                _state.update {
                    it.copy(notice = "scheduled:${channel.name}:${"%02d:%02d".format(hour, minute)}")
                }
            }
        }
        return true
    }

    /**
     * Cancels a timer: deletes its Room row first, then cancels the alarm
     * under the same id the list showed ("db:<rowId>"). A bare UUID id
     * (pre-persistence era, no row) skips the delete and still cancels
     * the alarm. The "cancelled" notice is set once both steps ran.
     */
    fun cancel(timerId: String) {
        viewModelScope.launch {
            timerId.removePrefix("db:").toLongOrNull()?.let { rowId ->
                runCatching { channelTimerDao.deleteById(rowId) }
            }
            ChannelTimerScheduler.cancelById(context, timerId)
            _state.update { it.copy(notice = "cancelled") }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** Next occurrence of hour:minute — today, or tomorrow if already past. */
    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
