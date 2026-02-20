package com.grateful.deadly.feature.playlist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.grateful.deadly.feature.playlist.screens.main.PlaylistScreen

/**
 * Playlist navigation route constants
 */
const val PLAYLIST_SHOW_ROUTE = "playlist/{showId}"
const val PLAYLIST_RECORDING_ROUTE = "playlist/{showId}/{recordingId}?trackNumber={trackNumber}"

/**
 * Extension function for NavController to navigate to Playlist
 *
 * @param showId The show ID to display
 * @param recordingId Optional specific recording ID. If null, show logic decides which recording to display
 * @param trackNumber Optional track number to auto-play after the playlist loads
 */
fun NavController.navigateToPlaylist(
    showId: String,
    recordingId: String? = null,
    trackNumber: Int? = null,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    val route = when {
        recordingId != null && trackNumber != null -> "playlist/$showId/$recordingId?trackNumber=$trackNumber"
        recordingId != null -> "playlist/$showId/$recordingId"
        else -> "playlist/$showId"
    }
    navigate(route, builder)
}

/**
 * Add Playlist destinations to NavGraphBuilder
 * Feature owns all routing decisions for true encapsulation
 *
 * Supports two routing patterns:
 * - playlist/{showId} - Let show logic decide which recording to display
 * - playlist/{showId}/{recordingId} - Display specific recording, with optional trackNumber query param
 */
fun NavGraphBuilder.playlistGraph(navController: NavController) {
    // Specific recording route - playlist/{showId}/{recordingId}?trackNumber={trackNumber}
    composable(
        route = PLAYLIST_RECORDING_ROUTE,
        arguments = listOf(
            navArgument("trackNumber") {
                type = NavType.IntType
                defaultValue = -1
            }
        )
    ) { backStackEntry ->
        val showId = backStackEntry.arguments?.getString("showId") ?: ""
        val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
        val trackNumber = backStackEntry.arguments?.getInt("trackNumber").takeIf { it != -1 }

        PlaylistScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToPlayer = {
                navController.navigate("player")
            },
            onNavigateToShow = { showId, recordingId ->
                // Navigate to player with specific recording
                navController.navigate("player/$recordingId")
            },
            onNavigateToCollection = { collectionId, showId ->
                navController.navigate("collectionDetail/$collectionId/$showId")
            },
            showId = showId,
            recordingId = recordingId,
            trackNumber = trackNumber
        )
    }

    // Show-only route - playlist/{showId} (show decides recording)
    composable(PLAYLIST_SHOW_ROUTE) { backStackEntry ->
        val showId = backStackEntry.arguments?.getString("showId") ?: ""

        PlaylistScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToPlayer = {
                navController.navigate("player")
            },
            onNavigateToShow = { showId, recordingId ->
                // Navigate to player with specific recording
                navController.navigate("player/$recordingId")
            },
            onNavigateToCollection = { collectionId, showId ->
                navController.navigate("collectionDetail/$collectionId/$showId")
            },
            showId = showId,
            recordingId = null // Let show logic decide
        )
    }
}
