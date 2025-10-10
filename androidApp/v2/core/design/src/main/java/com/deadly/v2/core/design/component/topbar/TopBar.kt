package com.deadly.v2.core.design.component.topbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.deadly.v2.core.design.R
import com.deadly.v2.core.design.resources.IconResources
import com.deadly.v2.core.theme.api.ThemeAssets

/**
 * Defines how the TopBar should handle status bar interaction
 */
sealed class TopBarMode {
    /**
     * Content is padded below the status bar.
     * Status bar area is treated as reserved system space.
     */
    object SOLID : TopBarMode()
    
    /**
     * Content draws behind the status bar with a gradient scrim.
     * Creates an immersive edge-to-edge experience.
     */
    object IMMERSIVE : TopBarMode()
}

/**
 * TopBar - Spotify-style TopBar with flexible status bar handling and theme support
 * 
 * This component provides consistent styling across all V2 screens with
 * flexible status bar interaction modes. It integrates with the V2 theme system
 * to display the current theme's logo (lightning bolt by default, or themed logos).
 * 
 * @param title The title text to display
 * @param mode How to handle status bar interaction (SOLID or IMMERSIVE)
 * @param navigationIcon Optional navigation icon (typically back arrow)
 * @param actions Optional action buttons
 * @param onNavigationClick Callback for navigation icon clicks
 * @param backgroundColor Background color for the top bar content
 * @param contentColor Color for text and icons
 * 
 * Used by:
 * - SearchScreen (SOLID mode)
 * - PlayerScreen (IMMERSIVE mode) 
 * - LibraryScreen (SOLID mode)
 * - Other V2 implementations
 */
@Composable
fun TopBar(
    title: String,
    mode: TopBarMode,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    onNavigationClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(density).toDp()
    }
    
    when (mode) {
        TopBarMode.SOLID -> {
            // SOLID mode: Traditional top bar with status bar padding
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
            ) {
                // Status bar spacer
                Spacer(modifier = Modifier.height(statusBarHeight))
                
                // Top bar content
                TopBarContent(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    onNavigationClick = onNavigationClick,
                    contentColor = contentColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        
        TopBarMode.IMMERSIVE -> {
            // IMMERSIVE mode: Edge-to-edge with gradient scrim
            Box(
                modifier = modifier.fillMaxWidth()
            ) {
                // Gradient scrim behind status bar for system icon readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight + 56.dp) // Status bar + top bar height
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f), // Darker at top for system icons
                                    Color.Black.copy(alpha = 0.4f),
                                    backgroundColor.copy(alpha = 0.9f), // Fade to top bar color
                                    backgroundColor
                                ),
                                startY = 0f,
                                endY = with(density) { (statusBarHeight + 32.dp).toPx() }
                            )
                        )
                        .zIndex(0f)
                )
                
                // Top bar content positioned below status bar
                Column(
                    modifier = Modifier.zIndex(1f)
                ) {
                    // Status bar spacer (transparent, content can show behind)
                    Spacer(modifier = Modifier.height(statusBarHeight))
                    
                    // Top bar content with enhanced contrast for immersive mode
                    TopBarContent(
                        title = title,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        onNavigationClick = onNavigationClick,
                        contentColor = Color.White, // High contrast for immersive mode
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Internal composable for the actual top bar content
 * Separated for reuse between SOLID and IMMERSIVE modes
 */
@Composable
private fun TopBarContent(
    title: String,
    navigationIcon: @Composable (() -> Unit)?,
    actions: @Composable (RowScope.() -> Unit),
    onNavigationClick: (() -> Unit)?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp), // Standard Material3 top bar height
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation icon
        navigationIcon?.let { icon ->
            IconButton(
                onClick = onNavigationClick ?: {},
                modifier = Modifier.size(48.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            }
        }
        
        // App Logo
        Image(
            painter = ThemeAssets.current.primaryLogo(),
            contentDescription = "Deadly",
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        
        // Action buttons
        Row(
            horizontalArrangement = Arrangement.End,
            content = {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    actions()
                }
            }
        )
        
        Spacer(modifier = Modifier.width(4.dp))
    }
}

/**
 * TopBarDefaults - Default action buttons and navigation icons for common TopBar use cases
 */
object TopBarDefaults {
    
    /**
     * Back navigation icon for screens with back navigation
     */
    @Composable
    fun BackNavigationIcon(onBackClick: () -> Unit): @Composable () -> Unit = {
        Icon(
            painter = IconResources.Navigation.Back(),
            contentDescription = "Back"
        )
    }
    
    /**
     * Search and Add actions for LibraryScreen
     */
    @Composable
    fun LibraryActions(
        onSearchClick: () -> Unit,
        onAddClick: () -> Unit
    ): @Composable RowScope.() -> Unit = {
        IconButton(onClick = onSearchClick) {
            Icon(
                painter = IconResources.Content.Search(),
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onAddClick) {
            Icon(
                painter = IconResources.Navigation.Add(),
                contentDescription = "Add to Library",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    
    /**
     * QR Scanner action for SearchScreen
     */
    @Composable
    fun SearchActions(
        onCameraClick: () -> Unit
    ): @Composable RowScope.() -> Unit = {
        IconButton(onClick = onCameraClick) {
            Icon(
                painter = IconResources.Content.QrCodeScanner(),
                contentDescription = "QR Code Scanner",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}