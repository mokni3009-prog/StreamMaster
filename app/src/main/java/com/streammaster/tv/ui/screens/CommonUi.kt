package com.streammaster.tv.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocus

/** Screen-local shared UI. GoldButton / SectionHeader / LoadingIndicator
 *  come from the components agent's Common.kt (CONTRACT §8) and are
 *  imported by the screens that use them; only the helpers below are
 *  screen-local (no shared equivalent exists for them). */
@Composable
fun GhostButton(
    text: String = "",
    @DrawableRes iconRes: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // R-S4: optional leading icon (e.g. the episode download button);
        // text stays optional so an icon-only button is possible.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = GoldWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (text.isNotEmpty()) {
                Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldWhite)
            }
        }
    }
}

/** G10 — prototype `.sort-pill`: padding 10x18dp, 20dp radius, 2dp `line`
 * border, 16sp ExtraBold; selected: gold gradient + #fff8d6 border. */
@Composable
fun SortPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(GoldGradient)
                else Modifier.background(Color(0x73000000)) // rgba(0,0,0,.45)
            )
            .border(
                2.dp,
                if (selected) Color(0xFFFFF8D6) else BorderGold,
                RoundedCornerShape(20.dp)
            )
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) GoldDeep else GoldWhite)
    }
}

/** Read-only input box (SPEC §4.5): no device keyboard — tapping opens the built-in keyboard.
 *  Model .input-wrap: 60dp high, 16dp radius, 2dp border, rgba(8,6,2,.92) background, 21sp input. */
@Composable
fun ReadOnlyField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEB080602))
            .border(2.dp, BorderGold, RoundedCornerShape(16.dp))
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (value.isEmpty()) label else value,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = if (value.isEmpty()) com.streammaster.tv.ui.theme.TextMuted else GoldWhite
        )
    }
}
