package com.opencalori.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.opencalori.app.ui.dashboard.DashboardScreen
import com.opencalori.app.ui.foodsearch.FoodSearchScreen
import com.opencalori.app.ui.onboarding.OnboardingScreen
import com.opencalori.app.ui.scanner.ScannerScreen
import com.opencalori.app.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SCANNER = "scanner"
    const val FOOD_SEARCH = "food_search"
    const val SETTINGS = "settings"
}

@Composable
fun OpenCaloriNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel()
) {
    val start by viewModel.startDestination.collectAsState()

    NavHost(
        navController = navController,
        startDestination = start ?: Routes.ONBOARDING
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
                onNavigateToScanner = { navController.navigate(Routes.SCANNER) },
                onNavigateToSearch = { navController.navigate(Routes.FOOD_SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SCANNER) {
            ScannerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FOOD_SEARCH) {
            FoodSearchScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
