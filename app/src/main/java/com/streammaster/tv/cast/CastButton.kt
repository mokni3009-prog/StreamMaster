package com.streammaster.tv.cast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.screens.GhostButton
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted

/**
 * SPEC §4.46 — the ready-made Cast button for the Player builder.
 *
 * CONTRACT — drop into PlayerScreen where the other player controls live:
 * ```kotlin
 * CastButton(castManager = castManager) // remember { CastManager(context) } in the player
 * ```
 * Behaviour: shows the connected device name once casting, otherwise
 * "Cast"; tap opens a TV-remote-friendly device picker (list of
 * discovered Chromecast / Cast-enabled TVs). Picking a device connects
 * the Cast session; the Player builder then calls
 * `castManager.castUrl(currentUrl, title, isLive)` to move the current
 * stream to the receiver, and `castManager.play()/pause()/stop()` for
 * transport control. When Cast is unavailable on the device (no Play
 * services / manifest provider not registered yet) the button shows the
 * "unavailable" label and does nothing — it never crashes.
 *
 * The Player builder owns: creating ONE [CastManager] per player session,
 * calling [CastManager.release] when the player leaves, and deciding
 * whether local playback pauses while casting.
 *
 * @param castManager the session owner (created once with `remember`).
 * @param onCastStarted called with the chosen device once a session is
 *   connected, so the player can hand over the current URL.
 */
@Composable
fun CastButton(
    castManager: CastManager,
    modifier: Modifier = Modifier,
    onCastStarted: (CastManager.CastDeviceInfo) -> Unit = {}
) {
    val state by castManager.state.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }

    val label = when {
        !state.available -> stringResource(R.string.cast_unavailable)
        state.connectedDeviceName != null -> state.connectedDeviceName!!
        else -> stringResource(R.string.cast_button)
    }

    GhostButton(
        text = label,
        modifier = modifier,
        onClick = { if (state.available) pickerOpen = true }
    )

    if (pickerOpen) {
        CastDevicePickerDialog(
            devices = state.devices,
            connectedDeviceName = state.connectedDeviceName,
            isCasting = state.isCasting,
            isPlaying = state.isPlaying,
            onPick = { device ->
                pickerOpen = false
                castManager.selectDevice(device)
                onCastStarted(device)
            },
            onPlayPause = { if (state.isPlaying) castManager.pause() else castManager.play() },
            onDisconnect = { castManager.disconnect() },
            onDismiss = { pickerOpen = false }
        )
    }
}

/** TV-remote-friendly picker: discovered devices as focusable rows. */
@Composable
private fun CastDevicePickerDialog(
    devices: List<CastManager.CastDeviceInfo>,
    connectedDeviceName: String?,
    isCasting: Boolean,
    isPlaying: Boolean,
    onPick: (CastManager.CastDeviceInfo) -> Unit,
    onPlayPause: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    // A plain centred column (no ModalDialog dependency — the player
    // builder can swap this for the app's ConfirmDialog style if wanted).
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.cast_pick_device),
                color = GoldWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.cast_no_device),
                    color = TextMuted,
                    fontSize = 16.sp
                )
            } else {
                devices.forEach { device ->
                    val selected = device.name == connectedDeviceName
                    GoldButton(
                        text = if (selected) "● ${device.name}" else device.name,
                        outlined = !selected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onPick(device) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (isCasting) {
                GoldButton(
                    text = if (isPlaying) stringResource(R.string.cast_pause)
                    else stringResource(R.string.cast_play),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPlayPause
                )
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = stringResource(R.string.cast_disconnect),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDisconnect(); onDismiss() }
                )
            }
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = stringResource(R.string.cancel),
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss
            )
        }
    }
}
