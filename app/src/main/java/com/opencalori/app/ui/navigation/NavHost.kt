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
import com.opencalori.app.ui.theme.MotionTokens
import com.opencalori.app.ui.onboarding.OnboardingScreen
import com.opencalori.app.ui.profile.ProfileScreen
import com.opencalori.app.ui.scanner.ScannerScreen
import com.opencalori.app.ui.textfood.TextFoodScreen
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
    const val TEXT_FOOD = "text_food/{date}"

    fun scanner(epochDay: Long) = "scanner/" + epochDay
    fun foodSearch(epochDay: Long) = "food_search/" + epochDay
    fun textFood(epochDay: Long) = "text_food/" + epochDay
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
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(MotionTokens.Screen)) +
                fadeIn(tween(MotionTokens.Standard))
        },
        exitTransition = { fadeOut(tween(MotionTokens.Quick)) },
        popEnterTransition = { fadeIn(tween(MotionTokens.Quick)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(MotionTokens.Screen)) +
                fadeOut(tween(MotionTokens.Standard))
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
                onNavigateToTextFood = { date -> navController.navigate(Routes.textFood(date)) },
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


        composable(
            route = Routes.TEXT_FOOD,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType })
        ) {
            TextFoodScreen(onBack = { navController.popBackStack() })
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
