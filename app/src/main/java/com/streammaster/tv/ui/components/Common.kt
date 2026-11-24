package com.streammaster.tv.ui.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.ui.focus.focusProperties
import com.streammaster.tv.ui.theme.BodyFontFamily
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardRadius
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.TextShadowCrisp
import com.streammaster.tv.ui.theme.TitleFontFamily
import com.streammaster.tv.ui.theme.tvFocus

/**
 * Shared building blocks (CONTRACT §8) in the gold identity
 * (CONTRACT §6 / SPEC §9: Gold #E5C158, GoldWhite #FFFDF4, 20.dp radius,
 * focus scale 1.03 via [tvFocus]).
 */

/** Gold filled button (or gold-outlined when [outlined] is true).
 * G08 — prototype `.btn-gold`: 68dp high, 20dp radius, 2dp #fff6cc border,
 * 23sp Black, 1sp letter-spacing, gold gradient + light gold shadow. */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    /** T-05 — optional leading vector icon (e.g. play/pause) instead of
     *  a text glyph; null keeps the text-only button as before. */
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit
) {
    val bg = when {
        !enabled -> Color(0xFF3A2F16)
        outlined -> Color.Transparent
        else -> Gold
    }
    val fg = when {
        !enabled -> TextMuted
        outlined -> GoldWhite
        else -> GoldDeep
    }
    Box(
        modifier = modifier
            .height(68.dp)
            .shadow(
                12.dp, RoundedCornerShape(20.dp),
                ambientColor = Gold.copy(alpha = 0.48f),
                spotColor = Gold.copy(alpha = 0.48f)
            )
            .clip(RoundedCornerShape(20.dp))
            .then(
                // SPEC §9: primary buttons use the gold gradient, not flat gold.
                if (enabled && !outlined) Modifier.background(GoldGradient)
                else Modifier.background(bg)
            )
            .then(
                if (enabled && !outlined) Modifier.border(2.dp, Color(0xFFFFF6CC), RoundedCornerShape(20.dp))
                else Modifier
            )
            // L-11 — a disabled button leaves the focus path (was
            // focusable with a dead OK press: Login while loading,
            // Diagnostics while checking, Drive, Onboarding...).
            // GoldSaveButton's own focusProperties stays harmless.
            .focusProperties { canFocus = enabled }
            .tvFocus()
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = text,
                    color = fg,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = BodyFontFamily,
                    letterSpacing = 1.sp,
                    style = TextStyle(shadow = TextShadowCrisp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = text,
                color = fg,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                fontFamily = BodyFontFamily,
                letterSpacing = 1.sp,
                style = TextStyle(shadow = TextShadowCrisp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * G06 — shared card surface: the prototype card shadow (`--card-shadow`:
 * `0 18px 42px rgba(0,0,0,.88), 0 0 24px rgba(229,193,88,.14)`).
 * [shape], [background] and [border] let a card keep its own radius,
 * gradient/solid background and border (R-B2) while still getting the
 * shadow; defaults reproduce the plain theme card (20dp, CardSurface
 * colour, no border). Pass a [SolidColor] brush for a flat colour.
 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(CardRadius),
    background: Brush? = null,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                18.dp, shape,
                ambientColor = Color(0xE0000000), // rgba(0,0,0,.88)
                spotColor = Gold.copy(alpha = 0.14f) // rgba(229,193,88,.14)
            )
            .clip(shape)
            .then(
                if (background != null) Modifier.background(background, shape)
                else Modifier.background(com.streammaster.tv.ui.theme.CardSurface)
            )
            .then(if (border != null) Modifier.border(border, shape) else Modifier),
        content = content
    )
}

/** Section title (serif, per CONTRACT §6) with an optional subtitle.
 * G09 — prototype `.sec-head`: 8x34dp gold bar + Georgia 32sp Black title
 * + gold count badge (`.badge-cnt`). */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    count: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 34.dp)
                    .shadow(
                        16.dp, RoundedCornerShape(4.dp),
                        ambientColor = Gold, spotColor = Gold
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoldGradient)
            )
            Text(
                text = title,
                color = GoldWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = TitleFontFamily,
                letterSpacing = 0.6.sp,
                style = TextStyle(shadow = TextShadowCrisp)
            )
            if (count != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x2EE5C158)) // rgba(229,193,88,.18)
                        .border(1.5.dp, BorderGold, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = GoldBright,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(shadow = TextShadowCrisp)
                    )
                }
            }
        }
        if (!subtitle.isNullOrEmpty()) {
            Text(text = subtitle, color = TextMuted, fontSize = 16.sp)
        }
    }
}

/** Centred gold loading spinner with an optional message. */
@Composable
fun LoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = Gold)
        if (!message.isNullOrEmpty()) {
            Text(
                text = message,
                color = GoldWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * R-M2 — gold toast bus (prototype `.toast`: gold pill, bottom centre,
 * 1.5px gold border, gold shadow, 19sp). [showToast] publishes here
 * instead of the system Toast; [GoldToastHost] (mounted once in
 * AppNavHost) renders it over every screen. The id makes a repeated
 * identical message re-trigger the host.
 */
data class ToastEvent(val text: String, val id: Long)

object ToastBus {
    private val _toast = MutableStateFlow<ToastEvent?>(null)
    val toast: StateFlow<ToastEvent?> = _toast
    // T-06 — atomic counter: show() is called from any thread.
    private val nextId = AtomicLong(0L)
    fun show(msg: String) {
        _toast.value = ToastEvent(msg, nextId.incrementAndGet())
    }

    fun clear() {
        _toast.value = null
    }
}

/**
 * Toast helper (CONTRACT §8: Toast was chosen over Snackbar because it
 * works from anywhere — ViewModels, the player, services — without a
 * Scaffold/SnackbarHost in scope). R-M2: publishes to [ToastBus] so the
 * gold [GoldToastHost] renders it; the system Toast is no longer used.
 * [context] is kept for call-site compatibility.
 */
fun showToast(context: Context, msg: String) {
    ToastBus.show(msg)
}

/** R-M2 — the gold toast pill (prototype `.toast`: 18dp radius, 16×34dp
 * padding, 19sp bold #fff3c4, gold gradient, 1.5px gold border, black +
 * gold shadow, 44dp above the bottom). Shows ~3s, then hides. */
@Composable
fun GoldToastHost() {
    val toast by ToastBus.toast.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        if (toast != null) {
            visible = true
            delay(3000)
            visible = false
            ToastBus.clear()
        }
    }
    if (visible && toast != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 44.dp)
                    .shadow(
                        18.dp, RoundedCornerShape(18.dp),
                        ambientColor = Color(0xE6000000), // rgba(0,0,0,.9)
                        spotColor = Color(0x66D4AF37) // rgba(212,175,55,.4)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFA281E08), Color(0xFA0E0A03))
                        )
                    )
                    .border(1.5.dp, Gold, RoundedCornerShape(18.dp))
                    .padding(horizontal = 34.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = toast!!.text,
                    color = Color(0xFFFFF3C4),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = TextShadowCrisp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
