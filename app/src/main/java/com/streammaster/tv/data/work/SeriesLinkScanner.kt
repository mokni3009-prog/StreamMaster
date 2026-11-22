package com.streammaster.tv.data.work

import android.content.Context
import android.util.Log
import com.streammaster.tv.data.model.ScheduledRecording
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.data.repository.ScheduledRecordingRepository
import com.streammaster.tv.data.repository.SeriesRecordingRepository
import com.streammaster.tv.data.util.TitleNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Series-link recording (2026-11-22): for every active rule, scan the
 * channel's upcoming EPG (next 7 days) and schedule a one-shot recording
 * for each airing whose normalized title matches the rule. Already
 * scheduled airings are skipped by id. Runs from [SeriesLinkWorker]
 * (every 6h) and immediately after a rule is created.
 *
 * A rule never schedules more than 10 new airings per scan, so a daily
 * show cannot flood the alarm table.
 */
@Singleton
class SeriesLinkScanner @Inject constructor(
    private val seriesRecordingRepository: SeriesRecordingRepository,
    private val scheduledRecordingRepository: ScheduledRecordingRepository,
    private val contentRepository: ContentRepository,
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "SeriesLink"
        private const val MAX_NEW_PER_RULE = 10
        private const val WINDOW_DAYS = 7L
    }

    /** Returns the number of newly scheduled recordings. */
    suspend fun scan(): Int {
        val rules = try {
            seriesRecordingRepository.observeRules().first().filter { it.active }
        } catch (e: Exception) {
            Log.w(TAG, "scan: cannot load rules", e)
            return 0
        }
        if (rules.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val to = now + WINDOW_DAYS * 24 * 60 * 60 * 1000L
        var created = 0
        for (rule in rules) {
            val programs = try {
                contentRepository.observeEpg(rule.channelId, now, to).first()
            } catch (e: Exception) {
                Log.w(TAG, "scan: cannot load EPG for ${rule.channelId}", e)
                continue
            }
            val matches = programs
                .filter { it.startMillis > now }
                .filter { TitleNormalizer.normalize(it.title) == rule.normalizedTitle }
                .sortedBy { it.startMillis }
            var perRule = 0
            for (program in matches) {
                if (perRule >= MAX_NEW_PER_RULE) break
                val id = "sr-series-${rule.id}-${program.startMillis}"
                if (scheduledRecordingRepository.getScheduled(id) != null) continue
                val rec = ScheduledRecording(
                    id = id, channelId = rule.channelId, channelName = rule.channelName,
                    programTitle = program.title, startMillis = program.startMillis,
                    endMillis = program.endMillis, repeat = "once", status = "scheduled"
                )
                scheduledRecordingRepository.save(rec)
                RecordingScheduler.schedule(appContext, rec)
                perRule++
                created++
            }
        }
        if (created > 0) Log.i(TAG, "scan: scheduled $created series airing(s)")
        return created
    }
}
