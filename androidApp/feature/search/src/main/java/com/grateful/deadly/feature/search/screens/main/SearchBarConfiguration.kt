package com.grateful.deadly.feature.search.screens.main

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode
import com.grateful.deadly.core.design.resources.IconResources

/**
 * SearchBarConfiguration - Bar configuration for Search feature
 * 
 * Defines how the navigation bars should appear for Search screens.
 * Colocated with Search feature to keep related UI settings together.
 */
object SearchBarConfiguration {
    
    /**
     * Configuration for main Search screen
     * 
     * Includes search title and camera/QR scanner action
     */
    fun getSearchBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Search",
            mode = TopBarMode.SOLID,
            actions = { SearchTopBarActions() }
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true) // Show MiniPlayer for music context during search
    )
    
    /**
     * Configuration for Search Results screen
     * 
     * Full-screen search results experience with no top bar
     */
    fun getSearchResultsBarConfig(): BarConfiguration = BarConfiguration(
        topBar = null, // No top bar for immersive search results
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true) // Show MiniPlayer for music context in results
    )
}

/**
 * Top bar actions for main Search screen
 */
@Composable
private fun SearchTopBarActions() {
    val context = LocalContext.current
    IconButton(
        onClick = {
            Toast.makeText(context, "QR scanning is coming soon", Toast.LENGTH_SHORT).show()
        }
    ) {
        Icon(
            painter = IconResources.Content.QrCodeScanner(),
            contentDescription = "Scan QR Code"
        )
    }
}

