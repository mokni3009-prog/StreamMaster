package com.streammaster.tv.ui.viewmodel

import com.streammaster.tv.data.model.Recording
import com.streammaster.tv.data.model.ScheduledRecording
import com.streammaster.tv.data.model.SeriesRecordingRule
import com.streammaster.tv.data.repository.RecordingRepository
import com.streammaster.tv.data.repository.ScheduledRecordingRepository
import com.streammaster.tv.data.repository.SeriesRecordingRepository
import com.streammaster.tv.data.work.RecordingScheduler
import com.streammaster.tv.player.RecordingManager
import com.streammaster.tv.player.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Enregistrements — instant DVR recordings (method 2): play, stop, delete.
 *  Plus scheduled recordings from the EPG (2026-11-22). */
@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val recordingManager: RecordingManager,
    private val scheduledRecordingRepository: ScheduledRecordingRepository,
    private val seriesRecordingRepository: SeriesRecordingRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    val recordings: StateFlow<List<Recording>> = recordingRepository.observeRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The running recording, if any (Idle otherwise). */
    val recordingState: StateFlow<RecordingState> = recordingManager.state

    /** Scheduled recordings (status = scheduled), soonest first. */
    val scheduled: StateFlow<List<ScheduledRecording>> = scheduledRecordingRepository.observeScheduled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Series-link recording rules (2026-11-22). */
    val seriesRules: StateFlow<List<SeriesRecordingRule>> = seriesRecordingRepository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(recording: Recording) = recordingManager.deleteRecording(recording)

    fun stopRecording() = recordingManager.stop()

    fun cancelScheduled(rec: ScheduledRecording) = viewModelScope.launch {
        RecordingScheduler.cancel(appContext, rec)
        scheduledRecordingRepository.delete(rec.id)
    }

    /** Deletes a series rule and cancels the airings it already scheduled
     *  (ids "sr-series-<ruleId>-<startMillis>"); recordings already done
     *  are kept. */
    fun cancelSeriesRule(rule: SeriesRecordingRule) = viewModelScope.launch {
        val pending = scheduledRecordingRepository.observeScheduled().first()
            .filter { it.id.startsWith("sr-series-${rule.id}-") }
        for (rec in pending) {
            RecordingScheduler.cancel(appContext, rec)
            scheduledRecordingRepository.delete(rec.id)
        }
        seriesRecordingRepository.delete(rule.id)
    }
}
