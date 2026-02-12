package com.deadly.v2.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.deadly.v2.core.design.resources.IconResources

/**
 * Bottom navigation destinations for V2 app
 * 
 * Defines the 5 main tabs in the bottom navigation:
 * - Home: Main hub for browsing and discovery
 * - Search: Search and browse functionality  
 * - Library: User's saved shows and favorites
 * - Collections: Curated collections and series
 * - Settings: App configuration and preferences
 */
sealed class BottomNavDestination(
    val route: String,
    val title: String,
    val selectedIcon: @Composable () -> Painter,
    val unselectedIcon: @Composable () -> Painter
) {
    data object Home : BottomNavDestination(
        route = "home",
        title = "Home",
        selectedIcon = { IconResources.Navigation.Home() },
        unselectedIcon = { IconResources.Navigation.HomeOutlined() }
    )
    
    data object Search : BottomNavDestination(
        route = "search",
        title = "Search", 
        selectedIcon = { IconResources.Navigation.Search() },
        unselectedIcon = { IconResources.Navigation.SearchOutlined() }
    )
    
    data object Library : BottomNavDestination(
        route = "library",
        title = "Library",
        selectedIcon = { IconResources.Navigation.Library() },
        unselectedIcon = { IconResources.Navigation.LibraryOutlined() }
    )
    
    data object Collections : BottomNavDestination(
        route = "collections",
        title = "Collections",
        selectedIcon = { IconResources.Navigation.Collections() },
        unselectedIcon = { IconResources.Navigation.Collections() }
    )
    
    data object Settings : BottomNavDestination(
        route = "settings", 
        title = "Settings",
        selectedIcon = { IconResources.Navigation.Settings() },
        unselectedIcon = { IconResources.Navigation.SettingsOutlined() }
    )
    
    companion object {
        val destinations = listOf(Home, Search, Library, Collections, Settings)
    }
}