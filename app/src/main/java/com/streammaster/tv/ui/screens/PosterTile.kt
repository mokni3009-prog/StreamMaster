package com.streammaster.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.R
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.LocalLowRam
import com.streammaster.tv.ui.theme.tvFocus
import kotlinx.coroutines.delay
import java.util.Locale

/** "8.7" (locale decimal separator, as in the prototype: "8,7" in fr). */
internal fun formatRating(rating: Double): String =
    String.format(Locale.getDefault(), "%.1f", rating)

/**
 * Prototype poster art (posterHTML `.bgp`): radial glow
 * hsl(hue,88%,60%) -> hsl(hue,74%,30%) over a 160° linear
 * hsl(hue,66%,22%) -> #0b0702. Reused by the detail poster (D02).
 */
@Composable
internal fun HueBackdrop(hue: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(Color.hsl(hue.toFloat(), 0.66f, 0.22f), Color(0xFF0B0702)),
                    start = Offset(w, 0f),
                    end = Offset(0f, h)
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.0f to Color.hsl(hue.toFloat(), 0.88f, 0.60f),
                    0.42f to Color.hsl(hue.toFloat(), 0.74f, 0.30f),
                    0.74f to Color.Transparent,
                    center = Offset(w * 0.34f, h * 0.26f),
                    radius = w * 0.9f
                )
            )
        )
    }
}

/**
 * Poster tile, literal to the prototype `.poster`:
 * 232x338 (352 high in a grid, 360x206 wide for "Récemment vus"),
 * ring + shade + gold quality badge, title + "★ rating • year/seasons"
 * INSIDE the poster, 36dp favourite badge, 8dp progress bar inside the
 * bottom edge, and the 2s-focus preview (total scale ~1.06: tvFocus 1.03 x
 * an extra 1.03) with a gold "▶ Aperçu" badge and a single 12s bar.
 * (CONTRACT §6 poster-preview behaviour.)
 */
@Composable
fun PosterTile(
    title: String,
    hue: Int,
    code: String,
    progressPercent: Int = 0,
    isFavorite: Boolean = false,
    numberBadge: String? = null,
    quality: String = "HD",
    rating: Double = 0.0,
    year: Int? = null,
    seasons: Int? = null,
    subtitle: String? = null,
    unstable: Boolean = false,
    wide: Boolean = false,
    inGrid: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    val lowRam = LocalLowRam.current
    val previewAnim = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (focused && !lowRam) {
            delay(2000)
            previewing = true
        } else {
            previewing = false
        }
    }
    // One 12s run of the preview bar (prototype `.pv-bar i`: `pvbar 12s linear forwards`).
    LaunchedEffect(previewing) {
        if (previewing) {
            previewAnim.snapTo(0f)
            previewAnim.animateTo(1f, tween(12000, easing = LinearEasing))
        } else {
            previewAnim.snapTo(0f)
        }
    }
    val tileWidth = when {
        inGrid -> null // fillMaxWidth (grid cell)
        wide -> 360.dp
        else -> 232.dp
    }
    val posterHeight = when {
        inGrid -> 352.dp
        wide -> 206.dp
        else -> 338.dp
    }
    // Wide posters show the remaining text / seasons (prototype posterHTML);
    // standard posters show "★ rating • year" (film) or "★ rating • N saisons" (series).
    val sub = if (wide) {
        subtitle
    } else {
        subtitle ?: when {
            year != null -> "★ ${formatRating(rating)} • $year"
            seasons != null -> "★ ${formatRating(rating)} • ${stringResource(R.string.seasons_label, seasons)}"
            rating > 0 -> "★ ${formatRating(rating)}"
            else -> null
        }
    }
    Column(
        modifier = modifier
            .then(if (tileWidth != null) Modifier.width(tileWidth) else Modifier.fillMaxWidth())
            .tvFocus()
            .onFocusChanged { focused = it.isFocused }
            // tvFocus already scales 1.03 on focus; the extra 1.03 here makes the
            // preview total ~1.06, exactly the prototype `.poster.previewing`.
            .scale(if (previewing) 1.03f else 1f)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, BorderGold, RoundedCornerShape(20.dp))
        ) {
            HueBackdrop(hue, Modifier.fillMaxSize())
            // Decorative ring (prototype `.ring`).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 68.dp, y = (-46).dp)
                    .size(230.dp)
                    .border(2.dp, Color(0x73FFE89E), CircleShape)
            )
            // Bottom shade (prototype `.shade`).
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF060401).copy(alpha = 0.15f),
                        0.48f to Color(0xFF060401).copy(alpha = 0.55f),
                        0.88f to Color(0xFF060401).copy(alpha = 0.98f)
                    )
                )
            )
            // Poster-art placeholder (no real poster images yet).
            Text(
                text = code, fontSize = 44.sp, fontWeight = FontWeight.Black,
                color = GoldWhite, modifier = Modifier.align(Alignment.Center)
            )
            // Quality badge, top-left (prototype `.q`); the legacy number
            // badge (favourite channels) takes the same spot instead.
            if (numberBadge != null) {
                Text(
                    text = "N° $numberBadge", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = GoldDeep,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GoldGradient)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = quality, fontSize = 14.sp, fontWeight = FontWeight.Black,
                    color = GoldDeep,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(GoldGradient)
                        .padding(horizontal = 11.dp, vertical = 3.dp)
                )
            }
            // Unstable-stream warning (prototype `.warn-badge`), left of the
            // favourite badge so the two never overlap.
            if (unstable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 56.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6A4D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "!", fontSize = 16.sp, fontWeight = FontWeight.Black, color = GoldDeep)
                }
            }
            // Favourite badge (prototype `.fav-badge`): 36dp circle, dark
            // background + 1.5px border; gold gradient when favourite.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .then(
                        if (isFavorite) Modifier.background(GoldGradient)
                        else Modifier.background(Color(0xD90A0702))
                    )
                    .border(
                        1.5.dp,
                        if (isFavorite) Color.White else BorderGold,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_star), contentDescription = null,
                    tint = if (isFavorite) GoldDeep else GoldBright,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Title + sub INSIDE the poster, bottom-left (prototype `.info`).
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Text(
                    text = title, fontSize = 23.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif, color = GoldWhite,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (!sub.isNullOrBlank()) {
                    Text(
                        text = sub, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = GoldBright, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
            // Progress bar INSIDE the bottom edge (prototype `.prog`, 8dp).
            if (progressPercent in 1..99) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xCC000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPercent / 100f)
                            .height(8.dp)
                            .background(GoldGradient)
                    )
                }
            }
            // Preview overlay after 2s of focus (prototype `.poster-preview`).
            if (previewing) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.28f to Color.Transparent,
                                0.60f to Color(0x80000000),
                                1.0f to Color(0xE0000000)
                            )
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = stringResource(R.string.preview_badge),
                        fontSize = 15.sp, fontWeight = FontWeight.Black, color = GoldDeep,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(GoldGradient)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 9.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.28f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(previewAnim.value)
                                .height(5.dp)
                                .background(GoldGradient)
                        )
                    }
                }
            }
        }
    }
}
