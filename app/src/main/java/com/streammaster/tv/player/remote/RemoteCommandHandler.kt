package com.streammaster.tv.player.remote

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone web remote (2026-11-22): routes one key/action name from the phone
 * page to the right local mechanism.
 * - Navigation keys (up/down/left/right/ok/back) → real key events via
 *   [RemoteKeyDispatcher], so Compose focus behaves like the physical remote.
 * - Volume / mute → AudioManager directly (dispatching volume keys to the
 *   activity is OEM-dependent).
 * - Player & navigation actions → [RemoteBus] for the PlayerScreen / Shell
 *   to handle (they own the ViewModels and the NavController).
 * - Text → [RemoteTextStore] for the Search screen.
 */
@Singleton
class RemoteCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bus: RemoteBus,
    private val textStore: RemoteTextStore
) {
    fun onKey(key: String) {
        // Digits 0-9: channel-number entry in the player.
        if (key.length == 1 && key[0].isDigit()) {
            bus.send(RemoteCommand.Digit(key[0]))
            return
        }
        when (key) {
            "up" -> RemoteKeyDispatcher.dispatchKey(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> RemoteKeyDispatcher.dispatchKey(KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> RemoteKeyDispatcher.dispatchKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> RemoteKeyDispatcher.dispatchKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "ok" -> RemoteKeyDispatcher.dispatchKey(KeyEvent.KEYCODE_DPAD_CENTER)
            "back" -> RemoteKeyDispatcher.dispatchKey(KeyEvent.KEYCODE_BACK)
            "playpause" -> bus.send(RemoteCommand.TogglePlayPause)
            "chup" -> bus.send(RemoteCommand.ChannelUp)
            "chdown" -> bus.send(RemoteCommand.ChannelDown)
            "live" -> bus.send(RemoteCommand.GoLive)
            "rec" -> bus.send(RemoteCommand.ToggleRecording)
            "lists" -> bus.send(RemoteCommand.OpenLists)
            "home" -> bus.send(RemoteCommand.GoHome)
            "search" -> bus.send(RemoteCommand.GoSearch)
            "guide" -> bus.send(RemoteCommand.GoEpg)
            "menu" -> bus.send(RemoteCommand.GoSettings)
            "tv" -> bus.send(RemoteCommand.GoDirect)
            "audio" -> bus.send(RemoteCommand.OpenAudio)
            "subs" -> bus.send(RemoteCommand.OpenSubtitles)
            "rew" -> bus.send(RemoteCommand.Rewind)
            "ff" -> bus.send(RemoteCommand.Forward)
            "stop" -> bus.send(RemoteCommand.StopPlayback)
            "prev" -> bus.send(RemoteCommand.PrevChannel)
            "next" -> bus.send(RemoteCommand.ChannelUp)
            "info" -> bus.send(RemoteCommand.ShowInfo)
            "red" -> bus.send(RemoteCommand.GoRecordings)
            "green" -> bus.send(RemoteCommand.GoFavoris)
            "yellow" -> bus.send(RemoteCommand.GoEpg)
            "blue" -> bus.send(RemoteCommand.GoMatchCenter)
            "power" -> goTvHome()
            "volup" -> adjustVolume(AudioManager.ADJUST_RAISE)
            "voldown" -> adjustVolume(AudioManager.ADJUST_LOWER)
            "mute" -> toggleMute()
            else -> Unit
        }
    }

    /**
     * Power button: an app cannot switch the TV off, so this leaves the
     * app for the Android TV launcher — the closest equivalent.
     */
    private fun goTvHome() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun onText(text: String) {
        textStore.send(text)
    }

    private fun audioManager(): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun adjustVolume(direction: Int) {
        audioManager().adjustStreamVolume(
            AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI
        )
    }

    @Suppress("DEPRECATION")
    private fun toggleMute() {
        val am = audioManager()
        if (Build.VERSION.SDK_INT >= 23) {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
        } else {
            am.setStreamMute(AudioManager.STREAM_MUSIC, !am.isStreamMute(AudioManager.STREAM_MUSIC))
        }
    }
}
