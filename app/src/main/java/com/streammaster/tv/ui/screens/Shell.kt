package com.streammaster.tv.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.data.matchcenter.GoalAlert
import com.streammaster.tv.player.remote.RemoteCommand
import com.streammaster.tv.ui.components.PinDialog
import com.streammaster.tv.ui.components.SelectorDialog
import com.streammaster.tv.ui.components.SelectorOption
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.CardSurface
import com.streammaster.tv.ui.theme.Gold
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.ProfilesViewModel
import com.streammaster.tv.ui.viewmodel.ShellViewModel
import kotlinx.coroutines.delay

/**
 * Shell — CONTRACT §6: dark sidebar with gold active highlight, profile box
 * at the bottom.
 * Sidebar order: Accueil, Recherche, Direct, Films, Séries, EPG, MultiView,
 * Enregistrements, Téléchargements, Matchs en direct | Favoris, Plus tard,
 * Historique, Playlists, Gérer la playlist, Paramètres. NO Catchup. In kids
 * mode the admin items (Playlists, Gérer, Paramètres) are hidden.
 */
data class SidebarItem(val route: String, val labelRes: Int, val icon: String)

val SIDEBAR_ITEMS = listOf(
    SidebarItem(Routes.HOME, R.string.nav_home, "⌂"),
    SidebarItem(Routes.SEARCH, R.string.nav_search, "⌕"),
    SidebarItem(Routes.LIVE, R.string.nav_live, "▶"),
    SidebarItem(Routes.FILMS, R.string.nav_films, "▣"),
    SidebarItem(Routes.SERIES, R.string.nav_series, "▤"),
    SidebarItem(Routes.EPG, R.string.nav_epg, "▦"),
    SidebarItem(Routes.MULTIVIEW, R.string.nav_multiview, "⊞"),
    SidebarItem(Routes.RECORDINGS, R.string.nav_recordings, "●"),
    SidebarItem(Routes.DOWNLOADS, R.string.nav_downloads, "↓"),
    SidebarItem(Routes.MATCH_CENTER, R.string.nav_match_center, "⚽"),
    SidebarItem(Routes.REMOTE, R.string.nav_remote, "📱"),
    SidebarItem(Routes.FAVORIS, R.string.nav_favoris, "♥"),
    SidebarItem(Routes.WATCH_LATER, R.string.nav_watch_later, "◷"),
    SidebarItem(Routes.HISTORIQUE, R.string.nav_historique, "↺"),
    SidebarItem(Routes.PLAYLISTS, R.string.nav_playlists, "☰"),
    SidebarItem(Routes.MANAGE, R.string.nav_manage, "✎"),
    SidebarItem(Routes.SETTINGS, R.string.nav_settings, "⚙")
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
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(CardSurface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    letterSpacing = 1.2.sp,
                    color = Gold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                visibleItems.forEach { item ->
                    val active = item.route == currentRoute
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) Gold else androidx.compose.ui.graphics.Color.Transparent)
                            .tvFocus()
                            .clickable {
                                if (item.route != currentRoute) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.icon, fontSize = 20.sp,
                            color = if (active) GoldDeep else GoldWhite,
                            modifier = Modifier.width(32.dp)
                        )
                        Text(
                            text = stringResource(item.labelRes), fontSize = 18.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) GoldDeep else GoldWhite
                        )
                    }
                }
                if (kidsMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(BorderGold.copy(alpha = 0.25f))
                            .tvFocus()
                            .clickable { kidsPinOpen = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✕", fontSize = 20.sp, color = Gold, modifier = Modifier.width(32.dp))
                        Text(text = stringResource(R.string.exit_kids_mode), fontSize = 18.sp, color = GoldWhite)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // Profile box at bottom — tap to switch profile.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BorderGold.copy(alpha = 0.25f))
                        .tvFocus()
                        .clickable { profilePickerOpen = true }
                        .padding(16.dp)
                ) {
                    Text(
                        text = profiles.firstOrNull { it.id == activeProfileId }?.name
                            ?: activePlaylist?.name ?: stringResource(R.string.profile_name),
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GoldWhite
                    )
                    Text(
                        text = activePlaylist?.let {
                            stringResource(R.string.profile_expiry, it.expiryDate)
                        } ?: stringResource(R.string.profile_status),
                        fontSize = 14.sp, color = TextMuted
                    )
                }
            }
            // Content
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp)) { content() }
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
        if (currentGoalAlert != null) {
            GoalAlertPopup(
                alert = currentGoalAlert!!,
                onDismiss = { currentGoalAlert = null }
            )
        }
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
                .background(CardSurface)
                .border(1.dp, BorderGold, RoundedCornerShape(20.dp))
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
                Text(text = alert.leagueName, fontSize = 15.sp, color = TextMuted)
            }
        }
    }
}
