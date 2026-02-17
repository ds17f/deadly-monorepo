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

object LibraryBarConfiguration {

    fun getLibraryBarConfig(
        onNavigateToDownloads: () -> Unit = {}
    ): BarConfiguration = BarConfiguration(
        topBar = TopBarConfig(
            title = "Your Library",
            mode = TopBarMode.SOLID,
            navigationIcon = null,
            actions = { LibraryTopBarActions(onNavigateToDownloads = onNavigateToDownloads) }
        ),
        bottomBar = BottomBarConfig(visible = true),
        miniPlayer = MiniPlayerConfig(visible = true)
    )
}

@Composable
private fun LibraryTopBarActions(onNavigateToDownloads: () -> Unit) {
    IconButton(onClick = onNavigateToDownloads) {
        Icon(
            painter = IconResources.Content.DownloadForOffline(),
            contentDescription = "Downloads"
        )
    }
}
