package com.streammaster.tv.data.matchcenter

/**
 * Whether the app's activity is currently in the foreground (2026-11-22).
 * Set by MainActivity; the goal-alert service uses it to decide between
 * the in-app popup (foreground) and a system notification (background).
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
}
