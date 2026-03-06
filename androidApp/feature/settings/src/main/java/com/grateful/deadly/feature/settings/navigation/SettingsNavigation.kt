package com.grateful.deadly.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.settings.screens.developer.DeveloperScreen
import com.grateful.deadly.feature.settings.screens.legal.LegalScreen
import com.grateful.deadly.feature.settings.screens.mission.MissionScreen

/**
 * Settings navigation route constants
 */
const val SETTINGS_ROUTE = "settings"
const val LEGAL_ROUTE = "legal"
const val MISSION_ROUTE = "mission"
const val DEVELOPER_ROUTE = "developer"

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
    onNavigateToDownloads: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToMission: () -> Unit,
    onNavigateToDeveloper: () -> Unit
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToLegal = onNavigateToLegal,
            onNavigateToMission = onNavigateToMission,
            onNavigateToDeveloper = onNavigateToDeveloper
        )
    }
}

fun NavGraphBuilder.developerScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = DEVELOPER_ROUTE) {
        DeveloperScreen()
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
        onNavigateToDownloads = { navController.navigate("downloads") },
        onNavigateToLegal = { navController.navigate(LEGAL_ROUTE) },
        onNavigateToMission = { navController.navigate(MISSION_ROUTE) },
        onNavigateToDeveloper = { navController.navigate(DEVELOPER_ROUTE) }
    )

    legalScreen(
        onNavigateBack = { navController.popBackStack() }
    )

    missionScreen(
        onNavigateBack = { navController.popBackStack() }
    )

    developerScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
