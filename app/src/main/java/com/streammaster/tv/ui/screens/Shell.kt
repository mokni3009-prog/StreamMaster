package com.streammaster.tv.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.matchcenter.GoalAlert
import com.streammaster.tv.player.remote.RemoteCommand
import com.streammaster.tv.ui.components.MiniPlayerOverlay
import com.streammaster.tv.ui.components.PinDialog
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextShadowCrisp
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.ProfilesViewModel
import com.streammaster.tv.ui.viewmodel.ShellViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shell — CONTRACT §6: dark sidebar with gold active highlight, profile box
 * at the bottom.
 * Sidebar order: Accueil, Recherche, Direct, Films, Séries, EPG, MultiView,
 * Enregistrements, Téléchargements, Matchs en direct | Favoris, Plus tard,
 * Historique, Playlists, Gérer la playlist, Paramètres. NO Catchup. In kids
 * mode the admin items (Playlists, Gérer, Paramètres) are hidden.
 * Model .sidebar: 360dp, gradient rgba(28,20,6,.98)→rgba(10,7,2,.99),
 * 2px right border, shadow; nav item 58dp / 21sp ExtraBold / 25dp icon.
 */
data class SidebarItem(val route: String, val labelRes: Int, @DrawableRes val iconRes: Int)

val SIDEBAR_ITEMS = listOf(
    SidebarItem(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
    SidebarItem(Routes.SEARCH, R.string.nav_search, R.drawable.ic_search),
    SidebarItem(Routes.LIVE, R.string.nav_live, R.drawable.ic_tv),
    SidebarItem(Routes.FILMS, R.string.nav_films, R.drawable.ic_film),
    SidebarItem(Routes.SERIES, R.string.nav_series, R.drawable.ic_layers),
    SidebarItem(Routes.EPG, R.string.nav_epg, R.drawable.ic_calendar),
    SidebarItem(Routes.MULTIVIEW, R.string.nav_multiview, R.drawable.ic_grid),
    SidebarItem(Routes.RECORDINGS, R.string.nav_recordings, R.drawable.ic_video),
    SidebarItem(Routes.DOWNLOADS, R.string.nav_downloads, R.drawable.ic_download),
    SidebarItem(Routes.MATCH_CENTER, R.string.nav_match_center, R.drawable.ic_activity),
    SidebarItem(Routes.MATCH_DAY, R.string.nav_match_day, R.drawable.ic_trending_up),
    SidebarItem(Routes.WATCH_TONIGHT, R.string.nav_watch_tonight, R.drawable.ic_star),
    SidebarItem(Routes.REMOTE, R.string.nav_remote, R.drawable.ic_smartphone),
    SidebarItem(Routes.FAVORIS, R.string.nav_favoris, R.drawable.ic_heart),
    SidebarItem(Routes.WATCH_LATER, R.string.nav_watch_later, R.drawable.ic_clock),
    SidebarItem(Routes.HISTORIQUE, R.string.nav_historique, R.drawable.ic_rotate_ccw),
    SidebarItem(Routes.PLAYLISTS, R.string.nav_playlists, R.drawable.ic_folder),
    SidebarItem(Routes.MANAGE, R.string.nav_manage, R.drawable.ic_list),
    SidebarItem(Routes.DIAGNOSTICS, R.string.nav_diagnostics, R.drawable.ic_server),
    SidebarItem(Routes.EPG_HEALTH, R.string.nav_epg_health, R.drawable.ic_info),
    SidebarItem(Routes.USB_FILES, R.string.nav_usb_files, R.drawable.ic_disc),
    SidebarItem(Routes.CHANNEL_TIMER, R.string.nav_channel_timer, R.drawable.ic_bell),
    SidebarItem(Routes.CLEANUP, R.string.nav_cleanup, R.drawable.ic_trash),
    SidebarItem(Routes.DRIVE, R.string.nav_drive, R.drawable.ic_upload),
    SidebarItem(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings)
)

/** Model .sidebar background: linear-gradient(180deg, rgba(28,20,6,.98), rgba(10,7,2,.99)). */
private val SidebarGradient: Brush = Brush.verticalGradient(
    listOf(Color(0xFA1C1406), Color(0xFC0A0702))
)

/** Model .topbar background: linear-gradient(180deg, rgba(18,13,4,.9), rgba(18,13,4,.3)). */
private val TopBarGradient: Brush = Brush.verticalGradient(
    listOf(Color(0xE6120D04), Color(0x4D120D04))
)

/** Model .profile-box background: linear-gradient(135deg, rgba(229,193,88,.20), rgba(229,193,88,.06)). */
private val ProfileBoxGradient: Brush = Brush.linearGradient(
    colors = listOf(Color(0x33E5C158), Color(0x0FE5C158)),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

@Composable
fun Shell(
    navController: NavHostController,
    currentRoute: String,
    viewModel: ShellViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val activePlaylist by viewModel.activePlaylist.collectAsState()
    val kidsMode by viewModel.kidsMode.collectAsState()
    val kidsPinError by viewModel.kidsPinError.collectAsState()
    // Sidebar count badges (prototype): content counts + favourites + playlists.
    val channelCount by viewModel.channelCount.collectAsState()
    val filmCount by viewModel.filmCount.collectAsState()
    val seriesCount by viewModel.seriesCount.collectAsState()
    val favoriteCount by viewModel.favoriteCount.collectAsState()
    val playlistCount by viewModel.playlistCount.collectAsState()
    // Initial focus (2026-11-23): the first sidebar item, so the D-pad
    // moves from the first press.
    val firstSidebarFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstSidebarFocus.requestFocus() }
    val profilesViewModel: ProfilesViewModel = hiltViewModel()
    val profiles by profilesViewModel.profiles.collectAsState()
    val activeProfileId by profilesViewModel.activeProfileId.collectAsState()
    var profilePickerOpen by remember { mutableStateOf(false) }
    var kidsPinOpen by remember { mutableStateOf(false) }
    var profilePinTarget by remember { mutableStateOf<String?>(null) }
    val profilePinError by profilesViewModel.pinError.collectAsState()
    // In kids mode the admin items (Playlists, Gérer, Paramètres) are hidden.
    val visibleItems = if (kidsMode) {
        SIDEBAR_ITEMS.filter { it.route !in listOf(Routes.PLAYLISTS, Routes.MANAGE, Routes.SETTINGS) }
    } else SIDEBAR_ITEMS
    // Phone web remote (2026-11-22): navigation commands from the phone.
    LaunchedEffect(Unit) {
        viewModel.remoteCommands.collect { cmd ->
            val target = when (cmd) {
                RemoteCommand.GoHome -> Routes.HOME
                RemoteCommand.GoSearch -> Routes.SEARCH
                RemoteCommand.GoEpg -> Routes.EPG
                RemoteCommand.GoSettings -> Routes.SETTINGS
                RemoteCommand.GoDirect -> Routes.LIVE
                RemoteCommand.GoRecordings -> Routes.RECORDINGS
                RemoteCommand.GoFavoris -> Routes.FAVORIS
                RemoteCommand.GoMatchCenter -> Routes.MATCH_CENTER
                else -> null
            }
            if (target != null) {
                navController.navigate(target) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    // Goal alerts (2026-11-22): popup over every screen, auto-dismiss 10s.
    // One alert at a time; later ones wait in the queue.
    var currentGoalAlert by remember { mutableStateOf<GoalAlert?>(null) }
    val goalAlertQueue = remember { mutableStateListOf<GoalAlert>() }
    LaunchedEffect(Unit) {
        viewModel.goalAlerts.collect { goalAlertQueue.add(it) }
    }
    LaunchedEffect(currentGoalAlert, goalAlertQueue.size) {
        if (currentGoalAlert == null && goalAlertQueue.isNotEmpty()) {
            currentGoalAlert = goalAlertQueue.removeAt(0)
        }
    }
    val shownGoalAlert = currentGoalAlert
    if (shownGoalAlert != null) {
        LaunchedEffect(shownGoalAlert.key) {
            delay(10_000)
            if (currentGoalAlert?.key == shownGoalAlert.key) currentGoalAlert = null
        }
    }
    // DND (§4.54) — the gate for ambient overlays lives in Shell: while
    // DND is on, no ambient popup may appear over any screen — EXCEPT
    // the goal alert (§4.33), which is exempt by spec and keeps working.
    // Today GoalAlertPopup is the only ambient overlay Shell owns, so it
    // renders unconditionally below; any future ambient overlay added to
    // Shell must check the DND state first. (The goal alert's system
    // notification when the app is backgrounded lives in
    // GoalAlertService, and toasts in GoldToastHost/AppNavHost — both
    // outside this file, so their DND muting belongs to their owners.)
    // D-03: the old `dndEnabled` collect here was dead — no overlay
    // ever read it — so it was removed instead of kept as a subscription.
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar (model .sidebar: 360dp, gradient, 2px right border, shadow).
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .shadow(14.dp)
                    .background(SidebarGradient)
                    .drawBehind {
                        // 2px right border (line colour).
                        drawLine(
                            color = BorderGold,
                            start = Offset(size.width - 1.dp.toPx(), 0f),
                            end = Offset(size.width - 1.dp.toPx(), size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Brand: 64dp logo + two lines (STREAM gradient 27sp / MASTER 13sp, 6sp spacing) + divider.
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 18.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                        Column {
                            Text(
                                text = "STREAM",
                                style = TextStyle(
                                    brush = Brush.linearGradient(listOf(GoldWhite, Gold)),
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 27.sp,
                                    letterSpacing = 2.5.sp,
                                    // R-S5: the explicit style skipped the
                                    // inherited --text-crisp shadow; add it.
                                    shadow = TextShadowCrisp
                                )
                            )
                            Text(
                                text = "MASTER",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 6.sp,
                                color = GoldWhite,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderGold)
                    )
                }
                // Nav list: scrollable so all 17 items + labels fit in 1080dp.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SidebarSectionLabel(text = stringResource(R.string.nav_section_navigation))
                    visibleItems.forEachIndexed { index, item ->
                        // Prototype sidebar section label: "Bibliothèque" above Favoris.
                        if (item.route == Routes.FAVORIS) {
                            SidebarSectionLabel(text = stringResource(R.string.nav_section_library))
                        }
                        val active = item.route == currentRoute
                        // Prototype count badges (12K / 8K / 2K / 6 / 2/10).
                        val countText: String? = when (item.route) {
                            Routes.LIVE -> if (channelCount > 0) compactCount(channelCount) else null
                            Routes.FILMS -> if (filmCount > 0) compactCount(filmCount) else null
                            Routes.SERIES -> if (seriesCount > 0) compactCount(seriesCount) else null
                            Routes.FAVORIS -> if (favoriteCount > 0) favoriteCount.toString() else null
                            Routes.PLAYLISTS -> if (playlistCount > 0) "$playlistCount/10" else null
                            else -> null
                        }
                        SidebarNavItem(
                            iconRes = item.iconRes,
                            label = stringResource(item.labelRes),
                            active = active,
                            countText = countText,
                            modifier = if (index == 0) Modifier.focusRequester(firstSidebarFocus) else Modifier
                        ) {
                            if (item.route != currentRoute) {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                    if (kidsMode) {
                        SidebarNavItem(
                            iconRes = R.drawable.ic_x,
                            label = stringResource(R.string.exit_kids_mode),
                            active = false,
                            countText = null
                        ) { kidsPinOpen = true }
                    }
                }
                // Profile box at bottom — tap to switch profile.
                // Model .profile-box: gold gradient, 50dp round gold avatar, name 18sp +
                // "Xtream Actif • Gérer" 14sp gold-bright.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(20.dp))
                        .background(ProfileBoxGradient, RoundedCornerShape(20.dp))
                        .border(1.5.dp, BorderGold, RoundedCornerShape(20.dp))
                        .tvFocus()
                        .clickable { profilePickerOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(GoldGradient, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_user),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = GoldDeep
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profiles.firstOrNull { it.id == activeProfileId }?.name
                                ?: activePlaylist?.name ?: stringResource(R.string.profile_name),
                            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = GoldWhite,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.profile_active_manage),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldBright,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            // Content + topbar.
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TopBar(
                    navController = navController,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )
                // Model .view: padding 26px 44px 44px.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 26.dp, start = 44.dp, end = 44.dp, bottom = 44.dp)
                ) { content() }
            }
        }

        if (profilePickerOpen) {
            SelectorDialog(
                title = stringResource(R.string.profiles_title),
                options = profiles.map { SelectorOption(it.id, it.name) },
                selectedId = activeProfileId,
                onSelect = { id ->
                    val profile = profiles.firstOrNull { it.id == id }
                    profilePickerOpen = false
                    if (profile != null && profile.pin.isNotEmpty()) {
                        profilePinTarget = id
                    } else if (profile != null) {
                        profilesViewModel.switchProfile(id)
                    }
                },
                onDismiss = { profilePickerOpen = false }
            )
        }
        if (profilePinTarget != null) {
            PinDialog(
                title = stringResource(R.string.profile_pin_title),
                errorMessage = if (profilePinError) stringResource(R.string.pin_error) else null,
                onComplete = { pin -> profilesViewModel.switchProfileWithPin(profilePinTarget!!, pin) },
                onCancel = { profilePinTarget = null; profilesViewModel.clearPinError() }
            )
        }
        // Close the PIN dialog once the switch succeeded (wrong PIN keeps it open).
        androidx.compose.runtime.LaunchedEffect(activeProfileId, profilePinTarget) {
            if (profilePinTarget != null && activeProfileId == profilePinTarget) profilePinTarget = null
        }
        if (kidsPinOpen) {
            PinDialog(
                title = stringResource(R.string.exit_kids_mode),
                errorMessage = if (kidsPinError) stringResource(R.string.pin_error) else null,
                onComplete = { pin -> viewModel.exitKidsMode(pin) },
                onCancel = { kidsPinOpen = false }
            )
        }
        // Close the kids PIN dialog once kids mode is off (wrong PIN keeps it open).
        androidx.compose.runtime.LaunchedEffect(kidsMode) {
            if (!kidsMode) kidsPinOpen = false
        }
        // Goal alert popup (2026-11-22): small gold card over every screen.
        // DND-exempt (§4.54/§4.33): it shows even when DND is on — hence
        // no DND condition here.
        if (currentGoalAlert != null) {
            GoalAlertPopup(
                alert = currentGoalAlert!!,
                onDismiss = { currentGoalAlert = null }
            )
        }
        // In-app mini player (§4.44): floating 480x270 window over the
        // browsable screens while playback is in PiP mode. It renders
        // nothing when isPip is false.
        MiniPlayerOverlay(navController = navController)
    }
}

/**
 * Goal alert popup (2026-11-22): small gold card, top-center, over every
 * screen. Shows the scorer, the minute and the match with its score.
 * Auto-dismisses after 10 seconds; tapping it dismisses it sooner.
 * It never takes focus — it is informational only.
 */
@Composable
fun GoalAlertPopup(alert: GoalAlert, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(com.streammaster.tv.ui.theme.CardSurface)
                .border(1.dp, BorderGold, RoundedCornerShape(20.dp))
                // It never takes focus (informational only): clickable
                // alone would make it a focus target, so exclude it —
                // tap still dismisses it, and it auto-dismisses in 10s.
                .focusProperties { canFocus = false }
                .clickable { onDismiss() }
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚽ " + stringResource(R.string.goal_alert_goal),
                fontSize = 26.sp, fontWeight = FontWeight.Black, color = Gold
            )
            if (alert.scorer.isNotBlank()) {
                Text(
                    text = alert.scorer + if (alert.minute.isNotBlank()) " — ${alert.minute}" else "",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                )
            } else if (alert.minute.isNotBlank()) {
                Text(
                    text = alert.minute,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                )
            }
            Text(
                text = "${alert.homeTeam} ${alert.homeScore} - ${alert.awayScore} ${alert.awayTeam}",
                fontSize = 20.sp, color = GoldWhite
            )
            if (alert.leagueName.isNotBlank()) {
                Text(text = alert.leagueName, fontSize = 15.sp, color = com.streammaster.tv.ui.theme.TextMuted)
            }
        }
    }
}

/** Prototype count format: 12450 → "12K", 8320 → "8K", 42 → "42". */
private fun compactCount(n: Int): String = if (n >= 1000) "${n / 1000}K" else n.toString()

/** Model .nav-sec-label: 14sp ExtraBold, 3.5sp letter spacing, gold-bright, uppercase. */
@Composable
private fun SidebarSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 3.5.sp,
        color = GoldBright,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)
    )
}

