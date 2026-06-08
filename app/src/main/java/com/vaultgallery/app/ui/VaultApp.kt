package com.vaultgallery.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultgallery.app.data.prefs.ThemeMode
import com.vaultgallery.app.ui.album.AlbumDetailScreen
import com.vaultgallery.app.ui.auth.LockScreen
import com.vaultgallery.app.ui.home.HomeScreen
import com.vaultgallery.app.ui.imports.ImportScreen
import com.vaultgallery.app.ui.navigation.Routes
import com.vaultgallery.app.ui.onboarding.OnboardingScreen
import com.vaultgallery.app.ui.player.VideoPlayerScreen
import com.vaultgallery.app.ui.settings.SettingsScreen
import com.vaultgallery.app.ui.theme.VaultTheme
import com.vaultgallery.app.ui.viewer.ImageViewerScreen
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * Root composable. Applies the user's theme, then routes between onboarding, the
 * lock screen and the unlocked vault graph based on session + settings state.
 */
@Composable
fun VaultApp(shellViewModel: AppShellViewModel = hiltViewModel()) {
    val settings by shellViewModel.settings.collectAsState()
    val unlocked by shellViewModel.unlocked.collectAsState()
    val current = settings ?: return

    val darkTheme = when (current.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    VaultTheme(darkTheme = darkTheme, dynamicColor = current.dynamicColor) {
        val navController = rememberNavController()
        val start = when {
            !current.onboarded -> Routes.ONBOARDING
            !unlocked -> Routes.LOCK
            else -> Routes.HOME
        }

        // Security: when the session locks (auto-lock on background), force the UI back
        // to the lock screen and clear the back stack so vault content can't be seen.
        LaunchedEffect(unlocked) {
            if (current.onboarded && !unlocked) {
                navController.navigate(Routes.LOCK) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }

        NavHost(navController = navController, startDestination = start) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.LOCK) {
                LockScreen(
                    onUnlocked = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOCK) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenItem = { item ->
                        if (item.isVideo) navController.navigate(Routes.player(item.id))
                        else navController.navigate(Routes.imageViewer(item.id))
                    },
                    onOpenAlbum = { id -> navController.navigate(Routes.albumDetail(id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onImport = { navController.navigate(Routes.IMPORT) }
                )
            }
            composable(
                Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { item ->
                        if (item.isVideo) navController.navigate(Routes.player(item.id))
                        else navController.navigate(Routes.imageViewer(item.id))
                    }
                )
            }
            composable(
                Routes.IMAGE_VIEWER,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) {
                ImageViewerScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) {
                VideoPlayerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.IMPORT) {
                ImportScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
