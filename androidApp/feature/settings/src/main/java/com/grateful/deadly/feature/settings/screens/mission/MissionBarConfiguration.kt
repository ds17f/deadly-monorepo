package com.grateful.deadly.feature.settings.screens.mission

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.BarConfiguration
import com.grateful.deadly.core.design.scaffold.BottomBarConfig
import com.grateful.deadly.core.design.scaffold.MiniPlayerConfig
import com.grateful.deadly.core.design.scaffold.TopBarConfig
import com.grateful.deadly.core.design.component.topbar.TopBarMode

object MissionBarConfiguration {

    fun getMissionBarConfig(): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Our Mission",
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