/**
 * Model .nav: 58dp high, 16dp radius, 21sp ExtraBold, 25dp icon.
 * Active: gold gradient + #fff8d6 border + gold shadow.
 * Count badge (.cnt): pill, padding 3×10dp, 16dp radius, black 55% background, 1px border.
 */
@Composable
private fun SidebarNavItem(
    @DrawableRes iconRes: Int,
    label: String,
    active: Boolean,
    countText: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (active) Modifier.shadow(
                    8.dp,
                    RoundedCornerShape(16.dp),
                    ambientColor = Color.Black,
                    spotColor = Color(0xFFE5C158)
                ) else Modifier
            )
            .then(
                if (active) Modifier.background(GoldGradient, RoundedCornerShape(16.dp))
                else Modifier.background(Color.Transparent, RoundedCornerShape(16.dp))
            )
            .border(
                1.5.dp,
                if (active) Color(0xFFFFF8D6) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(25.dp),
            tint = if (active) GoldDeep else GoldBright
        )
        Text(
            text = label,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) GoldDeep else GoldWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (countText != null) {
            Box(
                modifier = Modifier
                    .background(
                        if (active) Color(0x381A1100) else Color(0x8C000000),
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        if (active) Color(0x661A1100) else BorderGold,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = if (active) GoldDeep else GoldBright
                )
            }
        }
    }
}

