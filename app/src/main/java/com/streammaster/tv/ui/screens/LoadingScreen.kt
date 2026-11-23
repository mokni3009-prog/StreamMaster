package com.streammaster.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.api.SyncProgressBus
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.theme.TitleFontFamily
import com.streammaster.tv.ui.viewmodel.LoadingViewModel
import kotlinx.coroutines.delay

/** Model .sync-card background: linear-gradient(165deg, rgba(30,22,6,.96), rgba(11,8,3,.98)) — diagonal approximation via the stops overload. */
private val SyncCardGradient: Brush = Brush.linearGradient(
    0.0f to Color(0xF51E1606),
    1.0f to Color(0xFA0B0803)
)

/**
 * Loading — model #screen-sync .sync-card: 880dp wide, padding 48×60dp,
 * 36dp radius, gradient, 2px strong border, shadow + gold glow.
 * 74dp gold spinner (6dp stroke), title 34sp Black + description 20sp
 * gold-bright; sync steps as dark cards (black 55%, 1.5px border, 20dp
 * radius, 18×24dp padding) with a 12dp full-width progress bar.
 * LD05 / R-M3: live per-step percentage — the active step shows the real
 * sync progress (0 % -> 100 %) from SyncProgressBus, written by the API
 * layer as each fetch stage completes (Xtream's 6 sequential calls +
 * per-series info loop, Stalker handshake/genres/channels, M3U per-line
 * parsing); a finished step shows "100 %", a pending step "En attente".
 * The bar is determinate on the active step. EPG sync is a separate
 * worker and is not part of this percentage.
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
    // R-M3: live phase-based progress from the API layer (0 % at rest,
    // like the model's sp1-txt "0 %"); the done state still comes from
    // the DB via stepsReady, so a step only reads "100 %" once stored.
    val syncProgress by SyncProgressBus.state.collectAsState()
    LaunchedEffect(isReady) {
        if (isReady) {
            delay(600)
            navController.navigate(Routes.HOME) { popUpTo(Routes.LOADING) { inclusive = true } }
        }
    }
    val doneList = listOf(steps.first, steps.second, steps.third)
    // First unfinished step is the active one; -1 when all are done.
    val activeIndex = doneList.indexOfFirst { !it }
    ThemedScreen {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 880.dp).fillMaxWidth()
                    .shadow(
                        35.dp,
                        RoundedCornerShape(36.dp),
                        ambientColor = Color.Black,
                        spotColor = Color(0xFFE5C158)
                    )
                    .background(SyncCardGradient, RoundedCornerShape(36.dp))
                    .border(2.dp, BorderStrong, RoundedCornerShape(36.dp))
                    .padding(horizontal = 60.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                // Spinner + title + description (model .sync-top).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(74.dp),
                        strokeWidth = 6.dp,
                        color = GoldBright
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (playlistName.isNotBlank()) stringResource(R.string.sync_title, playlistName)
                            else stringResource(R.string.loading_title),
                            fontSize = 34.sp, fontWeight = FontWeight.Black,
                            // R-S1: Georgia (TitleFontFamily) like the model h2.
                            fontFamily = TitleFontFamily, color = GoldWhite
                        )
                        Text(
                            text = stringResource(R.string.sync_subtitle),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldBright
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SyncStep(
                        labelRes = R.string.sync_step_live,
                        done = steps.first,
                        active = activeIndex == 0,
                        percent = syncProgress.stepPercents.getOrElse(0) { 0 }
                    )
                    SyncStep(
                        labelRes = R.string.sync_step_films,
                        done = steps.second,
                        active = activeIndex == 1,
                        percent = syncProgress.stepPercents.getOrElse(1) { 0 }
                    )
                    SyncStep(
                        labelRes = R.string.sync_step_series,
                        done = steps.third,
                        active = activeIndex == 2,
                        percent = syncProgress.stepPercents.getOrElse(2) { 0 }
                    )
                }
            }
        }
    }
}

/** One sync step row (model .sync-step): dark card + 12dp progress bar.
 *  The right-hand value sits in the same place and format as the model
 *  (sp1-txt "0 %" / "100 %"): the active step shows the live percent,
 *  a finished step "100 %", a pending step "En attente". */
@Composable
private fun SyncStep(labelRes: Int, done: Boolean, active: Boolean, percent: Int = 0) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x8C000000))
            .border(1.5.dp, BorderGold, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(labelRes),
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite
            )
            Text(
                text = when {
                    done -> stringResource(R.string.sync_done_percent)
                    active -> "${percent.coerceIn(0, 100)} %"
                    else -> stringResource(R.string.sync_waiting)
                },
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GoldBright
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF3A2F16))
        ) {
            // Determinate bar: live percent on the active step, full when
            // done, empty while pending (model .loader-bar-fill).
            LinearProgressIndicator(
                progress = {
                    when {
                        done -> 1f
                        active -> percent.coerceIn(0, 100) / 100f
                        else -> 0f
                    }
                },
                modifier = Modifier.fillMaxSize(),
                color = Gold,
                trackColor = Color.Transparent
            )
        }
    }
}
