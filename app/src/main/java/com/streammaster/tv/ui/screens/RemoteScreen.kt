package com.streammaster.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.RemoteViewModel

/** Phone web remote — pairing screen (local Wi-Fi + QR code, 2026-11-22). */
@Composable
fun RemoteScreen(
    navController: NavHostController,
    viewModel: RemoteViewModel = hiltViewModel()
) {
    val enabled by viewModel.remoteEnabled.collectAsState()
    val code by viewModel.remoteCode.collectAsState()
    val running by viewModel.running.collectAsState()
    val ip by viewModel.localIp.collectAsState()
    val port by viewModel.port.collectAsState()
    val url by viewModel.pairingUrl.collectAsState()
    val qr by viewModel.qrBitmap.collectAsState()
    // Initial focus: the enable/disable button (first focusable).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.remote_title),
                fontSize = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite
            )
            GoldButton(
                text = stringResource(if (enabled) R.string.remote_disable else R.string.remote_enable),
                modifier = Modifier.focusRequester(firstFocus)
            ) { viewModel.setEnabled(!enabled) }
        }
        if (!enabled) {
            Text(
                text = stringResource(R.string.remote_hint),
                fontSize = 18.sp, color = TextMuted
            )
        } else {
            Text(
                text = stringResource(if (running) R.string.remote_running else R.string.remote_stopped),
                fontSize = 18.sp, color = if (running) Gold else TextMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = qr
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.remote_scan),
                        modifier = Modifier.size(340.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.remote_scan),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                    )
                    if (url.isNotEmpty()) {
                        Text(
                            text = "${stringResource(R.string.remote_url)} : $url",
                            fontSize = 18.sp, color = TextMuted
                        )
                    }
                    if (code.isNotEmpty()) {
                        Text(
                            text = "${stringResource(R.string.remote_code)} : $code",
                            fontSize = 28.sp, fontWeight = FontWeight.Black, color = Gold
                        )
                    }
                    if (ip.isNotEmpty() && port != 0) {
                        Text(
                            text = "$ip:$port",
                            fontSize = 18.sp, color = TextMuted
                        )
                    }
                    GoldButton(text = stringResource(R.string.remote_new_code), outlined = true) {
                        viewModel.regenerateCode()
                    }
                }
            }
        }
    }
}
