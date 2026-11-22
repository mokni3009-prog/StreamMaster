package com.streammaster.tv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stall detection (GUIDE D1 / CONTRACT §5).
 *
 * The frozen picture usually produces NO error, so a position watchdog is
 * required on top of `onPlayerError`: if the playhead does not advance for
 * [stallTimeoutMs] while the player is in a playing/buffering state, the
 * player is treated as stalled and [onStall] fires.
 *
 * The detector is engine-agnostic: the caller supplies the current position
 * and whether the player is currently supposed to be advancing. It works for
 * both ExoPlayer (position from `ExoPlayer.currentPosition`) and VLC
 * (position from `MediaPlayer.time`).
 *
 * After firing once it rearms automatically only after the position advances
 * again, so a single stall produces a single recovery attempt.
 */
class StallDetector(
    private val scope: CoroutineScope,
    private val stallTimeoutMs: Long = DEFAULT_STALL_TIMEOUT_MS,
    private val onStall: () -> Unit
) {
    companion object {
        /** CONTRACT §5: 12s without progress while playing/buffering = stall. */
        const val DEFAULT_STALL_TIMEOUT_MS = 12_000L
        private const val CHECK_INTERVAL_MS = 1_000L
    }

    private var tickerJob: Job? = null
    private var lastPositionMs: Long = -1L
    private var stalledForMs: Long = 0L
    private var firedForCurrentStall = false

    /**
     * Starts watching. [positionProvider] returns the current playhead in ms
     * (C.TIME_UNSET / negative values are treated as "no progress info" and
     * ignored). [activeProvider] returns true while the player is in a state
     * where the playhead is expected to advance (playing or buffering).
     */
    fun start(
        positionProvider: () -> Long,
        activeProvider: () -> Boolean
    ) {
        stop()
        lastPositionMs = -1L
        stalledForMs = 0L
        firedForCurrentStall = false
        tickerJob = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (!activeProvider()) {
                    stalledForMs = 0L
                    firedForCurrentStall = false
                    lastPositionMs = positionProvider()
                    continue
                }
                val position = positionProvider()
                if (position < 0) continue
                if (position == lastPositionMs) {
                    stalledForMs += CHECK_INTERVAL_MS
                    if (stalledForMs >= stallTimeoutMs && !firedForCurrentStall) {
                        firedForCurrentStall = true
                        onStall()
                    }
                } else {
                    lastPositionMs = position
                    stalledForMs = 0L
                    firedForCurrentStall = false
                }
            }
        }
    }

    /** Resets the watchdog without stopping the ticker (e.g. after a seek). */
    fun reset() {
        lastPositionMs = -1L
        stalledForMs = 0L
        firedForCurrentStall = false
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        lastPositionMs = -1L
        stalledForMs = 0L
        firedForCurrentStall = false
    }
}
