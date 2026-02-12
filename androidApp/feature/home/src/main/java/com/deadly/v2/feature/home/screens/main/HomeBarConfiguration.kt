package com.deadly.v2.feature.home.screens.main

import com.deadly.v2.core.design.scaffold.BarConfiguration
import com.deadly.v2.core.design.scaffold.BottomBarConfig
import com.deadly.v2.core.design.scaffold.MiniPlayerConfig
import com.deadly.v2.core.design.scaffold.TopBarConfig
import com.deadly.v2.core.design.scaffold.BottomBarStyle

/**
 * HomeBarConfiguration - Bar configuration for Home feature
 * 
 * Defines how the navigation bars should appear for Home screens.
 * Colocated with Home feature to keep related UI settings together.
 */
object HomeBarConfiguration {
    
    /**
     * Configuration for main Home screen
     * 
     * Simple design with "Home" title and bottom navigation enabled
     */
    fun getHomeBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Home"
        ),
        bottomBar = BottomBarConfig(
            visible = true,
            style = BottomBarStyle.DEFAULT
        ),
        miniPlayer = MiniPlayerConfig(visible = true) // Show MiniPlayer on home for music context
    )
}