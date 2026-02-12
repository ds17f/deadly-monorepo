package com.grateful.deadly.feature.settings.screens.main

import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode

/**
 * SettingsBarConfiguration - Bar configuration for Settings feature
 * 
 * Defines how the navigation bars should appear for Settings screens.
 * Colocated with Settings feature to keep related UI settings together.
 */
object SettingsBarConfiguration {
    
    /**
     * Configuration for main Settings screen
     * 
     * Simple settings title with no special actions needed
     */
    fun getSettingsBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Settings",
            mode = TopBarMode.SOLID,
            navigationIcon = null // No back button in bottom nav context
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = false) // Hide MiniPlayer for clean settings experience
    )
}