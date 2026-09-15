package com.itsdonebro.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itsdonebro.ui.dashboard.DashboardScreen
import com.itsdonebro.ui.onboarding.OnboardingScreen
import com.itsdonebro.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD  = "dashboard"
    const val SETTINGS   = "settings"
}

@Composable
fun AppNavigation(showOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (showOnboarding) Routes.ONBOARDING else Routes.DASHBOARD

    NavHost(
        navController    = navController,
        startDestination = startDestination
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
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
