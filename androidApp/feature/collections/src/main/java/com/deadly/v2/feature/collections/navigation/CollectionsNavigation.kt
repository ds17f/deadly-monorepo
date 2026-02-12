package com.deadly.v2.feature.collections.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.deadly.v2.feature.collections.screens.main.CollectionsScreen

/**
 * Navigation graph for collections feature
 */
fun NavGraphBuilder.collectionsGraph(navController: NavController) {
    navigation(
        startDestination = "collections",
        route = "collections-graph"
    ) {
        composable("collections") {
            CollectionsScreen(
                onNavigateToCollection = { /* Carousel changes don't navigate - just change state */ },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                }
            )
        }
        
        composable("collections/{collectionId}") { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
            CollectionsScreen(
                collectionId = collectionId,
                onNavigateToCollection = { /* Carousel changes don't navigate - just change state */ },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                }
            )
        }
        
        // Legacy routes for backward compatibility - redirect to main collections screen
        composable("collection/{collectionId}") { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
            
            // Instead of redirecting with navigation, just show the collections screen directly
            // This prevents creating extra navigation entries
            CollectionsScreen(
                collectionId = collectionId,
                onNavigateToCollection = { /* Carousel changes don't navigate - just change state */ },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                }
            )
        }
        
        composable("collectionDetail/{collectionId}/{showId}") { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
            
            // Instead of redirecting with navigation, just show the collections screen directly
            // This prevents creating extra navigation entries  
            CollectionsScreen(
                collectionId = collectionId,
                onNavigateToCollection = { /* Carousel changes don't navigate - just change state */ },
                onNavigateToShow = { showId ->
                    navController.navigate("playlist/$showId")
                }
            )
        }
    }
}