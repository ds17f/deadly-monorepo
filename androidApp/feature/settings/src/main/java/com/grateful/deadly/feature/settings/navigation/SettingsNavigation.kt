package com.grateful.deadly.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.settings.screens.connect.ConnectScreen
import com.grateful.deadly.feature.settings.screens.developer.DeveloperScreen
import com.grateful.deadly.feature.settings.screens.equalizer.EqualizerScreen
import com.grateful.deadly.feature.settings.screens.legal.LegalScreen
import com.grateful.deadly.feature.settings.screens.mission.MissionScreen
import com.grateful.deadly.feature.settings.screens.privacy.PrivacyDataScreen

/**
 * Settings navigation route constants
 */
const val SETTINGS_ROUTE = "settings"
const val EQUALIZER_ROUTE = "equalizer"
const val LEGAL_ROUTE = "legal"
const val MISSION_ROUTE = "mission"
const val DEVELOPER_ROUTE = "developer"
const val PRIVACY_DATA_ROUTE = "privacy_data"
const val CONNECT_ROUTE = "connect"

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
    onNavigateToEqualizer: () -> Unit,
    onNavigateToLegal: () -> Unit,
    onNavigateToMission: () -> Unit,
    onNavigateToDeveloper: () -> Unit,
    onNavigateToPrivacyData: () -> Unit,
    onNavigateToConnect: () -> Unit,
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToEqualizer = onNavigateToEqualizer,
            onNavigateToLegal = onNavigateToLegal,
            onNavigateToMission = onNavigateToMission,
            onNavigateToDeveloper = onNavigateToDeveloper,
            onNavigateToPrivacyData = onNavigateToPrivacyData,
            onNavigateToConnect = onNavigateToConnect,
        )
    }
}

fun NavGraphBuilder.equalizerScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = EQUALIZER_ROUTE) {
        EqualizerScreen()
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

fun NavGraphBuilder.privacyDataScreen(
    onNavigateBack: () -> Unit
) {
    composable(route = PRIVACY_DATA_ROUTE) {
        PrivacyDataScreen()
    }
}

/**
 * Settings navigation graph
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    settingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDownloads = { navController.navigate("downloads") },
        onNavigateToEqualizer = { navController.navigate(EQUALIZER_ROUTE) },
        onNavigateToLegal = { navController.navigate(LEGAL_ROUTE) },
        onNavigateToMission = { navController.navigate(MISSION_ROUTE) },
        onNavigateToDeveloper = { navController.navigate(DEVELOPER_ROUTE) },
        onNavigateToPrivacyData = { navController.navigate(PRIVACY_DATA_ROUTE) },
        onNavigateToConnect = { navController.navigate(CONNECT_ROUTE) }
    )

    equalizerScreen(
        onNavigateBack = { navController.popBackStack() }
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

    privacyDataScreen(
        onNavigateBack = { navController.popBackStack() }
    )

    composable(route = CONNECT_ROUTE) {
        ConnectScreen()
    }
}
