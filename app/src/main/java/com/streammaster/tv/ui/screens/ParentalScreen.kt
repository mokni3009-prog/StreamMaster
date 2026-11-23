package com.streammaster.tv.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.ManageKind
import com.streammaster.tv.ui.viewmodel.ParentalViewModel

/**
 * Contrôle parental (PC01) — full screen, NOT a sidebar item.
 * Title + PIN badge + mode tabs (Direct / Films / Séries, 56dp, radius 28,
 * icon + lock count) + 3-column workspace (bouquets | items | actions,
 * 470dp / 1fr / 430dp) + save bar (Retour + Save) + remote hint + the
 * "OK locks/unlocks" note. OK on a row toggles its lock; locks are a
 * draft until Save (ParentalViewModel -> SettingsRepository).
 */
@Composable
fun ParentalScreen(
    navController: NavHostController,
    viewModel: ParentalViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val dirtyCount by viewModel.dirtyCount.collectAsState()
    val toastRes by viewModel.toastRes.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val films by viewModel.films.collectAsState()
    val series by viewModel.series.collectAsState()
    val context = LocalContext.current
    var kind by remember { mutableStateOf(ManageKind.LIVE) }
    var selectedBouquetId by remember { mutableStateOf<String?>(null) }
    var selectedItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(draft.saved) {
        if (draft.saved) {
            showToast(context, context.getString(R.string.pc_saved))
            viewModel.clearSavedFlag()
        }
    }
    LaunchedEffect(toastRes) {
        toastRes?.let {
            showToast(context, context.getString(it))
            viewModel.clearToast()
        }
    }

    val kindBouquets = viewModel.bouquetsFor(kind)
    // Keep a bouquet selected (first one by default, like the prototype).
    LaunchedEffect(kind, kindBouquets) {
        if (selectedBouquetId == null || kindBouquets.none { it.id == selectedBouquetId }) {
            selectedBouquetId = kindBouquets.firstOrNull()?.id
            selectedItemId = null
        }
    }
    val items: List<ParentalItem> = when (kind) {
        ManageKind.LIVE -> channels.filter { it.bouquetId == selectedBouquetId }
            .map { ParentalItem(it.id, it.customName ?: it.name, it.category, it.bouquetId, R.drawable.ic_tv) }
        ManageKind.FILMS -> films.map { ParentalItem(it.id, it.customName ?: it.title, "${it.year} • ${it.genre}", null, R.drawable.ic_film) }
        ManageKind.SERIES -> series.map { ParentalItem(it.id, it.customName ?: it.title, "${stringResource(R.string.seasons_label, it.seasons)} • ${it.genre}", null, R.drawable.ic_layers) }
    }
    fun lockedCountFor(k: ManageKind): Int = when (k) {
        ManageKind.LIVE -> channels.count { viewModel.isItemLocked(k, it.id, it.bouquetId) }
        ManageKind.FILMS -> films.count { it.id in draft.items }
        ManageKind.SERIES -> series.count { it.id in draft.items }
    }
    val selectedBouquet = kindBouquets.firstOrNull { it.id == selectedBouquetId }
    val selectedItem = items.firstOrNull { it.id == selectedItemId }
    val bouquetLocked = selectedBouquetId?.let { viewModel.isBouquetLocked(kind, it) } == true
    val bouquetPartial = selectedBouquetId?.let { viewModel.isBouquetPartial(kind, it) } == true
    val bouquetFree = !bouquetLocked && !bouquetPartial
    val itemsTitle = stringResource(
        when (kind) {
            ManageKind.LIVE -> R.string.channels
            ManageKind.FILMS -> R.string.stats_films
            ManageKind.SERIES -> R.string.stats_series
        }
    )

    // Initial focus: the first tab (Direct).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.pc_title), fontSize = 30.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = GoldWhite,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (settings.pin.isNotEmpty()) GoldGradient else Color(0xFF2A2113))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(if (settings.pin.isNotEmpty()) R.string.st_pin_on else R.string.st_pin_off),
                    fontSize = 16.sp, fontWeight = FontWeight.Black,
                    color = if (settings.pin.isNotEmpty()) GoldDeep else GoldSoft
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeTab(
                label = stringResource(R.string.manage_tab_live), iconRes = R.drawable.ic_tv,
                count = lockedCountFor(ManageKind.LIVE), selected = kind == ManageKind.LIVE,
                modifier = Modifier.focusRequester(firstFocus)
            ) { kind = ManageKind.LIVE; selectedBouquetId = null; selectedItemId = null }
            ModeTab(
                label = stringResource(R.string.stats_films), iconRes = R.drawable.ic_film,
                count = lockedCountFor(ManageKind.FILMS), selected = kind == ManageKind.FILMS
            ) { kind = ManageKind.FILMS; selectedBouquetId = null; selectedItemId = null }
            ModeTab(
                label = stringResource(R.string.stats_series), iconRes = R.drawable.ic_layers,
                count = lockedCountFor(ManageKind.SERIES), selected = kind == ManageKind.SERIES
            ) { kind = ManageKind.SERIES; selectedBouquetId = null; selectedItemId = null }
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            // Column 1 — bouquets
            MgCol(
                modifier = Modifier.width(470.dp).fillMaxHeight(),
                iconRes = R.drawable.ic_folder,
                title = stringResource(R.string.bouquets),
                count = kindBouquets.size
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(kindBouquets) { index, bouquet ->
                        val locked = viewModel.isBouquetLocked(kind, bouquet.id)
                        val partial = viewModel.isBouquetPartial(kind, bouquet.id)
                        val itemCount = when (kind) {
                            ManageKind.LIVE -> channels.count { it.bouquetId == bouquet.id }
                            ManageKind.FILMS -> films.size
                            ManageKind.SERIES -> series.size
                        }
                        PcRow(
                            ord = index + 1,
                            iconRes = R.drawable.ic_folder,
                            title = bouquet.name,
                            subtitle = stringResource(R.string.mg_elements, itemCount),
                            badges = listOfNotNull(
                                if (locked) stringResource(R.string.locked) else null,
                                if (partial) stringResource(R.string.pc_partial) else null,
                                if (bouquet.hidden) stringResource(R.string.mg_hidden) else null
                            ),
                            selected = bouquet.id == selectedBouquetId
                        ) {
                            selectedBouquetId = bouquet.id
                            selectedItemId = null
                            viewModel.toggleBouquet(kind, bouquet.id)
                        }
                    }
                }
            }
            // Column 2 — items of the selected bouquet
            MgCol(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                iconRes = R.drawable.ic_shield,
                title = itemsTitle,
                count = items.size
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        val locked = viewModel.isItemLocked(kind, item.id, item.bouquetId)
                        PcRow(
                            ord = index + 1,
                            iconRes = item.iconRes,
                            title = item.title,
                            subtitle = item.subtitle,
                            badges = listOfNotNull(if (locked) stringResource(R.string.locked) else null),
                            selected = item.id == selectedItemId
                        ) {
                            selectedItemId = item.id
                            viewModel.toggleItem(item.id)
                        }
                    }
                }
            }
            // Column 3 — actions (no column header in the prototype: the
            // target box is the first element).
            MgCol(
                modifier = Modifier.width(430.dp).fillMaxHeight(),
                iconRes = R.drawable.ic_shield,
                title = stringResource(R.string.mg_target),
                count = dirtyCount,
                showHeader = false
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .dashedBorder(2.dp, BorderGold, 16.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = stringResource(R.string.mg_target), fontSize = 15.sp, color = TextMuted)
                    Text(
                        text = selectedItem?.title ?: selectedBouquet?.name ?: "—",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                    )
                    Text(
                        text = when {
                            selectedItem != null -> "${stringResource(R.string.pc_target_item)} • ${selectedBouquet?.name ?: ""}"
                            selectedBouquet != null -> "${stringResource(R.string.pc_target_bouquet)} • ${
                                stringResource(
                                    R.string.mg_elements,
                                    when (kind) {
                                        ManageKind.LIVE -> channels.count { it.bouquetId == selectedBouquet.id }
                                        ManageKind.FILMS -> films.size
                                        ManageKind.SERIES -> series.size
                                    }
                                )
                            }"
                            else -> stringResource(R.string.pc_no_target)
                        },
                        fontSize = 15.sp, color = GoldSoft
                    )
                }
                MgActRow(
                    label = stringResource(R.string.pc_lock_bouquet),
                    iconRes = R.drawable.ic_lock,
                    enabled = selectedBouquetId != null && !bouquetLocked
                ) { selectedBouquetId?.let { viewModel.lockBouquet(kind, it) } }
                MgActRow(
                    label = stringResource(R.string.pc_unlock_bouquet),
                    iconRes = R.drawable.ic_eye,
                    enabled = selectedBouquetId != null && !bouquetFree
                ) { selectedBouquetId?.let { viewModel.unlockBouquet(kind, it) } }
                MgActRow(
                    label = stringResource(R.string.pc_lock_all),
                    iconRes = R.drawable.ic_shield,
                    gold = true
                ) { viewModel.lockAll(kind) }
                MgActRow(
                    label = stringResource(R.string.pc_unlock_all),
                    iconRes = R.drawable.ic_x,
                    danger = true
                ) { viewModel.unlockAll(kind) }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.pc_note),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldSoft,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
        // Save bar: status + dirty badge + Retour + Save.
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0x33E5C158), Color(0x0FE5C158))))
                .border(2.dp, BorderStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (dirtyCount > 0) stringResource(R.string.pc_dirty, dirtyCount)
                    else stringResource(R.string.pc_clean),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                )
                Text(
                    text = stringResource(R.string.pc_save_hint),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldSoft
                )
            }
            if (dirtyCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x2EFF8A70))
                        .border(1.5.dp, Color(0xFFFF8A70), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.pc_dirty, dirtyCount),
                        fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD9CF)
                    )
                }
            }
            IconGhostButton(
                text = stringResource(R.string.back),
                iconRes = R.drawable.ic_arrow_left
            ) { navController.popBackStack() }
            GoldSaveButton(
                text = stringResource(R.string.save_changes),
                enabled = dirtyCount > 0
            ) { viewModel.save() }
        }
        RemoteHintBar(
            listOf(
                "↑ ↓" to stringResource(R.string.hint_choose_row),
                "← →" to stringResource(R.string.hint_choose_col),
                "OK" to stringResource(R.string.hint_lock_toggle)
            )
        )
    }
}

