package com.grateful.deadly.feature.library.screens.main

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode

/**
 * LibraryBarConfiguration - Bar configuration for Library feature
 * 
 * Defines how the navigation bars should appear for Library screens.
 * Colocated with Library feature to keep related UI settings together.
 */
object LibraryBarConfiguration {
    
    /**
     * Configuration for main Library screen
     * 
     * Library title with search and add actions for library management
     */
    fun getLibraryBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Your Library",
            mode = TopBarMode.SOLID,
            navigationIcon = null, // No back button in bottom nav context
            actions = { LibraryTopBarActions() }
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true) // Show MiniPlayer in library for music context
    )
}

/**
 * Top bar actions for main Library screen
 */
@Composable
private fun LibraryTopBarActions() {
    // Search action
    IconButton(onClick = { /* TODO: Implement library search */ }) {
        Icon(
            painter = IconResources.Content.Search(),
            contentDescription = "Search Library"
        )
    }
    
    // Add action  
    IconButton(onClick = { /* TODO: Implement add to library */ }) {
        Icon(
            painter = IconResources.Navigation.Add(),
            contentDescription = "Add to Library"
        )
    }
}