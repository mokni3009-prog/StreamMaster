package com.streammaster.tv.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.streammaster.tv.ui.components.GoldToastHost
import com.streammaster.tv.ui.screens.ChannelTimerScreen
import com.streammaster.tv.ui.screens.CleanupScreen
import com.streammaster.tv.ui.screens.DetailScreen
import com.streammaster.tv.ui.screens.DiagnosticsScreen
import com.streammaster.tv.ui.screens.DownloadsScreen
import com.streammaster.tv.ui.screens.DriveScreen
import com.streammaster.tv.ui.screens.EpgHealthScreen
import com.streammaster.tv.ui.screens.EpgScreen
import com.streammaster.tv.ui.screens.FavorisScreen
import com.streammaster.tv.ui.screens.FilmsScreen
import com.streammaster.tv.ui.screens.HistoriqueScreen
import com.streammaster.tv.ui.screens.HomeScreen
import com.streammaster.tv.ui.screens.LiveScreen
import com.streammaster.tv.ui.screens.LoadingScreen
import com.streammaster.tv.ui.screens.LoginScreen
import com.streammaster.tv.ui.screens.ManageScreen
import com.streammaster.tv.ui.screens.MatchCenterScreen
import com.streammaster.tv.ui.screens.MatchDayScreen
import com.streammaster.tv.ui.screens.MultiViewScreen
import com.streammaster.tv.ui.screens.OnboardingWizardScreen
import com.streammaster.tv.ui.screens.PlayerScreen
import com.streammaster.tv.ui.screens.ParentalScreen
import com.streammaster.tv.ui.screens.PlaylistsScreen
import com.streammaster.tv.ui.screens.RecordingsScreen
import com.streammaster.tv.ui.screens.RemoteScreen
import com.streammaster.tv.ui.screens.SearchScreen
import com.streammaster.tv.ui.screens.SeriesScreen
import com.streammaster.tv.ui.screens.SettingsScreen
import com.streammaster.tv.ui.screens.Shell
import com.streammaster.tv.ui.screens.SplashScreen
import com.streammaster.tv.ui.screens.UsbFilesScreen
import com.streammaster.tv.ui.screens.WatchLaterScreen
import com.streammaster.tv.ui.screens.WatchTonightScreen
import com.streammaster.tv.ui.viewmodel.DriveBackupViewModel

/**
 * NavHost — CONTRACT §6. Start destination SPLASH.
 * Sidebar routes are wrapped in Shell (sidebar + content); Player and Detail
 * are full-screen routes WITHOUT the sidebar.
 */
@Composable
fun AppNavHost(navController: NavHostController) {
    // R-M2: the gold toast host overlays the whole NavHost, so it covers
    // every screen — including Splash / Login / Loading.
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) { SplashScreen(navController) }
        composable(Routes.LOGIN) { LoginScreen(navController) }
        composable(Routes.LOADING) { LoadingScreen(navController) }

        composable(Routes.HOME) { Shell(navController, Routes.HOME) { HomeScreen(navController) } }
        composable(Routes.SEARCH) { Shell(navController, Routes.SEARCH) { SearchScreen(navController) } }
        composable(Routes.LIVE) { Shell(navController, Routes.LIVE) { LiveScreen(navController) } }
        composable(Routes.FILMS) { Shell(navController, Routes.FILMS) { FilmsScreen(navController) } }
        composable(Routes.SERIES) { Shell(navController, Routes.SERIES) { SeriesScreen(navController) } }
        composable(Routes.EPG) { Shell(navController, Routes.EPG) { EpgScreen(navController) } }
        composable(Routes.MULTIVIEW) { Shell(navController, Routes.MULTIVIEW) { MultiViewScreen(navController) } }
        composable(Routes.RECORDINGS) { Shell(navController, Routes.RECORDINGS) { RecordingsScreen(navController) } }
        composable(Routes.DOWNLOADS) { Shell(navController, Routes.DOWNLOADS) { DownloadsScreen(navController) } }
        composable(Routes.MATCH_CENTER) { Shell(navController, Routes.MATCH_CENTER) { MatchCenterScreen(navController) } }
        composable(Routes.MATCH_DAY) { Shell(navController, Routes.MATCH_DAY) { MatchDayScreen(navController) } }
        composable(Routes.WATCH_TONIGHT) { Shell(navController, Routes.WATCH_TONIGHT) { WatchTonightScreen(navController) } }
        composable(Routes.REMOTE) { Shell(navController, Routes.REMOTE) { RemoteScreen(navController) } }
        composable(Routes.FAVORIS) { Shell(navController, Routes.FAVORIS) { FavorisScreen(navController) } }
        composable(Routes.WATCH_LATER) { Shell(navController, Routes.WATCH_LATER) { WatchLaterScreen(navController) } }
        composable(Routes.HISTORIQUE) { Shell(navController, Routes.HISTORIQUE) { HistoriqueScreen(navController) } }
        composable(Routes.PLAYLISTS) { Shell(navController, Routes.PLAYLISTS) { PlaylistsScreen(navController) } }
        composable(Routes.MANAGE) { Shell(navController, Routes.MANAGE) { ManageScreen(navController) } }
        composable(Routes.DIAGNOSTICS) { Shell(navController, Routes.DIAGNOSTICS) { DiagnosticsScreen(navController) } }
        composable(Routes.EPG_HEALTH) { Shell(navController, Routes.EPG_HEALTH) { EpgHealthScreen(navController) } }
        composable(Routes.USB_FILES) { Shell(navController, Routes.USB_FILES) { UsbFilesScreen(navController) } }
        composable(Routes.CHANNEL_TIMER) { Shell(navController, Routes.CHANNEL_TIMER) { ChannelTimerScreen(navController) } }
        composable(Routes.CLEANUP) { Shell(navController, Routes.CLEANUP) { CleanupScreen(navController) } }
        composable(Routes.DRIVE) {
            val driveBackupViewModel: DriveBackupViewModel = hiltViewModel()
            Shell(navController, Routes.DRIVE) {
                DriveScreen(
                    backupExport = driveBackupViewModel::exportBackup,
                    backupImport = driveBackupViewModel::importBackup
                )
            }
        }
        composable(Routes.SETTINGS) { Shell(navController, Routes.SETTINGS) { SettingsScreen(navController) } }
        // PC01 — parental control is NOT a sidebar item (the prototype does
        // not put it there); it is reached from the locks row in Settings.
        composable(Routes.PARENTAL) { Shell(navController, Routes.PARENTAL) { ParentalScreen(navController) } }
        // Onboarding wizard (§4.39): first-run only, full-screen — NO
        // sidebar item and NO Shell (it finishes into Loading/Home itself).
        composable(Routes.ONBOARDING_WIZARD) { OnboardingWizardScreen(navController) }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val kind = backStackEntry.arguments?.getString("kind") ?: "live"
            val id = backStackEntry.arguments?.getString("id") ?: ""
            PlayerScreen(navController = navController, kind = kind, id = id)
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val kind = backStackEntry.arguments?.getString("kind") ?: "film"
            val id = backStackEntry.arguments?.getString("id") ?: ""
            DetailScreen(navController = navController, kind = kind, id = id)
        }
        }
        GoldToastHost()
    }
}
