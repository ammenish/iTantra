package com.mirage.itantra.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.mirage.itantra.ui.communication.CommunicationScreen
import com.mirage.itantra.ui.history.HistoryScreen
import com.mirage.itantra.ui.home.HomeScreen
import com.mirage.itantra.ui.home.HomeViewModel
import com.mirage.itantra.ui.settings.SettingsScreen

/**
 * Navigation routes for iTantra.
 */
object Routes {
    const val HOME = "home"
    const val COMMUNICATION = "communication"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val DEBUG = "debug" // Phase 13
}

/**
 * Main navigation graph.
 *
 * Single-activity architecture with Jetpack Navigation Compose.
 * All screens slide in/out with smooth animations.
 */
@Composable
fun NavGraph(homeViewModel: HomeViewModel = hiltViewModel()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToConnection = { navController.navigate(Routes.COMMUNICATION) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                viewModel = homeViewModel
            )
        }

        composable(Routes.COMMUNICATION) {
            CommunicationScreen(
                onNavigateBack = { navController.popBackStack() },
                onAlertTriggered = { homeViewModel.onAlertConfirmed() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
