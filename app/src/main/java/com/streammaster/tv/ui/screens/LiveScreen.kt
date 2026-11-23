package com.streammaster.tv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.showToast
import com.streammaster.tv.data.model.Bouquet
import com.streammaster.tv.data.model.Channel
import com.streammaster.tv.data.repository.toBouquet
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocusBorderless
import com.streammaster.tv.ui.viewmodel.LiveViewModel
import com.streammaster.tv.ui.viewmodel.MyPlaylistViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live (Direct) — model structure (stream-master.html, .live-workspace):
 * one column: top bouquet strip (.cat-strip) + a row with the channel
 * list (580dp) on the left and the preview column (viewport + EPG panel)
 * on the right. Single click selects + previews a channel; a second
 * click within 500ms (or the «Lancer» button) opens the fullscreen
 * player. Digit keys open the channel by number (custom number first,
 * then built-in), with a temporary OSD.
 *
 * Channel logos: `Channel.logoUrl` (Xtream `stream_icon`, Stalker
 * `logo`, M3U `tvg-logo`, stored at sync time, DB v7) is shown with
 * Coil in the 66×66 logo box, falling back to the 2-letter `code` on
 * the gold gradient when the provider supplies no logo or the load
 * fails. «SIGNAL FAIBLE» is shown in the channel card's title row,
 * exactly where the model puts it (`.chan-warn`, after the quality
 * badge and ★), when `Channel.isBroken` is true — the model's own
 * per-channel flag. Note: nothing in the app sets `isBroken` yet
 * (no Dao setter / failure hook), so the badge stays hidden until a
 * later wave revives the flag; live bandwidth (`networkSpeedLabel`)
 * belongs to the player session and cannot apply to this static
 * preview list.
 */
