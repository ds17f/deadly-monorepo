package com.grateful.deadly.feature.settings.screens.developer

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode

object DeveloperBarConfiguration {

    fun getDeveloperBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Developer",
            mode = TopBarMode.SOLID,
            navigationIcon = { BackIcon() }
        ),
        bottomBar = BottomBarConfig(visible = false),
        miniPlayer = MiniPlayerConfig(visible = false)
    )
}

@Composable
private fun BackIcon() {
    Icon(
        painter = IconResources.Navigation.Back(),
        contentDescription = "Back"
    )
}
