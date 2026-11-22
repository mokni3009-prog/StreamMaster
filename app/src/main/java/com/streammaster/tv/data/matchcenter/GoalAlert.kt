package com.streammaster.tv.data.matchcenter

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One announced goal (2026-11-22): everything the popup and the system
 * notification show — who scored, for which team, in which minute, and
 * the match with its current score.
 */
data class GoalAlert(
    val key: String,
    val leagueName: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val scorer: String,
    val scorerTeam: String,
    val minute: String
)

/** In-app bus: the alert service emits, the Shell overlay collects. */
@Singleton
class GoalAlertBus @Inject constructor() {
    private val _alerts = MutableSharedFlow<GoalAlert>(replay = 0, extraBufferCapacity = 16)
    val alerts: SharedFlow<GoalAlert> = _alerts.asSharedFlow()

    fun emit(alert: GoalAlert) {
        _alerts.tryEmit(alert)
    }
}