@Composable
fun LiveScreen(
    navController: NavHostController,
    viewModel: LiveViewModel = hiltViewModel(),
    myPlaylistViewModel: MyPlaylistViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val epg by viewModel.epg.collectAsState()
    val context = LocalContext.current
    // §4.62 — "My Playlist" (virtual #11) vs the active subscription.
    val myLiveBouquets by myPlaylistViewModel.liveBouquets.collectAsState()
    val myChannelsAll by myPlaylistViewModel.myChannels.collectAsState()
    val myChannelBouquet by myPlaylistViewModel.myChannelBouquet.collectAsState()
    val activePlaylist by myPlaylistViewModel.activePlaylist.collectAsState()
    val allActiveChannels by myPlaylistViewModel.allActiveChannels.collectAsState()
    // One source is browsed at a time; the flèche sections below switch it.
    var mySectionOpen by remember { mutableStateOf(true) }
    var activeSectionOpen by remember { mutableStateOf(false) }
    var mySource by remember { mutableStateOf(false) }
    var myBouquetId by remember { mutableStateOf<Long?>(null) }
    var myChannelId by remember { mutableStateOf<String?>(null) }
    // Displayed bouquets/channels follow the selected source.
    val displayBouquets: List<Bouquet> =
        if (mySource) myLiveBouquets.map { it.toBouquet() } else state.bouquets
    val displayChannels: List<Channel> = if (mySource) {
        if (myBouquetId == null) myChannelsAll
        else myChannelsAll.filter { myChannelBouquet[it.id] == myBouquetId }
    } else state.channels
    val displaySelectedChannel: Channel? = if (mySource) {
        displayChannels.firstOrNull { it.id == myChannelId } ?: displayChannels.firstOrNull()
    } else state.selectedChannel
    // Focus restore (§4.1): PlayerScreen writes "focusedId" into this
    // entry's savedStateHandle when leaving the player.
    val listState = rememberLazyListState()
    val channelFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val focusedId = savedStateHandle?.get<String>("focusedId")
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(focusedId, displayChannels, myChannelsAll, allActiveChannels) {
        if (focusedId == null || focusRestored) return@LaunchedEffect
        // Switching source changes displayChannels, so the effect restarts
        // with the new list; never conclude on the stale list.
        var switched = false
        if (myChannelsAll.any { it.id == focusedId }) {
            if (!mySource || myBouquetId != myChannelBouquet[focusedId] || myChannelId != focusedId) switched = true
            mySource = true
            myBouquetId = myChannelBouquet[focusedId]
            myChannelId = focusedId
            viewModel.selectChannel(focusedId) // feeds the EPG flow
        } else {
            val sourceChannel = allActiveChannels.firstOrNull { it.id == focusedId }
            if (sourceChannel != null) {
                if (mySource || state.selectedBouquetId != sourceChannel.bouquetId) switched = true
                mySource = false
                viewModel.selectBouquet(sourceChannel.bouquetId)
                viewModel.selectChannel(focusedId)
            }
        }
        if (switched) return@LaunchedEffect
        val idx = displayChannels.indexOfFirst { it.id == focusedId }
        if (idx >= 0) {
            listState.scrollToItem(idx)
            delay(120)
            channelFocusRequesters[focusedId]?.requestFocus()
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
        } else if (myChannelsAll.isNotEmpty() && allActiveChannels.isNotEmpty()) {
            // Both lists loaded and the id is in neither: nothing to restore.
            focusRestored = true
            savedStateHandle?.remove<String>("focusedId")
        }
    }
    // Keep the EPG flow on the displayed channel when the browsed source
    // (or bouquet) changes — otherwise the panel would show the previous
    // source's programs under a My Playlist channel.
    LaunchedEffect(mySource, myBouquetId, displaySelectedChannel?.id) {
        displaySelectedChannel?.let { viewModel.selectChannel(it.id) }
    }
    val narrow = LocalConfiguration.current.screenWidthDp < 700
    val scope = rememberCoroutineScope()
    var digitBuffer by remember { mutableStateOf("") }
    // Double-click detection (V11): last OK click on the same channel.
    var lastClickChannelId by remember { mutableStateOf<String?>(null) }
    var lastClickMillis by remember { mutableStateOf(0L) }
    // Initial focus: the first bouquet pill (first focusable of the screen).
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    fun openChannel(channelId: String) {
        navController.navigate(Routes.player("live", channelId))
    }
    fun onChannelClick(channel: Channel) {
        val nowMs = System.currentTimeMillis()
        if (lastClickChannelId == channel.id && nowMs - lastClickMillis < 500) {
            lastClickMillis = 0L
            openChannel(channel.id)
        } else {
            if (mySource) myChannelId = channel.id
            viewModel.selectChannel(channel.id)
            lastClickChannelId = channel.id
            lastClickMillis = nowMs
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    val digit = when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'; KeyEvent.KEYCODE_2 -> '2'
                        KeyEvent.KEYCODE_3 -> '3'; KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
                        KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'; KeyEvent.KEYCODE_8 -> '8'
                        KeyEvent.KEYCODE_9 -> '9'; else -> null
                    } ?: return@onPreviewKeyEvent false
                    digitBuffer = (digitBuffer + digit).take(2)
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        val found = viewModel.resolveChannelNumber(digitBuffer)
                        if (found != null) openChannel(found.id)
                        else showToast(context, context.getString(R.string.channel_not_found))
                        digitBuffer = ""
                    }
                    true
                }
        ) {
            // §4.62 — two named sections with a flèche (▸/▾): "My Playlist"
            // first, then the active subscription by name. The first click
            // opens the bouquet dropdown, the second hides it; picking a
            // bouquet browses that playlist's content (the strip below
            // follows the selected source).
            PlaylistSectionHeader(
                title = stringResource(R.string.my_playlist_name),
                count = myLiveBouquets.size,
                expanded = mySectionOpen,
                modifier = Modifier.padding(bottom = 10.dp),
                onToggle = { mySectionOpen = !mySectionOpen }
            )
            if (mySectionOpen) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SortPill(stringResource(R.string.live_all), mySource && myBouquetId == null) {
                            mySource = true; myBouquetId = null; myChannelId = null
                        }
                    }
                    items(myLiveBouquets) { bouquet ->
                        SortPill(bouquet.name, mySource && myBouquetId == bouquet.id) {
                            mySource = true; myBouquetId = bouquet.id; myChannelId = null
                        }
                    }
                }
            }
            PlaylistSectionHeader(
                title = activePlaylist?.name.orEmpty(),
                count = state.bouquets.size,
                expanded = activeSectionOpen,
                modifier = Modifier.padding(bottom = 10.dp),
                onToggle = { activeSectionOpen = !activeSectionOpen }
            )
            if (activeSectionOpen) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SortPill(stringResource(R.string.live_all), !mySource && state.selectedBouquetId == null) {
                            mySource = false; viewModel.selectAll()
                        }
                    }
                    items(state.bouquets) { bouquet ->
                        SortPill(bouquet.name, !mySource && bouquet.id == state.selectedBouquetId) {
                            mySource = false; viewModel.selectBouquet(bouquet.id)
                        }
                    }
                }
            }
            // V02 — bouquet strip (.cat-pill): 50dp high, radius 25, 19sp ExtraBold.
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    val allSelected = if (mySource) myBouquetId == null else state.selectedBouquetId == null
                    Box(
                        modifier = Modifier.height(50.dp)
                            .focusRequester(firstFocus)
                            .clip(RoundedCornerShape(25.dp))
                            .then(
                                if (allSelected) Modifier.background(GoldGradient)
                                    .border(2.dp, Color(0xFFFFF8D6), RoundedCornerShape(25.dp))
                                else Modifier.background(Color(0xEB1A1205))
                                    .border(2.dp, BorderGold, RoundedCornerShape(25.dp))
                            )
                            .tvFocusBorderless()
                            .clickable {
                                if (mySource) { myBouquetId = null; myChannelId = null }
                                else viewModel.selectAll()
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.live_all), fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (allSelected) GoldDeep else GoldWhite
                        )
                    }
                }
                itemsIndexed(displayBouquets) { _, bouquet ->
                    // Strip bouquets are §3 Bouquet (String id); My Playlist
                    // row ids are Long, stringified by toBouquet().
                    val selected = if (mySource) bouquet.id == myBouquetId?.toString() else bouquet.id == state.selectedBouquetId
                    Box(
                        modifier = Modifier.height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .then(
                                if (selected) Modifier.background(GoldGradient)
                                    .border(2.dp, Color(0xFFFFF8D6), RoundedCornerShape(25.dp))
                                else Modifier.background(Color(0xEB1A1205))
                                    .border(2.dp, BorderGold, RoundedCornerShape(25.dp))
                            )
                            .tvFocusBorderless()
                            .clickable {
                                if (mySource) { myBouquetId = bouquet.id.toLongOrNull(); myChannelId = null }
                                else viewModel.selectBouquet(bouquet.id)
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bouquet.name, fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) GoldDeep else GoldWhite
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // Left — channel list (580dp) + usage hint (V03).
                // UI scaling (2026-11-23): 580dp is a cap, not a forced
                // width — weight 0.671 reproduces 580dp at the 1472dp
                // reference content width (0.671/1.671 × 1444 ≈ 580).
                Column(modifier = Modifier.weight(0.671f).widthIn(max = 580.dp).fillMaxHeight()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.live_tip_astuce), fontSize = 14.sp,
                            fontWeight = FontWeight.Black, color = GoldDeep,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(GoldGradient).padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                        Text(
                            text = stringResource(R.string.live_hint), fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold, color = GoldBright
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 6.dp, end = 12.dp, bottom = 16.dp
                        )
                    ) {
                        items(displayChannels) { channel ->
                            ChannelCard(
                                channel = channel,
                                selected = channel.id == displaySelectedChannel?.id,
                                modifier = Modifier.focusRequester(
                                    channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                                ),
                                onClick = { onChannelClick(channel) }
                            )
                        }
                    }
                }
                // Right — preview column: viewport (V07) + EPG panel (V08).
                // Flexible split (2026-11-23): the fixed 420dp viewport
                // left the weight(1f) EPG panel only ~42dp on the narrow
                // 1280x720 screen (640x360dp) against ~326dp of content.
                // At the 1920×1080 reference the viewport is a fixed
                // 420dp and the panel fills the rest (~402dp); on the
                // narrow screen the weights (viewport 0.55 / panel 0.45)
                // give the panel ~208dp.
                val channel = displaySelectedChannel
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    LiveViewport(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (narrow) Modifier.weight(0.55f) else Modifier.height(420.dp)),
                        channel = channel,
                        epgNowTitle = epg.firstOrNull {
                            it.startMillis <= System.currentTimeMillis() && it.endMillis > System.currentTimeMillis()
                        }?.title ?: channel?.nowTitle.orEmpty(),
                        onLaunch = { channel?.let { openChannel(it.id) } },
                        onToggleFavorite = {
                            if (mySource) channel?.let { myPlaylistViewModel.toggleChannelFavorite(it) }
                            else viewModel.toggleFavorite()
                        }
                    )
                    if (channel != null) {
                        LiveEpgPanel(channel = channel, epg = epg, modifier = Modifier.fillMaxWidth().then(if (narrow) Modifier.weight(0.45f) else Modifier.weight(1f)))
                    }
                }
            }
        }
        // V10 — digit-entry OSD (.live-num-osd): dark bg, 2px border,
        // radius 16, padding 12×22, 24sp Black.
        if (digitBuffer.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE0080501))
                    .border(2.dp, BorderStrong, RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.channel_osd, digitBuffer),
                    fontSize = 24.sp, fontWeight = FontWeight.Black, color = GoldWhite
                )
            }
        }
    }
}

