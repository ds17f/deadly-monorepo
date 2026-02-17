package com.grateful.deadly.feature.downloads.screens.main

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode

object DownloadsBarConfiguration {

    fun getDownloadsBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Downloads",
            mode = TopBarMode.SOLID,
            navigationIcon = { BackIcon() },
            actions = null
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true)
    )
}

@Composable
private fun BackIcon() {
    Icon(
        painter = IconResources.Navigation.Back(),
        contentDescription = "Back"
    )
}
