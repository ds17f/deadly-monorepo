package com.grateful.deadly.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.settings.screens.legal.LegalScreen
import com.grateful.deadly.feature.settings.screens.mission.MissionScreen

/**
 * Settings navigation route constants
 */
const val SETTINGS_ROUTE = "settings"
const val LEGAL_ROUTE = "legal"
const val MISSION_ROUTE = "mission"

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
    onNavigateToLegal: () -> Unit,
    onNavigateToMission: () -> Unit
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(
            onNavigateToLegal = onNavigateToLegal,
            onNavigateToMission = onNavigateToMission
        )
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
 * Add Mission destination to NavGraphBuilder
 */
fun NavGraphBuilder.missionScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = MISSION_ROUTE) {
        MissionScreen()
    }
}

/**
 * Settings navigation graph
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    settingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToLegal = { navController.navigate(LEGAL_ROUTE) },
        onNavigateToMission = { navController.navigate(MISSION_ROUTE) }
    )

    legalScreen(
        onNavigateBack = { navController.popBackStack() }
    )

    missionScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
