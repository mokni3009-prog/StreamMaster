package com.streammaster.tv.data.matchcenter

import android.content.Context
import com.streammaster.tv.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Goal alerts manager (2026-11-22): watches the `goalAlerts` setting and
 * starts/stops [GoalAlertService]. Opt-in — nothing runs while the setting
 * is off. Bound once from MainActivity, like RemoteServerManager.
 *
 * §4.36.8: the service also carries the pre-match smart alerts, which
 * are gated inside the service on `smartMatchAlerts` (default true) —
 * so the service must ALSO start when `smartMatchAlerts` is on even if
 * `goalAlerts` is off; otherwise the pre-match alerts never fire alone.
 */
@Singleton
class GoalAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val bus: GoalAlertBus
) {
    val alerts: SharedFlow<GoalAlert> = bus.alerts

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bound = false

    fun bind() {
        if (bound) return
        bound = true
        scope.launch {
            settingsRepository.settings
                .map { it.goalAlerts || it.smartMatchAlerts }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) GoalAlertService.start(context)
                    else GoalAlertService.stop(context)
                }
        }
    }
}
