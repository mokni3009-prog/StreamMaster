package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streammaster.tv.R
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite

/**
 * Confirmation dialog (K11).
 *
 * Mirrors the prototype's `dlg-confirm`: a gold card with a 100dp
 * warning icon, a Georgia 34sp title, a message and confirm / cancel
 * buttons. Used for destructive confirmations (delete a playlist,
 * clear history, …) instead of the inline confirmation columns that
 * used to live inside the pages.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirmer",
    dismissText: String = "Annuler",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Initial focus: the confirm button — without it a Dialog opens with
    // no focused element and the first D-pad press is lost.
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { confirmFocus.requestFocus() }
    // .dlg-card shell (prototype): 720dp, padding 40×46, radius 30,
    // gold gradient, 2.5px border.
    val cardShape = RoundedCornerShape(30.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .background(
                    Brush.linearGradient(
                        0.0f to Color(0xFA221807),
                        1.0f to Color(0xFC0C0903)
                    ),
                    cardShape
                )
                .border(2.5.dp, BorderStrong, cardShape)
                .padding(horizontal = 46.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // .dlg-icon.warn: 100dp circle, warm gradient, alert icon.
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFFE3A3), Color(0xFFD99B0E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = null,
                    tint = Color(0xFF2A1A00),
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = title,
                color = GoldWhite,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                color = GoldSoft,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
            ) {
                GoldButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).focusRequester(confirmFocus)
                )
                GoldButton(
                    text = dismissText,
                    onClick = onDismiss,
                    outlined = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