/** §4.62 — named playlist section header with a flèche (▸ collapsed /
 *  ▾ expanded). First click opens the bouquet dropdown, second click
 *  hides it. Shared by Live / Films / Series (same package). */
@Composable
internal fun PlaylistSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEB1A1205))
            .border(2.dp, BorderGold, RoundedCornerShape(16.dp))
            .tvFocusBorderless()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (expanded) "▾" else "▸", fontSize = 22.sp,
            fontWeight = FontWeight.Black, color = GoldBright
        )
        Text(
            text = title, fontSize = 20.sp, fontWeight = FontWeight.Black,
            color = GoldWhite, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "$count", fontSize = 16.sp, fontWeight = FontWeight.Black,
            color = GoldDeep,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .background(GoldGradient)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** V04/V05 — channel card (.chan-card): number 21sp + 66×66 logo box +
 *  title 22sp + quality badge + ★ + current program 18sp + 6dp progress
 *  bar + «EN LECTURE» tag (visible only when selected). Selected: full
 *  gold gradient + white border + scale 1.02 / translateX 5dp + 4dp
 *  #FFF8D6 halo + 42dp glow, all text turned black. */
@Composable
private fun ChannelCard(channel: Channel, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val black = Color(0xFF171000)
    val density = LocalDensity.current
    val translateX = with(density) { 5.dp.toPx() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (selected) { scaleX = 1.02f; scaleY = 1.02f; translationX = translateX }
            }
            .then(
                if (selected) Modifier
                    .shadow(42.dp, RoundedCornerShape(20.dp), ambientColor = Color(0xFFFFF8D6), spotColor = GoldBright)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GoldGradient)
                    .border(4.dp, Color(0xFFFFF8D6), RoundedCornerShape(20.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                else Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xF01C1406), Color(0xF50E0A03))
                        )
                    )
                    .border(2.dp, Color(0x47FFE89E), RoundedCornerShape(20.dp))
            )
            .tvFocusBorderless()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val displayNum = channel.customNum?.toString() ?: channel.num
        Text(
            text = displayNum, fontSize = 21.sp, fontWeight = FontWeight.Black,
            color = if (selected) black else GoldBright,
            modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        // Logo (66×66): the provider logo via Coil when `logoUrl` is
        // set, falling back to the 2-letter `code` on the gold gradient
        // when there is no logo or the load fails.
        var logoFailed by remember(channel.id) { mutableStateOf(false) }
        Box(
            modifier = Modifier.size(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) Modifier.background(black)
                        .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    else Modifier.background(GoldGradient)
                )
                .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logoUrl.isNullOrBlank() && !logoFailed) {
                AsyncImage(
                    model = channel.logoUrl, contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
            } else {
                Text(
                    text = channel.code, fontSize = 24.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif,
                    color = if (selected) GoldBright else GoldDeep
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = channel.customName ?: channel.name, fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (selected) black else GoldWhite,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (channel.quality.isNotBlank()) {
                    Text(
                        text = channel.quality, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        color = if (selected) GoldBright else GoldWhite,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .then(
                                if (selected) Modifier.background(black)
                                else Modifier.background(Color(0x38E5C158))
                                    .border(1.dp, GoldBright, RoundedCornerShape(6.dp))
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (channel.isFavorite) {
                    Text(text = "★", fontSize = 18.sp, color = if (selected) black else GoldBright)
                }
                // «SIGNAL FAIBLE» (.chan-warn, model chanCardHTML): after
                // the quality badge and ★, gated on Channel.isBroken.
                if (channel.isBroken) {
                    Text(
                        text = "SIGNAL FAIBLE", fontSize = 11.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                        color = if (selected) Color(0xFFFF9D86) else Color(0xFF1B1200),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (selected) black else Color(0xFFFF6A4D))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (channel.nowTitle.isNotBlank()) {
                Text(
                    text = channel.nowTitle + if (channel.nowTime.isNotBlank()) " • ${channel.nowTime}" else "",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) black else GoldBright,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                // 6dp progress bar (.chan-mini-bar).
                val fraction = (channel.progress.coerceIn(0, 100) / 100f)
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) Color(0x4D171000) else Color(0xBF000000))
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                            .then(if (selected) Modifier.background(black) else Modifier.background(GoldGradient))
                    )
                }
            }
        }
        // «EN LECTURE» tag — hidden until the channel is selected (model .chan-status-tag).
        if (selected) {
            Text(
                text = stringResource(R.string.live_playing_tag), fontSize = 13.sp,
                fontWeight = FontWeight.Black, color = GoldBright,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(black)
                    .border(1.dp, GoldBright, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** V07 — preview window (.live-viewport): radius 26,
 *  2.5px border, pulsing «EN DIRECT» pill + tech pill on top, bottom
 *  bar with the «Now playing» title + gold «Lancer» and «Favori»
 *  buttons. Flexible height (2026-11-23): fixed 420dp at the
 *  reference, weight 0.55 of the preview column on the narrow screen
 *  (~254dp).
 *  A single click on a channel only selects/previews it — the
 *  viewport itself does NOT open the player. */
@Composable
private fun LiveViewport(
    modifier: Modifier = Modifier,
    channel: Channel?,
    epgNowTitle: String,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "liveDotAlpha"
    )
    Box(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF050401))
            .border(2.5.dp, BorderStrong, RoundedCornerShape(26.dp))
            .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
    ) {
        // Radial gold glow behind the play art (model .live-viewport svg).
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0.0f to Color(0x73FFE89E),
                    0.6f to Color(0x2E9C6F08),
                    1.0f to Color(0xFF050301)
                )
            )
        )
        // Centre art (model `.live-viewport svg.screen-art`,
        // viewBox 900×420): outer circle r=120, inner circle r=72 and
        // solid gold play triangle (points 436,172 436,228 480,200),
        // all centred on (450,200). Scaled to the 120dp outer circle
        // (model outer diameter 240 units → 0.5×): inner 72dp,
        // triangle 22×28dp.
        Box(
            modifier = Modifier.align(Alignment.Center).size(120.dp)
                .border(3.dp, GoldBright.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(72.dp)
                    .border(3.dp, GoldBright.copy(alpha = 0.4f), CircleShape)
            )
            Canvas(modifier = Modifier.size(22.dp, 28.dp)) {
                drawPath(
                    path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(0f, size.height)
                        lineTo(size.width, size.height / 2f)
                        close()
                    },
                    color = GoldBright
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE00C0802))
                    .border(1.5.dp, GoldBright, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(11.dp)
                        .clip(CircleShape)
                        .background(GoldBright.copy(alpha = dotAlpha))
                )
                Text(
                    text = stringResource(R.string.live_badge), fontSize = 16.sp,
                    fontWeight = FontWeight.Black, color = GoldWhite
                )
            }
            if (channel != null && channel.quality.isNotBlank()) {
                Text(
                    text = channel.quality, fontSize = 16.sp, fontWeight = FontWeight.Black,
                    color = GoldBright,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(Color(0xE00C0802))
                        .border(1.5.dp, BorderGold, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val nowText = epgNowTitle.ifBlank { stringResource(R.string.live_select_channel) }
            Text(
                text = "${stringResource(R.string.live_now_playing)} : $nowText",
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xDB080602))
                    .border(1.dp, BorderGold, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(GoldGradient)
                        .border(2.dp, Color(0xFFFFF8D6), RoundedCornerShape(25.dp))
                        .tvFocusBorderless()
                        .clickable { onLaunch() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null, tint = GoldDeep, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.live_launch), fontSize = 17.sp,
                        fontWeight = FontWeight.Black, color = GoldDeep
                    )
                }
                Row(
                    modifier = Modifier.height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color(0xE60E0A03))
                        .border(2.dp, GoldBright, RoundedCornerShape(25.dp))
                        .tvFocusBorderless()
                        .clickable { onToggleFavorite() }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = null, tint = GoldBright, modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.favorite), fontSize = 17.sp,
                        fontWeight = FontWeight.Black, color = GoldWhite
                    )
                }
            }
        }
    }
}

