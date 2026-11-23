package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.theme.Danger
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.ChannelTimerViewModel

/** Route for the channel timer screen (§4.58). Registered by the nav builder. */
const val CHANNEL_TIMER_ROUTE = "channel_timer"

/**
 * Channel timer UI (§4.58): pick a channel + a time, list the programmed
 * timers, cancel one. Scheduling and the automatic switch live in
 * ChannelTimerScheduler (settings builder).
 */
@Composable
fun ChannelTimerScreen(
    navController: NavHostController,
    viewModel: ChannelTimerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val timers by viewModel.timers.collectAsState()
    var showChannelPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeader(stringResource(R.string.ns_timer_title), stringResource(R.string.ns_timer_subtitle))

        CardSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ReadOnlyField(
                    stringResource(R.string.ns_timer_choose_channel),
                    state.selectedChannel?.name ?: ""
                ) { showChannelPicker = true }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Stepper(
                        label = stringResource(R.string.ns_timer_hour),
                        value = state.hour,
                        onMinus = { viewModel.setHour(state.hour - 1) },
                        onPlus = { viewModel.setHour(state.hour + 1) }
                    )
                    Text(text = ":", fontSize = 32.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                    Stepper(
                        label = stringResource(R.string.ns_timer_minute),
                        value = state.minute,
                        onMinus = { viewModel.setMinute(state.minute - 5) },
                        onPlus = { viewModel.setMinute(state.minute + 5) }
                    )
                }
                GoldButton(text = stringResource(R.string.ns_timer_schedule), modifier = Modifier.fillMaxWidth()) {
                    viewModel.schedule()
                }
                state.notice?.let { notice ->
                    val text = when {
                        notice == "pick_channel" -> stringResource(R.string.ns_timer_pick_channel_first)
                        notice == "cancelled" -> stringResource(R.string.ns_timer_cancelled)
                        notice.startsWith("scheduled:") -> {
                            val parts = notice.removePrefix("scheduled:").split(":")
                            // parts: [channelName, "HH", "MM"] — name may itself
                            // contain ":", so the time is the last two parts.
                            val time = parts.takeLast(2).joinToString(":")
                            val name = parts.dropLast(2).joinToString(":")
                            stringResource(R.string.ns_timer_scheduled, name, time)
                        }
                        else -> notice
                    }
                    Text(
                        text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = if (notice == "pick_channel") Danger else GoldBright
                    )
                }
            }
        }

        SectionHeader(stringResource(R.string.ns_timer_list))
        if (timers.isEmpty()) {
            Text(text = stringResource(R.string.ns_timer_empty), fontSize = 18.sp, color = TextMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(timers) { timer ->
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = timer.channelName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
                                Text(
                                    text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(timer.triggerMillis)),
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldBright
                                )
                            }
                            GhostButton(text = stringResource(R.string.ns_timer_cancel)) {
                                viewModel.cancel(timer.id)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChannelPicker) {
        SelectorDialog(
            title = stringResource(R.string.ns_timer_choose_channel),
            options = state.channels.map { SelectorOption(it.id, it.name, it.category) },
            selectedId = state.selectedChannel?.id,
            onSelect = { id ->
                state.channels.firstOrNull { it.id == id }?.let { viewModel.selectChannel(it) }
                showChannelPicker = false
            },
            onDismiss = { showChannelPicker = false }
        )
    }
}

/** − value + stepper (hour / minute): remote-friendly, no keyboard needed. */
@Composable
private fun Stepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(text = "−") { onMinus() }
            Text(
                text = "%02d".format(value),
                fontSize = 32.sp, fontWeight = FontWeight.Black, color = GoldWhite
            )
            GhostButton(text = "+") { onPlus() }
        }
    }
}
