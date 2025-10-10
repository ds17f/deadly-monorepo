package com.deadly.v2.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.deadly.v2.feature.search.screens.main.SearchScreen
import com.deadly.v2.feature.search.screens.searchResults.SearchResultsScreen

/**
 * Navigation graph for search feature
 */
fun NavGraphBuilder.searchGraph(navController: NavController) {
    navigation(
        startDestination = "search",
        route = "search-graph"
    ) {
        composable("search") {
            SearchScreen(
                onNavigateToPlayer = { recordingId ->
                    // TODO: Navigate to player when implemented
                    // navController.navigate("player/$recordingId")
                },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                },
                onNavigateToSearchResults = {
                    navController.navigate("search-results")
                },
                initialEra = null
            )
        }
        
        composable("search-results") {
            SearchResultsScreen(
                initialQuery = "",
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                },
                onNavigateToPlayer = { recordingId ->
                    // TODO: Navigate to player when implemented
                    // navController.navigate("player/$recordingId")
                }
            )
        }
    }
}