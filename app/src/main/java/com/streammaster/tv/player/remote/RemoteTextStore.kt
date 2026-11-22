package com.streammaster.tv.player.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone web remote (2026-11-22): text typed on the phone's keyboard.
 * The Search screen's ViewModel collects this and appends the text to its
 * query, so the phone keyboard replaces the on-screen keyboard for search.
 * Texts sent while no search screen is open are dropped (replay = 0).
 */
@Singleton
class RemoteTextStore @Inject constructor() {
    private val _text = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
    val text: SharedFlow<String> = _text.asSharedFlow()

    fun send(text: String) {
        if (text.isNotEmpty()) _text.tryEmit(text)
    }
}
