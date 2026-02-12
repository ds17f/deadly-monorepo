package com.deadly.v2.feature.search.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                },
                onNavigateToSearchResults = { query ->
                    val encoded = Uri.encode(query)
                    navController.navigate("search-results?query=$encoded")
                },
                initialEra = null
            )
        }

        composable(
            route = "search-results?query={query}",
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchResultsScreen(
                initialQuery = query,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                },
            )
        }
    }
}