private data class ParentalItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val bouquetId: String?,
    @DrawableRes val iconRes: Int
)

/** Mode tab (M01/PC01): 56dp, radius 28, 24dp icon + lock count; active = gold gradient. */
@Composable
internal fun ModeTab(
    label: String,
    @DrawableRes iconRes: Int,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .then(if (selected) Modifier.background(GoldGradient) else Modifier.background(Color(0xFF1A1205)))
            .border(2.dp, if (selected) BorderStrong else BorderGold, RoundedCornerShape(28.dp))
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (selected) GoldDeep else GoldBright,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label, fontSize = 20.sp, fontWeight = FontWeight.Black,
            color = if (selected) GoldDeep else GoldWhite
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) Color(0x381A1100) else Color(0x66000000))
                .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = if (selected) GoldDeep else GoldBright,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = count.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (selected) GoldDeep else GoldBright
            )
        }
    }
}

/** Workspace column card (M02): gradient + 2px border + radius 24 + 18dp padding + header. */
@Composable
internal fun MgCol(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    count: Int,
    showHeader: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // R-B2: CardSurface with the column's own 24dp shape, gradient and
    // 2px border, so it carries the --card-shadow.
    CardSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        background = Brush.linearGradient(listOf(Color(0xFF1E1606), Color(0xFF0D0903))),
        border = BorderStroke(2.dp, BorderGold)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = GoldBright,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(text = title, fontSize = 23.sp, fontWeight = FontWeight.Black, color = GoldWhite)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x2EE5C158))
                        .border(1.5.dp, BorderGold, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = count.toString(), fontSize = 15.sp, fontWeight = FontWeight.Black, color = GoldBright)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(1.5.dp)
                    .background(Color(0x38FFE89E))
            )
        }
        content()
        }
    }
}

