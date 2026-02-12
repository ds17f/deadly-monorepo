package com.grateful.deadly.feature.collections.screens.main

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode
import com.grateful.deadly.core.design.resources.IconResources

/**
 * CollectionsBarConfiguration - Bar configuration for Collections feature
 * 
 * Defines how the navigation bars should appear for Collections screens.
 * Colocated with Collections feature to keep related UI settings together.
 */
object CollectionsBarConfiguration {
    
    /**
     * Configuration for main Collections screen
     * 
     * Standard collections browsing interface
     */
    fun getCollectionsBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Collections",
            mode = TopBarMode.SOLID,
            actions = { CollectionsTopBarActions() }
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true) // Show MiniPlayer for music context during browsing
    )
}

/**
 * Top bar actions for main Collections screen
 */
@Composable
private fun CollectionsTopBarActions() {
    IconButton(
        onClick = { 
            // TODO: Handle search in collections - show search dialog or navigate to search
        }
    ) {
        Icon(
            painter = IconResources.Content.Search(),
            contentDescription = "Search Collections"
        )
    }
}