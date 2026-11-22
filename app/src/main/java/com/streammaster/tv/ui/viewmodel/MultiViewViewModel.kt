package com.streammaster.tv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.repository.ContentRepository
import com.streammaster.tv.player.MultiViewManager
import com.streammaster.tv.player.MultiViewSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** MultiView — up to 4 live channels at once (2026-11-22). */
@HiltViewModel
class MultiViewViewModel @Inject constructor(
    private val multiViewManager: MultiViewManager,
    contentRepository: ContentRepository
) : ViewModel() {
    val slots: StateFlow<List<MultiViewSlot>> = multiViewManager.slots

    val channels: StateFlow<List<Channel>> = contentRepository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _focused = MutableStateFlow(0)
    val focused: StateFlow<Int> = _focused.asStateFlow()

    /** Slot whose channel picker is open, or null. */
    private val _pickerSlot = MutableStateFlow<Int?>(null)
    val pickerSlot: StateFlow<Int?> = _pickerSlot.asStateFlow()

    fun focusSlot(index: Int) {
        _focused.value = index
        multiViewManager.setFocused(index)
    }

    fun openPicker(slot: Int) { _pickerSlot.value = slot }
    fun closePicker() { _pickerSlot.value = null }

    fun pickChannel(channel: Channel) {
        val slot = _pickerSlot.value ?: return
        multiViewManager.setChannel(slot, channel)
        _pickerSlot.value = null
        focusSlot(slot)
    }

    fun clearSlot(slot: Int) {
        multiViewManager.clearChannel(slot)
        _pickerSlot.value = null
    }

    override fun onCleared() {
        multiViewManager.stopAll()
    }
}