/** V08 — EPG panel below the viewport (.live-epg-panel): gradient card
 *  with 2px border, header (58dp logo + 28sp name + «Chaîne N° 01 •
 *  Cinéma»), current program (21sp title + time + description + 10dp
 *  bar) and the «next» list in a 2-column grid. Data comes from the
 *  EPG flow (LiveViewModel.epg); Channel.nowTitle/nowTime/description
 *  are the fallback when the EPG has no program for this channel. */
@Composable
private fun LiveEpgPanel(
    channel: Channel,
    epg: List<com.streammaster.tv.data.model.EpgProgram>,
    modifier: Modifier = Modifier
) {
    val nowMs = System.currentTimeMillis()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val nowProg = epg.firstOrNull { it.startMillis <= nowMs && it.endMillis > nowMs }
    val nextProgs = epg.filter { it.endMillis > nowMs && it != nowProg }.take(4)
    val nowTitle = nowProg?.title ?: channel.nowTitle
    val nowTime = if (nowProg != null) {
        "${timeFormat.format(Date(nowProg.startMillis))} – ${timeFormat.format(Date(nowProg.endMillis))}"
    } else channel.nowTime
    val nowDesc = nowProg?.description?.takeIf { it.isNotBlank() } ?: channel.description
    val fraction = if (nowProg != null && nowProg.endMillis > nowProg.startMillis) {
        ((nowMs - nowProg.startMillis).toFloat() / (nowProg.endMillis - nowProg.startMillis)).coerceIn(0f, 1f)
    } else channel.progress.coerceIn(0, 100) / 100f
    val displayNum = channel.customNum?.toString() ?: channel.num

    // R-B2: CardSurface with the panel's own 24dp shape, gradient and 2px
    // border, so it carries the --card-shadow.
    CardSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        background = Brush.linearGradient(
            listOf(Color(0xF21E1606), Color(0xFA0D0903))
        ),
        border = BorderStroke(2.dp, BorderGold)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Logo (58dp): provider logo via Coil, code fallback.
            var panelLogoFailed by remember(channel.id) { mutableStateOf(false) }
            Box(
                modifier = Modifier.size(58.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(GoldGradient),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank() && !panelLogoFailed) {
                    AsyncImage(
                        model = channel.logoUrl, contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onError = { panelLogoFailed = true },
                        modifier = Modifier.fillMaxSize().padding(5.dp)
                    )
                } else {
                    Text(
                        text = channel.code, fontSize = 22.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, color = GoldDeep
                    )
                }
            }
            Column {
                Text(
                    text = channel.customName ?: channel.name, fontSize = 28.sp,
                    fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                    color = GoldWhite, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.live_channel_number, displayNum, channel.category),
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = GoldBright
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(1.5.dp)
                .background(Color(0x40FFE89E))
        )
        if (nowTitle.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = nowTitle, fontSize = 21.sp, fontWeight = FontWeight.Black,
                    color = GoldWhite, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = nowTime, fontSize = 19.sp, fontWeight = FontWeight.Black,
                    color = GoldBright
                )
            }
            if (nowDesc.isNotBlank()) {
                Text(
                    text = nowDesc, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = GoldSoft, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xCC000000))
                    .border(1.dp, BorderGold, RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                        .background(GoldGradient)
                )
            }
        } else {
            Text(text = stringResource(R.string.no_epg), fontSize = 18.sp, color = TextMuted)
        }
        // «Next» list — 2-column grid (EPG rows first, Channel.next fallback).
        val nextItems: List<Pair<String, String>> = if (nextProgs.isNotEmpty()) {
            nextProgs.map { timeFormat.format(Date(it.startMillis)) to it.title }
        } else channel.next.take(4)
        if (nextItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                nextItems.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { (time, title) ->
                            Row(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color(0x80000000))
                                    .border(1.dp, BorderGold, RoundedCornerShape(13.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = time, fontSize = 14.sp, fontWeight = FontWeight.Black,
                                    color = GoldBright
                                )
                                Text(
                                    text = title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                                    color = GoldWhite, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        }
    }
}
