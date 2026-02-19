package com.grateful.deadly.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.settings.screens.about.AboutScreen

/**
 * Settings navigation route constants
 */
const val SETTINGS_ROUTE = "settings"
const val ABOUT_ROUTE = "about"

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
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(onNavigateToAbout = onNavigateToAbout)
    }
}

/**
 * Add About destination to NavGraphBuilder
 */
fun NavGraphBuilder.aboutScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = ABOUT_ROUTE) {
        AboutScreen()
    }
}

/**
 * Settings navigation graph
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    settingsScreen(
        onNavigateBack = {
            navController.popBackStack()
        },
        onNavigateToAbout = {
            navController.navigate(ABOUT_ROUTE)
        }
    )

    aboutScreen(
        onNavigateBack = {
            navController.popBackStack()
        }
    )
}