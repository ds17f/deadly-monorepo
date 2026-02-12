package com.grateful.deadly.core.design.scaffold

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grateful.deadly.core.design.component.topbar.TopBar
import com.grateful.deadly.core.design.component.topbar.TopBarMode

/**
 * AppScaffold - Unified scaffold for all screens
 * 
 * This component encapsulates all screen layout concerns and provides a consistent
 * API for screens. It automatically handles status bar insets based on the
 * TopBar mode and provides proper padding for content.
 * 
 * Features:
 * - Automatic WindowInsets handling based on TopBar mode
 * - Unified API for all screen layouts  
 * - Support for optional TopBar, bottom navigation
 * - Spotify-style status bar handling (SOLID/IMMERSIVE modes)
 * 
 * @param modifier Modifier for the scaffold
 * @param topBarMode How to handle status bar interaction (null = no top bar)
 * @param topBarTitle Title to display in the top bar
 * @param topBarNavigationIcon Optional navigation icon (typically back arrow)
 * @param topBarActions Optional action buttons
 * @param onNavigationClick Callback for navigation icon clicks
 * @param showBottomNav Whether to show bottom navigation (future feature)
 * @param content The main content of the screen
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    // Top bar configuration
    topBarMode: TopBarMode? = null, // null = no top bar
    topBarTitle: String = "",
    topBarNavigationIcon: @Composable (() -> Unit)? = null,
    topBarActions: @Composable (RowScope.() -> Unit) = {},
    onNavigationClick: (() -> Unit)? = null,
    // Bottom navigation
    showBottomNav: Boolean = false,
    // Content
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = if (topBarMode == TopBarMode.SOLID) {
            {
                TopBar(
                    title = topBarTitle,
                    mode = topBarMode,
                    navigationIcon = topBarNavigationIcon,
                    actions = topBarActions,
                    onNavigationClick = onNavigationClick
                )
            }
        } else {
            // IMMERSIVE mode or null - no TopBar rendered
            // In IMMERSIVE mode, content flows behind status bar with underlay
            {}
        },
        bottomBar = if (showBottomNav) {
            { 
                // TODO: Bottom Navigation when we implement it
                // For now, empty placeholder
            }
        } else {
            {}
        },
        // Automatically handle window insets based on top bar mode
        contentWindowInsets = when (topBarMode) {
            TopBarMode.SOLID -> WindowInsets.systemBars
            TopBarMode.IMMERSIVE -> WindowInsets(0, 0, 0, 0)
            null -> WindowInsets.systemBars // No top bar, normal system insets
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

/**
 * Enhanced AppScaffold with BarConfiguration support
 * 
 * This version accepts BarConfiguration objects and handles both top and bottom
 * navigation based on the current route configuration. This is the new unified
 * layout controller for the app with bottom navigation support.
 * 
 * Added MiniPlayer support - positioned above bottom navigation like Spotify.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBarConfig: TopBarConfig? = null,
    bottomBarConfig: BottomBarConfig? = null,
    bottomNavigationContent: (@Composable () -> Unit)? = null,
    miniPlayerConfig: MiniPlayerConfig? = null,
    miniPlayerContent: (@Composable () -> Unit)? = null,
    onNavigationClick: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    // Use Box layout to properly layer MiniPlayer above bottom navigation
    val shouldShowMiniPlayer = miniPlayerConfig?.visible == true && miniPlayerContent != null

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                topBarConfig?.let { config ->
                    TopBar(
                        title = config.title,
                        mode = config.mode,
                        navigationIcon = config.navigationIcon,
                        actions = config.actions?.let { actions ->
                            { actions() }
                        } ?: {},
                        onNavigationClick = onNavigationClick
                    )
                }
            },
            bottomBar = {
                // Empty bottomBar - we'll render navigation and MiniPlayer separately below
            },
            contentWindowInsets = when (topBarConfig?.mode) {
                TopBarMode.SOLID -> WindowInsets.systemBars
                TopBarMode.IMMERSIVE -> WindowInsets(0, 0, 0, 0)
                null -> WindowInsets.systemBars
            }
        ) { paddingValues ->
            // Main content with extra bottom padding if MiniPlayer or bottom nav are present
            val extraBottomPadding = when {
                shouldShowMiniPlayer && bottomBarConfig?.visible == true -> 144.dp // MiniPlayer (88dp) + BottomNav (56dp)
                shouldShowMiniPlayer -> 88.dp // Just MiniPlayer
                bottomBarConfig?.visible == true -> 56.dp // Just BottomNav
                else -> 0.dp
            }

            content(
                PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = paddingValues.calculateBottomPadding() + extraBottomPadding
                )
            )
        }

        // Layer MiniPlayer and bottom navigation at the bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // MiniPlayer above bottom navigation (Spotify-style) - only if config allows
            if (shouldShowMiniPlayer) {
                miniPlayerContent?.invoke()
            }

            // Bottom navigation at the very bottom
            if (bottomBarConfig?.visible == true && bottomNavigationContent != null) {
                bottomNavigationContent()
            }
        }
    }
}