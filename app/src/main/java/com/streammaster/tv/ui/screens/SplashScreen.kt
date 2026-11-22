package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.delay

/** Splash — logo + "Version 1.2.0" (CONTRACT §6). App lock (2026-11-22):
 *  when enabled, the parental PIN must be entered before the app opens. */
@Composable
fun SplashScreen(
    navController: NavHostController,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val hasPlaylist by viewModel.hasPlaylist.collectAsState()
    val lockRequired by viewModel.lockRequired.collectAsState()
    val unlocked by viewModel.unlocked.collectAsState()
    val pinError by viewModel.pinError.collectAsState()
    LaunchedEffect(hasPlaylist, unlocked) {
        if (lockRequired && !unlocked) return@LaunchedEffect
        delay(1800)
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
            Text(
                text = stringResource(R.string.splash_tagline),
                fontSize = 22.sp, color = GoldWhite,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.app_version),
                fontSize = 18.sp, color = Gold,
                modifier = Modifier.padding(top = 32.dp)
            )
        }
    }
}
