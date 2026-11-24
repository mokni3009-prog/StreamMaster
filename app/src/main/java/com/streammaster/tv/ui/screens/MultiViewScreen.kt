package com.streammaster.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.player.MultiViewSlot
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.LiveViewModel
import com.streammaster.tv.ui.viewmodel.MultiViewViewModel

/** MultiView — up to 4 live channels at once (2026-11-22).
 *  Only the focused tile has sound. OK on a filled tile opens it
 *  fullscreen; OK on an empty tile opens the channel picker. */
@Composable
fun MultiViewScreen(
    navController: NavHostController,
    viewModel: MultiViewViewModel = hiltViewModel()
) {
    val slots by viewModel.slots.collectAsState()
    val focused by viewModel.focused.collectAsState()
    val pickerSlot by viewModel.pickerSlot.collectAsState()
    // Initial focus: the first tile (slot 0).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    // The picker is a same-window overlay: when it closes, the focused
    // picker item is disposed, so focus returns to the first tile —
    // otherwise the D-pad is left with no focus.
    LaunchedEffect(pickerSlot) {
        if (pickerSlot == null) firstFocus.requestFocus()
    }
    // Back closes the picker first, instead of leaving the screen.
    BackHandler(enabled = pickerSlot != null) { viewModel.closePicker() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.multiview_title),
                    fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
                )
                Text(text = stringResource(R.string.multiview_hint), fontSize = 15.sp, color = TextMuted)
            }
            for (row in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (col in 0..1) {
                        val index = row * 2 + col
                        val slot = slots.getOrNull(index)
                        MultiViewTile(
                            slot = slot,
                            focused = focused == index,
                            onFocus = { viewModel.focusSlot(index) },
                            onClick = {
                                val channel = slot?.channel
                                if (channel == null) viewModel.openPicker(index)
                                else navController.navigate(Routes.player("live", channel.id))
                            },
                            modifier = if (index == 0) Modifier.weight(1f).focusRequester(firstFocus) else Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (pickerSlot != null) {
            MultiViewPicker(
                slot = pickerSlot!!,
                filled = slots.getOrNull(pickerSlot!!)?.channel != null,
                onPick = { viewModel.pickChannel(it) },
                onClear = { viewModel.clearSlot(pickerSlot!!) },
                onClose = { viewModel.closePicker() }
            )
        }
    }
}

@Composable
private fun MultiViewTile(
    slot: MultiViewSlot?,
    focused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Gold else CardSurface,
                shape = RoundedCornerShape(16.dp)
            )
            .tvFocus()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val player = slot?.player
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = slot?.channel?.customName ?: slot?.channel?.name ?: "",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite,
                modifier = Modifier.align(Alignment.BottomStart)
                    .background(Color(0xCC000000)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "＋", fontSize = 40.sp, color = Gold)
                Text(text = stringResource(R.string.multiview_choose), fontSize = 16.sp, color = TextMuted)
            }
        }
    }
}

/** Channel picker overlay: bouquets -> channels (same data as Live). */
@Composable
private fun MultiViewPicker(
    slot: Int,
    filled: Boolean,
    onPick: (com.streammaster.tv.data.model.Channel) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val liveViewModel: LiveViewModel = hiltViewModel()
    val liveState by liveViewModel.state.collectAsState()
    // Initial focus: the Back button (first focusable of the picker).
    val pickerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { pickerFocus.requestFocus() }
    var selectedBouquetId by remember { mutableStateOf<String?>(null) }
    val channels = liveState.channels.filter {
        selectedBouquetId == null || it.bouquetId == selectedBouquetId
    }
    // Focus (2026-11-23, m-13): a real Dialog — its own window traps
    // focus inside the picker while open, so the D-pad can no longer
    // escape to the tiles behind it. Previously this was a plain Box
    // over the tiles and focus leaked through to them.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xE6000000)).padding(24.dp)
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.multiview_slot, slot + 1),
                    fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldWhite
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (filled) GhostButton(text = stringResource(R.string.clear_slot)) { onClear() }
                    GoldButton(text = stringResource(R.string.back), modifier = Modifier.focusRequester(pickerFocus)) { onClose() }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(modifier = Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(liveState.bouquets) { bouquet ->
                        val selected = bouquet.id == (selectedBouquetId ?: liveState.selectedBouquetId)
                        Text(
                            text = bouquet.name, fontSize = 18.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) GoldDeep else GoldWhite,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Gold else CardSurface)
                                .tvFocus().clickable { selectedBouquetId = bouquet.id; liveViewModel.selectBouquet(bouquet.id) }
                                .padding(12.dp)
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels) { channel ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardSurface)
                                .tvFocus().clickable { onPick(channel) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = channel.customName ?: channel.name, fontSize = 18.sp, color = GoldWhite)
                            Text(text = channel.customNum?.toString() ?: channel.num, fontSize = 16.sp, color = Gold)
                        }
                    }
                }
            }
        }
    }
    }
}
