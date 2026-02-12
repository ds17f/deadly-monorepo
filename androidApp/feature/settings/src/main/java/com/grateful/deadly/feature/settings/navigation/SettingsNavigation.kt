package com.grateful.deadly.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.settings.SettingsScreen

/**
 * Settings navigation route constant
 */
const val SETTINGS_ROUTE = "settings"

/**
 * Extension function for NavController to navigate to Settings
 */
fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE)
}

/**
 * Add Settings destination to NavGraphBuilder
 * 
 * Following navigation patterns where screens accept
 * navigation callbacks rather than NavController directly.
 */
fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen()
    }
}

/**
 * Settings navigation graph
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    settingsScreen(
        onNavigateBack = {
            navController.popBackStack()
        }
    )
}