package com.deadly.v2.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.deadly.v2.feature.home.screens.main.HomeScreen

/**
 * Navigation graph for home feature
 */
fun NavGraphBuilder.homeGraph(navController: NavController) {
    composable("home") {
        HomeScreen(
            onNavigateToPlayer = { recordingId ->
                navController.navigate("player")
            },
            onNavigateToShow = { showId ->
                navController.navigate("playlist/$showId")  
            },
            onNavigateToSearch = {
                navController.navigate("search")
            },
            onNavigateToCollection = { collectionId ->
                navController.navigate("collection/$collectionId")
            }
        )
    }
}