package com.streammaster.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streammaster.tv.R
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.tvFocusBorderless

/**
 * Generic list selector dialog (CONTRACT §8, SPEC §4.1).
 *
 * Mirrors the prototype's `openSelector()` / `pickSelector()`:
 * `.dlg-card.xwide` — 980dp wide, 40×46dp padding, 30dp radius,
 * gold gradient, 2.5px border; each `.sel-opt` has a 38dp gold check
 * circle, a 20sp title, a 16sp description and an optional `.sel-tag`.
 * Focus starts on the current option, and OK picks it.
 *
 * The player screen maps its own [com.streammaster.tv.player.TrackOption]
 * values to [SelectorOption] before calling this — the dialog itself knows
 * nothing about tracks, so it stays reusable for audio / subtitle /
 * quality / display-mode lists and for DNS / User-Agent lists in Settings.
 */
data class SelectorOption(
    val id: String,
    val label: String,
    /** Optional description shown under the label (`.sel-opt span`, 16sp). */
    val description: String = "",
    /** Optional tag pill shown at the end of the row (`.sel-tag`). */
    val tag: String? = null
)

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

    // .dlg-card.xwide: 980dp, padding 40×46, radius 30, gradient, 2.5px border.
    val cardShape = RoundedCornerShape(30.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .widthIn(max = 980.dp).fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        0.0f to Color(0xFA221807),
                        1.0f to Color(0xFC0C0903)
                    ),
                    cardShape
                )
                .border(2.5.dp, BorderStrong, cardShape)
                .padding(horizontal = 46.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(text = subtitle, color = GoldSoft, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    val selected = option.id == selectedId
                    val rowShape = RoundedCornerShape(17.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(Color(0x4DE5C158), Color(0x1AE5C158))
                                        ),
                                        rowShape
                                    )
                                } else {
                                    Modifier.background(Color(0x80000000), rowShape)
                                }
                            )
                            .border(
                                2.dp,
                                if (selected) Color(0xFFFFF8D6) else Color(0x42FFE89E),
                                rowShape
                            )
                            // The focusRequester must come BEFORE
                            // tvFocusBorderless (which owns the focusable).
                            .focusRequester(focusRequesters[index])
                            .tvFocusBorderless()
                            .clickable { onSelect(option.id) }
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // .sel-check: 38dp gold circle.
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) Modifier.background(GoldGradient)
                                    else Modifier.background(Color(0x26E5C158))
                                )
                                .border(
                                    2.dp,
                                    if (selected) Color.White else Color(0x59FFE89E),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                color = if (selected) GoldDeep else Color.Transparent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                color = GoldWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (option.description.isNotEmpty()) {
                                Text(
                                    text = option.description,
                                    color = GoldBright,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (option.tag != null) {
                            Text(
                                text = option.tag,
                                color = GoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color(0x2EE5C158))
                                    .border(1.dp, Color(0x59FFE89E), RoundedCornerShape(13.dp))
                                    .padding(horizontal = 13.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            // K06: "Annuler" as a gold (primary) button, like the prototype.
            GoldButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
