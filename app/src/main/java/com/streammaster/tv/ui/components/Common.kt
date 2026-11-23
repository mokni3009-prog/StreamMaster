package com.streammaster.tv.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus

/**
 * Shared building blocks (CONTRACT §8) in the gold identity
 * (CONTRACT §6 / SPEC §9: Gold #E5C158, GoldWhite #FFFDF4, 20.dp radius,
 * focus scale 1.03 via [tvFocus]).
 */

/** Gold filled button (or gold-outlined when [outlined] is true). */
@Composable
fun GoldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
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
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                // SPEC §9: primary buttons use the gold gradient, not flat gold.
                if (enabled && !outlined) Modifier.background(GoldGradient)
                else Modifier.background(bg)
            )
            .tvFocus()
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** Section title (serif, per CONTRACT §6) with an optional subtitle. */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = GoldWhite,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif
        )
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
 * Full error screen (player error: "Impossible de lire cette chaîne",
 * SPEC §2). [onRetry] retries the same content, [onBack] returns to the
 * previous screen, [onChangeChannel] — shown only when non-null — opens
 * another channel (live only).
 */
@Composable
fun ErrorScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onChangeChannel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2113))
                .padding(horizontal = 28.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "!", color = Gold, fontSize = 44.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            color = GoldWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = TextMuted,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        GoldButton(
            text = "Réessayer",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        GoldButton(
            text = "Retour",
            onClick = onBack,
            outlined = true,
            modifier = Modifier.fillMaxWidth(0.4f)
        )
        if (onChangeChannel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            GoldButton(
                text = "Changer de chaîne",
                onClick = onChangeChannel,
                outlined = true,
                modifier = Modifier.fillMaxWidth(0.4f)
            )
        }
    }
}

/**
 * Toast helper (CONTRACT §8: Toast was chosen over Snackbar because it
 * works from anywhere — ViewModels, the player, services — without a
 * Scaffold/SnackbarHost in scope).
 */
fun showToast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
