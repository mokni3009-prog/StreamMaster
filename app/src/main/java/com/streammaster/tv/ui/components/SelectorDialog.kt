package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocusBorderless

/**
 * Generic list selector dialog (CONTRACT §8, SPEC §4.1).
 *
 * Mirrors the prototype's `openSelector()` / `pickSelector()`: a titled
 * list where the current option is marked, focus starts on the current
 * option, and OK picks it.
 *
 * The player screen maps its own [com.streammaster.tv.player.TrackOption]
 * values to [SelectorOption] before calling this — the dialog itself knows
 * nothing about tracks, so it stays reusable for audio / subtitle /
 * quality / display-mode lists and for DNS / User-Agent lists in Settings.
 */
data class SelectorOption(val id: String, val label: String)

@Composable
fun SelectorDialog(
    title: String,
    options: List<SelectorOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String = "",
    /** Optional poster shown left of the title (EPG enrichment, 2026-11-22). */
    posterUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val selectedIndex = options.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
    val focusRequesters = remember(options) {
        List(options.size) { FocusRequester() }
    }
    LaunchedEffect(Unit) {
        try {
            focusRequesters.getOrNull(selectedIndex)?.requestFocus()
        } catch (_: Exception) {
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.55f)
                .background(Color(0xFF14100A), RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .width(110.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = GoldWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(text = subtitle, color = TextMuted, fontSize = 16.sp)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    val selected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) Color(0xFF2A2113) else Color(0xFF1A1408),
                                RoundedCornerShape(12.dp)
                            )
                            .tvFocusBorderless()
                            .focusRequester(focusRequesters[index])
                            .clickable { onSelect(option.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selected) "✓" else "",
                            color = Gold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = option.label,
                            color = if (selected) Gold else GoldWhite,
                            fontSize = 19.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            GoldButton(
                text = "Fermer",
                onClick = onDismiss,
                outlined = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