/**
 * Topbar (model .topbar): 104dp high, 44dp horizontal padding, bottom
 * border, gradient background; search pill (max 540dp, 58dp, radius 29,
 * 2px border, 19sp, ic_search) + "Flux 4K Actif" pill with pulsing dot +
 * latency pill (ic_wifi, live OkHttp HEAD via ShellViewModel) + clock
 * (Georgia 40sp Black) and date (16sp gold-bright). Both pills are hidden
 * when there is no active playlist; latency shows "— ms" before the first
 * measurement.
 */
@Composable
private fun TopBar(
    navController: NavHostController,
    viewModel: ShellViewModel,
    modifier: Modifier = Modifier
) {
    val activePlaylist by viewModel.activePlaylist.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()
    // UI scaling (2026-11-23): on the narrowest canvas class the latency
    // pill is hidden first (Flux + clock stay) so the search pill keeps room.
    val narrow = LocalConfiguration.current.screenWidthDp < 700
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    // T-04: wake at the next minute boundary, not every 30s — the clock
    // (minutes) was up to 30s late and the date at midnight likewise.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            nowMillis = now
            delay(60_000 - (now % 60_000))
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
    // Model .pill .dot: 11dp gold-bright dot, pulse 1.6s (opacity 1 → .35).
    val pulse = rememberInfiniteTransition(label = "fluxDot")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "fluxDotAlpha"
    )
    Row(
        modifier = modifier
            .height(104.dp)
            .background(TopBarGradient)
            .drawBehind {
                drawLine(
                    color = Color(0x29FFE89E),
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search pill.
        Row(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 540.dp)
                .height(58.dp)
                .background(Color(0xE6140E04), RoundedCornerShape(29.dp))
                .border(2.dp, BorderGold, RoundedCornerShape(29.dp))
                .tvFocus()
                .clickable { navController.navigate(Routes.SEARCH) }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = GoldBright
            )
            Text(
                text = stringResource(R.string.search_hint),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = GoldSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (activePlaylist != null) {
            // "Flux 4K Actif" pill (model .pill: 52dp, radius 26, 1.5px border, 18sp ExtraBold).
            Row(
                modifier = Modifier
                    .height(52.dp)
                    .background(Color(0xE61C1406), RoundedCornerShape(26.dp))
                    .border(1.5.dp, BorderGold, RoundedCornerShape(26.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .shadow(6.dp, CircleShape, spotColor = GoldBright)
                        .background(GoldBright, CircleShape)
                )
                Text(
                    text = stringResource(R.string.topbar_flux),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldWhite
                )
            }
            // Latency pill (ic_wifi + live ms) — hidden first on narrow canvases.
            if (!narrow) Row(
                modifier = Modifier
                    .height(52.dp)
                    .background(Color(0xE61C1406), RoundedCornerShape(26.dp))
                    .border(1.5.dp, BorderGold, RoundedCornerShape(26.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = GoldBright
                )
                Text(
                    text = latencyMs?.let { stringResource(R.string.topbar_latency, it) }
                        ?: stringResource(R.string.topbar_latency_unknown),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldWhite
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeFormat.format(Date(nowMillis)),
                fontFamily = FontFamily.Serif,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = GoldWhite
            )
            Text(
                text = dateFormat.format(Date(nowMillis)),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldBright,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
