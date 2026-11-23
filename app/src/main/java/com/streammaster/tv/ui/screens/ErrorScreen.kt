package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.ErrorViewModel

/**
 * Error dialog — dlg-error from the model (stream-master.html s.1687-1700):
 * a dialog over the player, NOT a fullscreen screen (PL18 + K09).
 * Gold danger icon 100dp + error code ERR-STREAM-503 + hint +
 * primary "Réessayer" + "Fermer". Used as an overlay inside PlayerScreen
 * (its route is not in NavRoutes; the player shows it when
 * PlayerManager.state.showError). The `message` variant is the one the
 * player uses; the no-arg retry falls back to ErrorViewModel.retry().
 */
@Composable
fun ErrorScreen(
    message: String = "",
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    viewModel: ErrorViewModel = hiltViewModel()
) {
    // Initial focus: the retry button — without it this Dialog opens with
    // no focused element and the first D-pad press is lost.
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Dialog(
        onDismissRequest = { onBack?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.width(720.dp)
                .background(
                    Brush.linearGradient(
                        0.0f to Color(0xFA221807),
                        1.0f to Color(0xFA0C0903)
                    ),
                    RoundedCornerShape(30.dp)
                )
                .border(2.5.dp, BorderStrong, RoundedCornerShape(30.dp))
                .padding(horizontal = 46.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(100.dp)
                    .background(GoldGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "!", fontSize = 50.sp, fontWeight = FontWeight.Black, color = GoldDeep)
            }
            Text(
                text = stringResource(R.string.error_title),
                fontSize = 28.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif, color = GoldWhite,
                textAlign = TextAlign.Center
            )
            if (message.isNotBlank()) {
                Text(
                    text = message, fontSize = 17.sp, color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = stringResource(R.string.error_code),
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GoldBright,
                letterSpacing = 2.sp, textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.error_hint),
                fontSize = 17.sp, color = TextMuted,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GoldButton(text = stringResource(R.string.retry), modifier = Modifier.focusRequester(retryFocus)) {
                    onRetry?.invoke() ?: viewModel.retry()
                }
                GhostButton(text = stringResource(R.string.close)) {
                    onBack?.invoke()
                }
            }
        }
    }
}
