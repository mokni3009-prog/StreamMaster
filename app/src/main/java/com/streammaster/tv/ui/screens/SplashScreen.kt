package com.streammaster.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.delay

/** Splash — model #screen-boot .boot-card: padding 56×76dp, 36dp radius,
 *  rgba(20,14,4,.86) background, 2px border, shadow + gold glow; 150dp
 *  pulsing logo; STREAM 64sp / MASTER 22sp; 560×16dp loader bar;
 *  status 22sp Bold; version 15sp ExtraBold; outline skip button 68dp.
 *  App lock (2026-11-22): when enabled, the parental PIN must be entered
 *  before the app opens. */
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
    // Model floatPulse: scale 1 → 1.06 over 2.4s, infinite.
    val pulse = rememberInfiniteTransition(label = "bootLogo")
    val logoScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "bootLogoScale"
    )
    ThemedScreen {
        // R-M5 (SP08): the splash-only glow — 900×550 at 50%/42%,
        // rgba(229,193,88,.26) — drawn over the theme background, behind
        // the card (same elliptical-glow technique as Theme.kt).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val glowSize = Size(900.dp.toPx(), 550.dp.toPx())
                    val center = Offset(size.width * 0.5f, size.height * 0.42f)
                    val radius = maxOf(glowSize.width, glowSize.height) / 2f
                    withTransform({
                        scale(
                            scaleX = glowSize.width / (2f * radius),
                            scaleY = glowSize.height / (2f * radius),
                            pivot = center
                        )
                    }) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x42E5C158), Color.Transparent),
                                center = center, radius = radius
                            ),
                            center = center, radius = radius
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .shadow(
                            30.dp,
                            RoundedCornerShape(36.dp),
                            ambientColor = Color.Black,
                            spotColor = Color(0xFFE5C158)
                        )
                        .background(Color(0xDB140E04), RoundedCornerShape(36.dp))
                        .border(2.dp, BorderGold, RoundedCornerShape(36.dp))
                        .padding(horizontal = 76.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer { scaleX = logoScale; scaleY = logoScale }
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "STREAM",
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                        fontSize = 64.sp, letterSpacing = 5.sp, color = GoldWhite
                    )
                    Text(
                        text = "MASTER",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp, letterSpacing = 10.sp, color = GoldBright,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // Loader bar: 560×16dp, 1.5px border, gold glow.
                Box(
                    modifier = Modifier
                        .width(560.dp)
                        .height(16.dp)
                        .shadow(8.dp, RoundedCornerShape(10.dp), spotColor = GoldBright)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xBF000000))
                        .border(1.5.dp, BorderGold, RoundedCornerShape(10.dp))
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = Gold,
                        trackColor = Color.Transparent
                    )
                }
                Text(
                    text = stringResource(R.string.splash_status),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                )
                Text(
                    text = stringResource(R.string.app_version),
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp, color = GoldBright
                )
                // Skip button — model .btn-outline: 68dp high, 20dp radius,
                // 2px strong border, 20sp ExtraBold. (Local, not the shared
                // GhostButton, whose size does not match the model.)
                Box(
                    modifier = Modifier
                        .height(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x1FE5C158))
                        .border(2.dp, BorderStrong, RoundedCornerShape(20.dp))
                        .focusRequester(skipFocus)
                        .tvFocus()
                        .clickable { skipRequested = true }
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.splash_skip),
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite
                    )
                }
                }
            }
        }
    }
}
