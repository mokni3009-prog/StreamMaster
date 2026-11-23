package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.GhostButton
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.delay

/** Splash — logo + loader bar + status + "Version 1.2.0" + skip button
 *  (prototype screen-boot). App lock (2026-11-22): when enabled, the
 *  parental PIN must be entered before the app opens. */
@Composable
fun SplashScreen(
    navController: NavHostController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val hasPlaylist by viewModel.hasPlaylist.collectAsState()
    val lockRequired by viewModel.lockRequired.collectAsState()
    val unlocked by viewModel.unlocked.collectAsState()
    val pinError by viewModel.pinError.collectAsState()
    var skipRequested by remember { mutableStateOf(false) }
    // Initial focus: the skip button (only when no app lock — the PIN
    // dialog owns focus then).
    val skipFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!lockRequired) skipFocus.requestFocus() }
    LaunchedEffect(hasPlaylist, unlocked, skipRequested) {
        if (lockRequired && !unlocked) return@LaunchedEffect
        if (!skipRequested) delay(1800)
        navController.navigate(if (hasPlaylist) Routes.LOADING else Routes.LOGIN) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }
    if (lockRequired && !unlocked) {
        com.streammaster.tv.ui.components.PinDialog(
            title = stringResource(R.string.app_lock_title),
            errorMessage = if (pinError) stringResource(R.string.pin_error) else null,
            onComplete = { viewModel.checkPin(it) },
            onCancel = { /* locked: no way out except the PIN */ }
        )
    }
    ThemedScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                fontSize = 72.sp, letterSpacing = 1.2.sp, color = Gold
            )
            // Prototype loader bar (#boot-fill).
            LinearProgressIndicator(
                modifier = Modifier.width(420.dp).height(6.dp).padding(top = 16.dp),
                color = Gold,
                trackColor = Color(0xFF3A2F16)
            )
            Text(
                text = stringResource(R.string.splash_status),
                fontSize = 18.sp, color = TextMuted,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.app_version),
                fontSize = 18.sp, color = Gold,
                modifier = Modifier.padding(top = 32.dp)
            )
            GhostButton(
                text = stringResource(R.string.splash_skip),
                modifier = Modifier.padding(top = 24.dp).focusRequester(skipFocus)
            ) { skipRequested = true }
        }
    }
}
