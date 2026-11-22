package com.streammaster.tv.ui.screens

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.LiveViewModel
import kotlinx.coroutines.launch

/**
 * Live (Direct) — 3 columns: bouquets | channels | player window + channel
 * name + EPG below (CONTRACT §6). Digit keys open the channel by number
 * (custom number first, then built-in), with a temporary OSD.
 */
@Composable
fun LiveScreen(
    navController: NavHostController,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val epg by viewModel.epg.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var digitBuffer by remember { mutableStateOf("") }
    fun openChannel(channelId: String) {
        navController.navigate(Routes.player("live", channelId))
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val digit = when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'; KeyEvent.KEYCODE_2 -> '2'
                    KeyEvent.KEYCODE_3 -> '3'; KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
                    KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'; KeyEvent.KEYCODE_8 -> '8'
                    KeyEvent.KEYCODE_9 -> '9'; else -> null
                } ?: return@onPreviewKeyEvent false
                digitBuffer = (digitBuffer + digit).take(2)
                scope.launch {
                    kotlinx.coroutines.delay(1500)
                    val found = viewModel.resolveChannelNumber(digitBuffer)
                    if (found != null) openChannel(found.id)
                    else Toast.makeText(context, R.string.channel_not_found, Toast.LENGTH_SHORT).show()
                    digitBuffer = ""
                }
                true
            }
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Column 1 — bouquets
            Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                Text(text = stringResource(R.string.bouquets), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.bouquets) { bouquet ->
                        val selected = bouquet.id == state.selectedBouquetId
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) Gold else CardSurface)
                                .tvFocus()
                                .clickable { viewModel.selectBouquet(bouquet.id) }
                                .padding(16.dp)
                        ) {
                            Text(text = bouquet.name, fontSize = 18.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) GoldDeep else GoldWhite)
                        }
                    }
                }
            }
            // Column 2 — channels of the focused bouquet
            Column(modifier = Modifier.width(380.dp).fillMaxHeight()) {
                Text(text = stringResource(R.string.channels), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite, modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.channels) { channel ->
                        val selected = channel.id == state.selectedChannel?.id
                        val displayNum = channel.customNum?.toString() ?: channel.num
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) Gold else CardSurface)
                                .tvFocus()
                                .clickable { viewModel.selectChannel(channel.id) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = displayNum, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (selected) GoldDeep else Gold, modifier = Modifier.width(48.dp))
                            Text(text = channel.customName ?: channel.name, fontSize = 17.sp, color = if (selected) GoldDeep else GoldWhite, modifier = Modifier.weight(1f))
                            if (channel.customNum != null) {
                                Text(text = "N° ${channel.customNum}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDeep, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Gold).padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            if (channel.isFavorite) Text(text = "♥", color = if (selected) GoldDeep else Gold)
                        }
                    }
                }
            }
            // Column 3 — player window + channel name + EPG
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val channel = state.selectedChannel
                Box(
                    modifier = Modifier.fillMaxWidth().height(420.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(androidx.compose.ui.graphics.Color.hsv((channel?.hue ?: 44).toFloat(), 0.55f, 0.25f))
                        .border(2.dp, BorderGold, RoundedCornerShape(20.dp))
                        .tvFocus()
                        .clickable { channel?.let { openChannel(it.id) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = channel?.code ?: "—", fontSize = 64.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                    if (channel != null) {
                        Text(text = stringResource(R.string.live_badge), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldDeep, modifier = Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(10.dp)).background(Gold).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                if (channel != null) {
                    Text(text = channel.customName ?: channel.name, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite)
                    Text(text = "${stringResource(R.string.epg_now)}: ${channel.nowTitle} — ${channel.nowTime}", fontSize = 17.sp, color = TextMuted)
                    val next = epg.drop(1).firstOrNull()
                    if (next != null) {
                        Text(text = "${stringResource(R.string.epg_next)}: ${next.title}", fontSize = 16.sp, color = TextMuted)
                    } else if (channel.next.isNotEmpty()) {
                        Text(text = "${stringResource(R.string.epg_next)}: ${channel.next.first().second}", fontSize = 16.sp, color = TextMuted)
                    }
                }
                if (digitBuffer.isNotEmpty()) {
                    Text(text = stringResource(R.string.channel_osd, digitBuffer), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Gold)
                }
            }
        }
    }

}
