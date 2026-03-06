package com.grateful.deadly.feature.favorites.screens.main

import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode

object FavoritesBarConfiguration {

    fun getFavoritesBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Favorites",
            mode = TopBarMode.SOLID,
            navigationIcon = null
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true)
    )
}
