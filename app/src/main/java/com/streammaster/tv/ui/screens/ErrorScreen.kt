package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.viewmodel.ErrorViewModel
import com.streammaster.tv.ui.components.GoldButton

/**
 * Error screen — "Impossible de lire cette chaîne" with retry / back /
 * change-channel. Used as an overlay inside PlayerScreen (its route is not
 * in NavRoutes; the player shows it when PlayerManager.state.showError).
 * The `message` variant is the one the player uses; the navController-only
 * overload is kept for direct use with ErrorViewModel retry.
 */
@Composable
fun ErrorScreen(
    navController: NavHostController,
    message: String = "",
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onChangeChannel: (() -> Unit)? = null,
    viewModel: ErrorViewModel = hiltViewModel()
) {
    ThemedScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "⚠", fontSize = 64.sp, color = Gold)
            Text(
                text = stringResource(R.string.error_title),
                fontSize = 32.sp, fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (message.isNotBlank()) {
                Text(text = message, fontSize = 17.sp, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
            }
            Text(text = stringResource(R.string.error_hint), fontSize = 17.sp, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 32.dp)) {
                GoldButton(text = stringResource(R.string.retry)) {
                    onRetry?.invoke() ?: viewModel.retry()
                }
                GhostButton(text = stringResource(R.string.change_channel)) {
                    onChangeChannel?.invoke() ?: navController.navigate(Routes.LIVE)
                }
                GhostButton(text = stringResource(R.string.back)) {
                    onBack?.invoke() ?: navController.popBackStack()
                }
            }
        }
    }
}
