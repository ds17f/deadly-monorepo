package com.deadly.v2.feature.collections.screens.main

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.deadly.v2.core.design.scaffold.BarConfiguration
import com.deadly.v2.core.design.scaffold.BottomBarConfig
import com.deadly.v2.core.design.scaffold.MiniPlayerConfig
import com.deadly.v2.core.design.scaffold.TopBarConfig
import com.deadly.v2.core.design.component.topbar.TopBarMode
import com.deadly.v2.core.design.resources.IconResources

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