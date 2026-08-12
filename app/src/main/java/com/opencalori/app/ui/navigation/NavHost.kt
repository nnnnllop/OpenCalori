package com.opencalori.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencalori.app.ui.dashboard.DashboardScreen
import com.opencalori.app.ui.foodsearch.FoodSearchScreen
import com.opencalori.app.ui.onboarding.OnboardingScreen
import com.opencalori.app.ui.profile.ProfileScreen
import com.opencalori.app.ui.scanner.ScannerScreen
import com.opencalori.app.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"

    const val ARG_DATE = "date"

    /**
     * The scanner and the search screen write into whatever day the diary is showing,
     * so the selected date travels with the route instead of defaulting to today.
     */
    const val SCANNER = "scanner/{date}"
    const val FOOD_SEARCH = "food_search/{date}"

    fun scanner(epochDay: Long) = "scanner/" + epochDay
    fun foodSearch(epochDay: Long) = "food_search/" + epochDay
}

@Composable
fun OpenCaloriNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel()
) {
    val start by viewModel.startDestination.collectAsState()

    NavHost(
        navController = navController,
        startDestination = start ?: Routes.ONBOARDING,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) +
                fadeIn(tween(220))
        },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(140)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) +
                fadeOut(tween(220))
        }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToScanner = { date -> navController.navigate(Routes.scanner(date)) },
                onNavigateToSearch = { date -> navController.navigate(Routes.foodSearch(date)) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.SCANNER,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType })
        ) {
            ScannerScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.FOOD_SEARCH,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType })
        ) {
            FoodSearchScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEditProfile = { navController.navigate(Routes.PROFILE) }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
