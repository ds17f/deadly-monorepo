package com.deadly.v2.core.design.component.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex

/**
 * StatusBarUnderlay - Translucent overlay for immersive mode
 * 
 * Creates a translucent background that sits between the system status bar
 * and scrolling content, ensuring the status bar remains readable while
 * content flows naturally behind it.
 * 
 * Key Features:
 * - Matches system status bar height exactly
 * - Semi-transparent background for readability
 * - High z-index to layer above content
 * - Supports both light and dark themes
 * - Edge-to-edge positioning
 * 
 * Usage:
 * ```
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // Content that scrolls behind
 *     LazyColumn { ... }
 *     
 *     // Underlay positioned on top
 *     StatusBarUnderlay(
 *         backgroundColor = MaterialTheme.colorScheme.surface,
 *         alpha = 0.85f
 *     )
 * }
 * ```
 */
@Composable
fun StatusBarUnderlay(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.85f
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { statusBarHeight.toDp() })
            .background(backgroundColor.copy(alpha = alpha))
            .zIndex(999f), // High z-index to ensure it's above content
        contentAlignment = Alignment.Center
    ) {
        // Empty - this is just a translucent background overlay
        // Any content (like icons) would be added by the parent
    }
}

/**
 * StatusBarUnderlayWithContent - Underlay with optional content
 * 
 * Extended version that allows content to be placed within the underlay,
 * such as action icons or minimal navigation elements.
 */
@Composable
fun StatusBarUnderlayWithContent(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    alpha: Float = 0.85f,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { statusBarHeight.toDp() })
            .background(backgroundColor.copy(alpha = alpha))
            .zIndex(999f),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}