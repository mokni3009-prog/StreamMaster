package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.LoadingIndicator
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.viewmodel.LoadingViewModel
import kotlinx.coroutines.delay

/**
 * Loading — prototype screen-sync: title "Synchronisation : <playlist>",
 * 3 sync steps (live channels & EPG, VOD films, series & episodes), each
 * with its own progress bar and status ("En attente" / "Terminé").
 * Navigates home once any content is ready.
 */
@Composable
fun LoadingScreen(
    navController: NavHostController,
    viewModel: LoadingViewModel = hiltViewModel()
) {
    val isReady by viewModel.isReady.collectAsState()
    val steps by viewModel.stepsReady.collectAsState()
    val playlistName by viewModel.playlistName.collectAsState()
    LaunchedEffect(isReady) {
        if (isReady) {
            delay(600)
            navController.navigate(Routes.HOME) { popUpTo(Routes.LOADING) { inclusive = true } }
        }
    }
    ThemedScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingIndicator(null)
            Text(
                text = if (playlistName.isNotBlank()) stringResource(R.string.sync_title, playlistName)
                else stringResource(R.string.loading_title),
                fontSize = 30.sp, fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, color = GoldWhite,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = stringResource(R.string.sync_subtitle),
                fontSize = 18.sp, color = TextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
            Column(
                modifier = Modifier.width(640.dp).padding(top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                SyncStep(
                    labelRes = R.string.sync_step_live,
                    done = steps.first
                )
                SyncStep(
                    labelRes = R.string.sync_step_films,
                    done = steps.second
                )
                SyncStep(
                    labelRes = R.string.sync_step_series,
                    done = steps.third
                )
            }
        }
    }
}

/** One sync step row: label + status + progress bar. */
@Composable
private fun SyncStep(labelRes: Int, done: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(labelRes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
            Text(
                text = if (done) stringResource(R.string.sync_done) else stringResource(R.string.sync_waiting),
                fontSize = 16.sp, color = if (done) Gold else TextMuted
            )
        }
        LinearProgressIndicator(
            progress = if (done) 1f else 0f,
            modifier = Modifier.fillMaxWidth(),
            color = Gold,
            trackColor = Color(0xFF3A2F16)
        )
    }
}
