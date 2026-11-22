package com.streammaster.tv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.LocalLowRam
import com.streammaster.tv.ui.theme.tvFocus
import kotlinx.coroutines.delay

/**
 * Poster tile with the CONTRACT §6 poster-preview behaviour:
 * 2s of focus on a film/series poster -> scale 1.06 + "▶ Aperçu" badge +
 * animated progress bar; cancelled immediately on focus loss.
 * (Local helper — the components agent's PosterCard is a different,
 * card-level component; this tile owns the preview timing.)
 */
@Composable
fun PosterTile(
    title: String,
    hue: Int,
    code: String,
    progressPercent: Int = 0,
    isFavorite: Boolean = false,
    numberBadge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    val lowRam = LocalLowRam.current
    val infinite = rememberInfiniteTransition(label = "preview")
    val previewProgress by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "previewProgress"
    )
    LaunchedEffect(focused) {
        if (focused && !lowRam) {
            delay(2000)
            previewing = true
        } else {
            previewing = false
        }
    }
    Column(
        modifier = modifier
            .width(220.dp)
            .tvFocus()
            .onFocusChanged { focused = it.isFocused }
            .scale(if (previewing) 1.06f else 1f)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.hsv(hue.toFloat(), 0.55f, 0.32f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = code, fontSize = 44.sp, fontWeight = FontWeight.Black, color = GoldWhite)
            if (isFavorite) {
                Text(
                    text = "♥", fontSize = 20.sp, color = Gold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                )
            }
            if (numberBadge != null) {
                Text(
                    text = "N° $numberBadge", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = GoldDeep,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Gold)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (previewing) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xAA000000))
                        .padding(10.dp)
                ) {
                    Text(text = stringResource(R.string.preview_badge), color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LinearProgressIndicator(
                        progress = { previewProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Gold, trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }
        if (progressPercent in 1..99) {
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 6.dp),
                color = Gold, trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
        Text(
            text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = GoldWhite, maxLines = 1, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
