package com.deadly.v2.feature.player.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.deadly.v2.feature.player.screens.main.PlayerScreen

/**
 * V2 Player Navigation
 * 
 * Defines the player destination and navigation functions.
 * Route: "player" - Shows whatever is currently playing from MediaController
 */

const val PLAYER_ROUTE = "player"

/**
 * Add player destination to NavGraphBuilder
 */
fun NavGraphBuilder.playerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlaylist: (String, String?) -> Unit
) {
    composable(route = PLAYER_ROUTE) {
        PlayerScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToPlaylist = onNavigateToPlaylist
        )
    }
}

/**
 * Navigate to player from other features
 */
fun NavController.navigateToPlayer() {
    navigate(PLAYER_ROUTE)
}