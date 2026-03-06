package com.grateful.deadly.feature.favorites.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.grateful.deadly.feature.favorites.screens.main.FavoritesScreen

/**
 * Favorites Navigation - Navigation integration for Favorites feature
 *
 * Provides type-safe navigation routing following standard navigation patterns.
 * Integrates with the overall app navigation system.
 */

// Route constants (string values kept for deep link backward compat)
object FavoritesRoutes {
    const val FAVORITES_MAIN = "library"
}

/**
 * Add Favorites navigation graph to the overall navigation
 * Feature owns all routing decisions for true encapsulation
 */
fun NavGraphBuilder.favoritesNavigation(navController: NavController) {
    composable(route = FavoritesRoutes.FAVORITES_MAIN) {
        FavoritesScreen(
            onNavigateToShow = { showId ->
                navController.navigate("playlist/$showId")
            },
            onNavigateToPlayer = { recordingId ->
                navController.navigate("player/$recordingId")
            },
            onNavigateToPlaylist = { showId, recordingId, trackNumber, autoPlay ->
                val route = when {
                    recordingId != null && trackNumber != null ->
                        "playlist/$showId/$recordingId?trackNumber=$trackNumber&autoPlay=$autoPlay"
                    recordingId != null ->
                        "playlist/$showId/$recordingId?autoPlay=$autoPlay"
                    trackNumber != null ->
                        "playlist/$showId?autoPlay=$autoPlay"
                    else ->
                        "playlist/$showId?autoPlay=$autoPlay"
                }
                navController.navigate(route)
            },
            onNavigateToFavorites = { },
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}

/**
 * Navigation extension functions for Favorites
 */
fun NavController.navigateToFavorites() {
    navigate(FavoritesRoutes.FAVORITES_MAIN) {
        // Single top to avoid multiple instances
        launchSingleTop = true
        // Restore state if returning to favorites
        restoreState = true
    }
}

/**
 * Favorites Navigation Destination - For use in bottom navigation
 */
data class FavoritesDestination(
    val route: String = FavoritesRoutes.FAVORITES_MAIN,
    val title: String = "Favorites",
    val iconResource: Int = android.R.drawable.ic_dialog_dialer, // Placeholder
    val selectedIconResource: Int = android.R.drawable.ic_dialog_dialer // Placeholder
)
