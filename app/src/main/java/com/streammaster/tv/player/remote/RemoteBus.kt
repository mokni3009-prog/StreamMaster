package com.streammaster.tv.player.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone web remote (2026-11-22): commands sent by the phone that need the
 * Compose UI to act (navigation, player actions). The embedded HTTP server
 * only emits into this bus; [com.streammaster.tv.ui.screens.Shell] handles
 * the navigation commands and
 * [com.streammaster.tv.ui.screens.PlayerScreen] the player commands.
 * DPAD / OK / Back keys do NOT go through here — they are dispatched as
 * real Android key events by [RemoteKeyDispatcher].
 */
sealed class RemoteCommand {
    object TogglePlayPause : RemoteCommand()
    object ChannelUp : RemoteCommand()
    object ChannelDown : RemoteCommand()
    object GoLive : RemoteCommand()
    object ToggleRecording : RemoteCommand()
    object OpenLists : RemoteCommand()
    object GoHome : RemoteCommand()
    object GoSearch : RemoteCommand()
    // Full physical-remote buttons (2026-11-22).
    data class Digit(val digit: Char) : RemoteCommand()
    object GoEpg : RemoteCommand()
    object GoSettings : RemoteCommand()
    object GoDirect : RemoteCommand()
    object GoRecordings : RemoteCommand()
    object GoFavoris : RemoteCommand()
    object GoMatchCenter : RemoteCommand()
    object OpenAudio : RemoteCommand()
    object OpenSubtitles : RemoteCommand()
    object Rewind : RemoteCommand()
    object Forward : RemoteCommand()
    object StopPlayback : RemoteCommand()
    object PrevChannel : RemoteCommand()
    object ShowInfo : RemoteCommand()
}

@Singleton
class RemoteBus @Inject constructor() {
    private val _commands = MutableSharedFlow<RemoteCommand>(replay = 0, extraBufferCapacity = 16)
    val commands: SharedFlow<RemoteCommand> = _commands.asSharedFlow()

    fun send(command: RemoteCommand) {
        _commands.tryEmit(command)
    }
}
