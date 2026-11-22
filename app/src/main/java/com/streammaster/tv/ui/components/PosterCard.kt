package com.streammaster.tv.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocus

/**
 * Poster card (CONTRACT §8, SPEC §4.1).
 *
 * - 20.dp radius, 2.dp gold border and focus scale 1.03 via [tvFocus]
 *   (CONTRACT §6 / SPEC §9).
 * - [previewing]: poster-preview state — after 2 s of focus the card
 *   scales to 1.06 and shows a "▶ Aperçu" badge plus an animated progress
 *   bar overlay; the caller clears it on focus loss / on opening the
 *   player or detail screen (the 2 s timer lives in the caller, e.g. the
 *   Films/Series ViewModel, because only it knows focus timing).
 * - [progress]: continue-watching progress 0–100; a gold progress bar is
 *   drawn at the bottom of the poster when 1–99 (0 = not started / no bar,
 *   100 = finished / no bar).
 * - [isFavorite]: ★ badge, top-right.
 * - [customNumber]: gold "N°" badge, top-left, for channels with an
 *   assigned custom number (SPEC §4.2).
 * - When [posterUrl] is null (e.g. a live channel with no artwork) a
 *   hue-coloured placeholder with the title's first letter is shown,
 *   matching the prototype's channel tiles.
 */
@Composable
fun PosterCard(
    title: String,
    hue: Int = 0,
    posterUrl: String? = null,
    progress: Int = 0,
    isFavorite: Boolean = false,
    customNumber: Int? = null,
    previewing: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val previewProgress by rememberInfiniteTransition(label = "posterPreview").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "previewProgress"
    )

    Column(
        modifier = modifier
            .width(170.dp)
            .graphicsLayer {
                // Preview state scales slightly beyond the normal focus scale.
                val s = if (previewing) 1.06f else 1f
                scaleX = s
                scaleY = s
            }
            .tvFocus()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(255.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(hueColor(hue))
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Hue placeholder with the title initial (prototype channel tile).
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "?",
                        color = GoldWhite,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Gold N° badge (custom channel number).
            if (customNumber != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gold)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "N° $customNumber",
                        color = GoldDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Favorite badge.
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC14100A))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "★", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Preview overlay: "▶ Aperçu" badge + animated progress bar.
            if (previewing) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "▶ Aperçu",
                        color = GoldWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { previewProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Gold,
                        trackColor = Color(0xFF3A2F16)
                    )
                }
            } else if (progress in 1..99) {
                // Continue-watching progress bar (bottom of the poster).
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Gold,
                    trackColor = Color(0x99000000)
                )
            }
        }

        Text(
            text = title,
            color = GoldWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )
    }
}

/** Deterministic muted colour from the prototype's hue value (0–359). */
private fun hueColor(hue: Int): Color {
    val h = ((hue % 360) + 360) % 360
    return Color.hsv(h.toFloat(), 0.45f, 0.32f)
}
