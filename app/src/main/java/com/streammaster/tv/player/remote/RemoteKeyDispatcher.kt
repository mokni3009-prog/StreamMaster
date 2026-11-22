package com.streammaster.tv.player.remote

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Phone web remote (2026-11-22): replays the phone's navigation keys as
 * real Android key events on the current activity, so focus movement, OK
 * and Back behave exactly like the physical remote. [MainActivity] keeps
 * [activity] up to date; volume is NOT sent here (AudioManager, see
 * [RemoteCommandHandler]).
 */
object RemoteKeyDispatcher {
    @Volatile
    var activity: Activity? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun dispatchKey(keyCode: Int) {
        val target = activity ?: return
        mainHandler.post {
            target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            target.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }
}
