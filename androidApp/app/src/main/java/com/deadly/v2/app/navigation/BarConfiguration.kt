package com.deadly.v2.app.navigation

// Re-export core design configuration types for convenience
import com.deadly.v2.core.design.scaffold.BarConfiguration
import com.deadly.v2.core.design.scaffold.BottomBarConfig
import com.deadly.v2.core.design.scaffold.MiniPlayerConfig
import com.deadly.v2.feature.home.screens.main.HomeBarConfiguration
import com.deadly.v2.feature.search.screens.main.SearchBarConfiguration
import com.deadly.v2.feature.settings.screens.main.SettingsBarConfiguration
import com.deadly.v2.feature.library.screens.main.LibraryBarConfiguration
import com.deadly.v2.feature.collections.screens.main.CollectionsBarConfiguration
import com.deadly.v2.feature.collections.screens.details.CollectionDetailsBarConfiguration

/**
 * Central route mapping to feature bar configurations
 * 
 * This delegates to feature-specific configuration objects,
 * keeping the actual configurations colocated with their features.
 */
object NavigationBarConfig {
    fun getBarConfig(route: String?): BarConfiguration = when {
        // Home routes
        route == "home" -> HomeBarConfiguration.getHomeBarConfig()
        
        // Search routes - delegate to SearchBarConfiguration
        route == "search" -> SearchBarConfiguration.getSearchBarConfig()
        route == "search-results" -> SearchBarConfiguration.getSearchResultsBarConfig()
        
        // Settings routes
        route == "settings" -> SettingsBarConfiguration.getSettingsBarConfig()
        
        // Library routes
        route == "library" -> LibraryBarConfiguration.getLibraryBarConfig()
        
        // Collections routes
        route == "collections" -> CollectionsBarConfiguration.getCollectionsBarConfig()
        
        // Collection details routes - dynamic route handling
        route?.startsWith("collection/") == true -> {
            val collectionId = route.removePrefix("collection/")
            CollectionDetailsBarConfiguration.getCollectionDetailsBarConfig(
                collectionName = "Collection", // Will be updated by ViewModel
                onNavigateBack = { /* Handled by screen */ }
            )
        }
        
        // Player routes - full screen immersive experience
        route == "player" -> BarConfiguration(
            topBar = null, // Player has its own top bar
            bottomBar = BottomBarConfig(visible = false), // Hide bottom nav in player
            miniPlayer = MiniPlayerConfig(visible = false) // Hide MiniPlayer in player (it has its own)
        )
        
        // Splash and other routes
        route == "splash" -> BarConfiguration(
            topBar = null,
            bottomBar = BottomBarConfig(visible = false), // Hide bottom nav on splash
            miniPlayer = MiniPlayerConfig(visible = false) // Hide MiniPlayer during splash
        )
        
        // Default configuration
        else -> BarConfiguration(
            topBar = null,
            bottomBar = BottomBarConfig(visible = true),
            miniPlayer = MiniPlayerConfig(visible = true) // Show MiniPlayer by default
        )
    }
}