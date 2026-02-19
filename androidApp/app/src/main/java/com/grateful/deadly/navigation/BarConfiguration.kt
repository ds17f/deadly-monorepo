package com.grateful.deadly.navigation

// Re-export core design configuration types for convenience
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.feature.home.screens.main.HomeBarConfiguration
import com.grateful.deadly.feature.search.screens.main.SearchBarConfiguration
import com.grateful.deadly.feature.settings.screens.main.SettingsBarConfiguration
import com.grateful.deadly.feature.settings.screens.about.AboutBarConfiguration
import com.grateful.deadly.feature.library.screens.main.LibraryBarConfiguration
import com.grateful.deadly.feature.collections.screens.main.CollectionsBarConfiguration
import com.grateful.deadly.feature.collections.screens.details.CollectionDetailsBarConfiguration
import com.grateful.deadly.feature.downloads.screens.main.DownloadsBarConfiguration

/**
 * Central route mapping to feature bar configurations
 * 
 * This delegates to feature-specific configuration objects,
 * keeping the actual configurations colocated with their features.
 */
object NavigationBarConfig {
    fun getBarConfig(
        route: String?,
        onNavigateToDownloads: () -> Unit = {}
    ): BarConfiguration = when {
        // Home routes
        route == "home" -> HomeBarConfiguration.getHomeBarConfig()
        
        // Search routes - delegate to SearchBarConfiguration
        route == "search" -> SearchBarConfiguration.getSearchBarConfig()
        route == "search-results" -> SearchBarConfiguration.getSearchResultsBarConfig()
        
        // Settings routes
        route == "settings" -> SettingsBarConfiguration.getSettingsBarConfig()
        route == "about" -> AboutBarConfiguration.getAboutBarConfig()

        // Library routes
        route == "library" -> LibraryBarConfiguration.getLibraryBarConfig(
            onNavigateToDownloads = onNavigateToDownloads
        )
        
        // Downloads routes
        route == "downloads" -> DownloadsBarConfiguration.getDownloadsBarConfig()

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