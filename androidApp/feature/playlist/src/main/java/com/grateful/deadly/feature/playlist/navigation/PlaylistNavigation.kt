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
const val PLAYLIST_SHOW_ROUTE = "playlist/{showId}?autoPlay={autoPlay}&openSheet={openSheet}"
const val PLAYLIST_RECORDING_ROUTE = "playlist/{showId}/{recordingId}?trackNumber={trackNumber}&autoPlay={autoPlay}&openSheet={openSheet}"

/**
 * Extension function for NavController to navigate to Playlist
 *
 * @param showId The show ID to display
 * @param recordingId Optional specific recording ID. If null, show logic decides which recording to display
 * @param trackNumber Optional track number to auto-play after the playlist loads
 * @param autoPlay Whether to start playback automatically (e.g., from "Play Now" deep link action)
 * @param openSheet Optional sheet to auto-open on arrival ("setlist" | "collections" | "recording"),
 *                  used by the player's "⋯" menu to deep-link into a "This Show" view (ADR-0014)
 */
fun NavController.navigateToPlaylist(
    showId: String,
    recordingId: String? = null,
    trackNumber: Int? = null,
    autoPlay: Boolean = false,
    openSheet: String? = null,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    val sheet = openSheet ?: "none"
    val route = when {
        recordingId != null && trackNumber != null -> "playlist/$showId/$recordingId?trackNumber=$trackNumber&autoPlay=$autoPlay&openSheet=$sheet"
        recordingId != null -> "playlist/$showId/$recordingId?autoPlay=$autoPlay&openSheet=$sheet"
        else -> "playlist/$showId?autoPlay=$autoPlay&openSheet=$sheet"
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
fun NavGraphBuilder.playlistGraph(
    navController: NavController,
    onNavigateToShowQueue: () -> Unit
) {
    // Specific recording route - playlist/{showId}/{recordingId}?trackNumber={trackNumber}&autoPlay={autoPlay}&openSheet={openSheet}
    composable(
        route = PLAYLIST_RECORDING_ROUTE,
        arguments = listOf(
            navArgument("trackNumber") {
                type = NavType.IntType
                defaultValue = -1
            },
            navArgument("autoPlay") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("openSheet") {
                type = NavType.StringType
                defaultValue = "none"
            }
        )
    ) { backStackEntry ->
        val showId = backStackEntry.arguments?.getString("showId") ?: ""
        val recordingId = backStackEntry.arguments?.getString("recordingId") ?: ""
        val trackNumber = backStackEntry.arguments?.getInt("trackNumber").takeIf { it != -1 }
        val autoPlay = backStackEntry.arguments?.getBoolean("autoPlay") ?: false
        val openSheet = backStackEntry.arguments?.getString("openSheet").takeIf { it != "none" }

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
            onNavigateToUpNext = onNavigateToShowQueue,
            showId = showId,
            recordingId = recordingId,
            trackNumber = trackNumber,
            autoPlay = autoPlay,
            openSheet = openSheet
        )
    }

    // Show-only route - playlist/{showId}?autoPlay={autoPlay}&openSheet={openSheet} (show decides recording)
    composable(
        route = PLAYLIST_SHOW_ROUTE,
        arguments = listOf(
            navArgument("autoPlay") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("openSheet") {
                type = NavType.StringType
                defaultValue = "none"
            }
        )
    ) { backStackEntry ->
        val showId = backStackEntry.arguments?.getString("showId") ?: ""
        val autoPlay = backStackEntry.arguments?.getBoolean("autoPlay") ?: false
        val openSheet = backStackEntry.arguments?.getString("openSheet").takeIf { it != "none" }

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
            onNavigateToUpNext = onNavigateToShowQueue,
            showId = showId,
            recordingId = null, // Let show logic decide
            autoPlay = autoPlay,
            openSheet = openSheet
        )
    }
}
