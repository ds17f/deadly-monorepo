package com.grateful.deadly.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.grateful.deadly.core.design.resources.IconResources

/**
 * Bottom navigation destinations
 *
 * Defines the 4 main tabs in the bottom navigation:
 * - Home: Main hub for browsing and discovery
 * - Search: Search and browse functionality
 * - Favorites: User's saved shows
 * - Collections: Curated collections and series
 *
 * Settings is accessed via the drawer (gear icon in top bar).
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

    data object Favorites : BottomNavDestination(
        route = "library",
        title = "Favorites",
        selectedIcon = { IconResources.Content.Favorite() },
        unselectedIcon = { IconResources.Content.FavoriteBorder() }
    )

    data object Collections : BottomNavDestination(
        route = "collections",
        title = "Collections",
        selectedIcon = { IconResources.Navigation.Collections() },
        unselectedIcon = { IconResources.Navigation.Collections() }
    )

    companion object {
        val destinations = listOf(Home, Search, Favorites, Collections)
    }
}