/** One row in a workspace column: 38dp order + 46dp icon + title + subtitle + badges. */
@Composable
private fun PcRow(
    ord: Int,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badges: List<String>,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) GoldGradient else Color(0x73000000))
            .border(2.dp, if (selected) BorderStrong else BorderGold, RoundedCornerShape(17.dp))
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) Color(0x381A1100) else Color(0xFF2A2113)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ord.toString(), fontSize = 16.sp, fontWeight = FontWeight.Black,
                color = if (selected) GoldDeep else Gold
            )
        }
        GoldIconBox(iconRes = iconRes, boxSize = 46.dp, iconSize = 24.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = if (selected) GoldDeep else GoldWhite
            )
            Text(text = subtitle, fontSize = 15.sp, color = if (selected) GoldDeep else GoldSoft)
        }
        badges.forEach { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0x381A1100) else Color(0xFF2A2113))
                    .border(1.dp, BorderGold, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) GoldDeep else GoldBright
                )
            }
        }
    }
}

/** Action row (M06): 60dp, 25dp icon + label; gold = primary, danger = red. */
@Composable
internal fun MgActRow(
    label: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    hint: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                when {
                    gold -> Modifier.background(GoldGradient)
                    danger -> Modifier.background(Color(0x1FFF6A4D))
                    else -> Modifier.background(Color(0x80000000))
                }
            )
            .border(
                2.dp,
                when {
                    gold -> BorderStrong
                    danger -> Color(0x8CFF8A70)
                    else -> BorderGold
                },
                RoundedCornerShape(16.dp)
            )
            .tvFocus()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = when {
                gold -> GoldDeep
                danger -> Color(0xFFFF8A70)
                else -> GoldBright
            },
            modifier = Modifier.size(25.dp)
        )
        Text(
            text = label, fontSize = 19.sp, fontWeight = FontWeight.Bold,
            color = when {
                gold -> GoldDeep
                danger -> Color(0xFFFFD9CF)
                else -> GoldWhite
            },
            modifier = Modifier.weight(1f)
        )
        hint?.let {
            Text(text = it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldBright)
        }
    }
}

/** Dashed border (playlist add card, parental target box). */
internal fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = this.drawBehind {
    val strokeWidth = width.toPx()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
        ),
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx())
    )
}
