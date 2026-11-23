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
 * LD05: no fake live percentage — the active step shows an indeterminate
 * bar, a finished step shows "100 %", a pending step shows "En attente".
 * A true live percentage needs sync progress from the repository layer,
 * which does not exist (no API reports per-item sync progress).
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
                        active = activeIndex == 0
                    )
                    SyncStep(
                        labelRes = R.string.sync_step_films,
                        done = steps.second,
                        active = activeIndex == 1
                    )
                    SyncStep(
                        labelRes = R.string.sync_step_series,
                        done = steps.third,
                        active = activeIndex == 2
                    )
                }
            }
        }
    }
}

/** One sync step row (model .sync-step): dark card + 12dp progress bar. */
@Composable
private fun SyncStep(labelRes: Int, done: Boolean, active: Boolean) {
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
                    active -> stringResource(R.string.sync_in_progress)
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
            if (active) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = Gold,
                    trackColor = Color.Transparent
                )
            } else {
                LinearProgressIndicator(
                    progress = { if (done) 1f else 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = Gold,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
