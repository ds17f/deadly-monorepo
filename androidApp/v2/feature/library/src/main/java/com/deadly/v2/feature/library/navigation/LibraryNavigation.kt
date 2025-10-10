package com.deadly.v2.feature.library.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.deadly.v2.feature.library.screens.main.LibraryScreen

/**
 * V2 Library Navigation - Navigation integration for V2 Library feature
 * 
 * Provides type-safe navigation routing following V2 navigation patterns.
 * Integrates with the overall V2 app navigation system.
 */

// V2 Library route constants
object LibraryRoutes {
    const val LIBRARY_MAIN = "library"
}

/**
 * Add V2 Library navigation graph to the overall navigation
 * Feature owns all routing decisions for true encapsulation
 */
fun NavGraphBuilder.libraryNavigation(navController: NavController) {
    composable(route = LibraryRoutes.LIBRARY_MAIN) {
        LibraryScreen(
            onNavigateToShow = { showId ->
                navController.navigate("playlist/$showId")
            },
            onNavigateToPlayer = { recordingId ->
                navController.navigate("player/$recordingId")
            },
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}

/**
 * Navigation extension functions for V2 Library
 */
fun NavController.navigateToV2Library() {
    navigate(LibraryRoutes.LIBRARY_MAIN) {
        // Single top to avoid multiple instances
        launchSingleTop = true
        // Restore state if returning to library
        restoreState = true
    }
}

/**
 * V2 Library Navigation Destination - For use in bottom navigation
 */
data class LibraryDestination(
    val route: String = LibraryRoutes.LIBRARY_MAIN,
    val title: String = "Library",
    val iconResource: Int = android.R.drawable.ic_dialog_dialer, // Placeholder
    val selectedIconResource: Int = android.R.drawable.ic_dialog_dialer // Placeholder
)