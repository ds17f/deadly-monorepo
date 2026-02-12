package com.deadly.v2.core.design.scaffold

import androidx.compose.runtime.Composable
import com.deadly.v2.core.design.component.topbar.TopBarMode

/**
 * BarConfiguration - Navigation bar configuration data classes
 * 
 * These data classes define how navigation bars should appear and behave.
 * Located in core/design to be accessible by AppScaffold without circular dependencies.
 */
data class BarConfiguration(
    val topBar: TopBarConfig? = null,
    val bottomBar: BottomBarConfig? = null,
    val miniPlayer: MiniPlayerConfig? = null
)

/**
 * Configuration for top bar appearance and behavior
 */
data class TopBarConfig(
    val title: String,
    val mode: TopBarMode = TopBarMode.SOLID,
    val actions: (@Composable () -> Unit)? = null,
    val navigationIcon: (@Composable () -> Unit)? = null
)

/**
 * Configuration for bottom navigation bar
 */
data class BottomBarConfig(
    val visible: Boolean = true,
    val style: BottomBarStyle = BottomBarStyle.DEFAULT
)

/**
 * Bottom bar styling options
 */
enum class BottomBarStyle {
    DEFAULT,
    TRANSPARENT,
    ELEVATED
}

/**
 * Configuration for MiniPlayer appearance and behavior
 */
data class MiniPlayerConfig(
    val visible: Boolean = true,
    val style: MiniPlayerStyle = MiniPlayerStyle.DEFAULT
)

/**
 * MiniPlayer styling options
 */
enum class MiniPlayerStyle {
    DEFAULT,    // Normal MiniPlayer
    COMPACT,    // Smaller version for dense screens
    HIDDEN      // Hidden but position reserved
}