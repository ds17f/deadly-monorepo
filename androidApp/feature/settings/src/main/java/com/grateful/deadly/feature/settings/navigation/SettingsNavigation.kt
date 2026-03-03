package com.grateful.deadly.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.settings.screens.legal.LegalScreen

/**
 * Settings navigation route constants
 */
const val SETTINGS_ROUTE = "settings"
const val LEGAL_ROUTE = "legal"

/**
 * Extension function for NavController to navigate to Settings
 */
fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE)
}

/**
 * Add Settings destination to NavGraphBuilder
 */
fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLegal: () -> Unit
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(onNavigateToLegal = onNavigateToLegal)
    }
}

/**
 * Add Legal destination to NavGraphBuilder
 */
fun NavGraphBuilder.legalScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = LEGAL_ROUTE) {
        LegalScreen()
    }
}

/**
 * Settings navigation graph
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    settingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToLegal = { navController.navigate(LEGAL_ROUTE) }
    )

    legalScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
