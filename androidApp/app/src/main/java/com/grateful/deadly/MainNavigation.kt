package com.grateful.deadly

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grateful.deadly.navigation.BottomNavDestination
import com.grateful.deadly.navigation.NavigationBarConfig
import com.grateful.deadly.core.design.scaffold.AppScaffold
import com.grateful.deadly.feature.home.navigation.homeGraph
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.splash.navigation.splashGraph
import com.grateful.deadly.feature.search.navigation.searchGraph
import com.grateful.deadly.feature.playlist.navigation.playlistGraph
import com.grateful.deadly.feature.playlist.navigation.navigateToPlaylist
import com.grateful.deadly.feature.player.navigation.playerScreen
import com.grateful.deadly.feature.miniplayer.screens.main.MiniPlayerScreen
import com.grateful.deadly.feature.library.navigation.libraryNavigation
import com.grateful.deadly.feature.collections.navigation.collectionsGraph
import com.grateful.deadly.feature.downloads.navigation.downloadsNavigation
/**
 * MainNavigation - Scalable navigation architecture
 *
 * This is the main navigation coordinator that orchestrates routing between
 * all feature modules. Each feature owns its own navigation subgraph,
 * maintaining clean separation of concerns.
 *
 * Navigation Flow:
 * 1. splash → home (after database initialization)
 * 2. home → search-graph (user taps search)
 * 3. search → search-results (user taps search box)
 * 4. search-results → search (back navigation)
 * 5. Any screen → playlist/{showId} or playlist/{showId}/{recordingId}
 *
 * Architecture Benefits:
 * - Scalable: Easy to add new feature subgraphs
 * - Modular: Each feature manages its own navigation
 * - Testable: Features accept navigation callbacks
 * - Clean: App module stays minimal and focused
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MainNavigation() {
    val appViewModel: AppViewModel = hiltViewModel()
    val isOffline by appViewModel.isOffline.collectAsState()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val snackbarHostState = remember { SnackbarHostState() }
    val prevIsOffline = remember { mutableStateOf<Boolean?>(null) }

    // Redirect to downloads and show snackbar on connectivity changes
    LaunchedEffect(isOffline) {
        // Show snackbar only on changes, not initial composition
        if (prevIsOffline.value != null && prevIsOffline.value != isOffline) {
            val msg = if (isOffline) "Offline — showing downloaded content" else "Back online"
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
        prevIsOffline.value = isOffline

        // Redirect to downloads when offline, unless already on a safe screen
        if (isOffline) {
            val route = navController.currentBackStackEntry?.destination?.route
            val isSafe = route == "downloads" || route == "player" || route == "splash"
            if (!isSafe) {
                navController.navigate("downloads") { launchSingleTop = true }
            }
        }
    }

    // Get bar configuration based on current route
    val barConfig = NavigationBarConfig.getBarConfig(
        route = currentRoute,
        onNavigateToDownloads = { navController.navigate("downloads") }
    )

    AppScaffold(
        topBarConfig = barConfig.topBar,
        bottomBarConfig = barConfig.bottomBar,
        bottomNavigationContent = if (barConfig.bottomBar?.visible == true) {
            {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigateToDestination = { route ->
                        val target = if (isOffline && route != "downloads") "downloads" else route
                        navController.navigate(target) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        } else null,
        miniPlayerConfig = barConfig.miniPlayer,
        miniPlayerContent = {
            MiniPlayerScreen(
                onTapToExpand = { _ ->
                    Log.d("MainNavigation", "MiniPlayer tapped - navigating to player")
                    navController.navigate("player")
                }
            )
        },
        onNavigationClick = {
            navController.popBackStack()
        },
        snackbarHostState = snackbarHostState
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.padding(paddingValues)
            ) {
                // Splash feature - handles database initialization
                splashGraph(navController)

                // Home feature - main hub screen
                homeGraph(navController)

                // Library feature - user's saved content
                libraryNavigation(navController)

                // Downloads feature - downloaded content management
                downloadsNavigation(navController)

                // Collections feature - curated collections and series
                collectionsGraph(navController)

                // Search feature - search and browse functionality
                searchGraph(navController)

                // Playlist feature - show and recording details
                playlistGraph(navController)

                // Player feature - playback interface
                playerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPlaylist = { showId, recordingId ->
                        navController.navigateToPlaylist(showId, recordingId)
                    }
                )

                // Settings feature - app configuration
                composable("settings") {
                    SettingsScreen()
                }
            }

            AnimatedVisibility(
                visible = isOffline,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = paddingValues.calculateBottomPadding()),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                OfflineBanner()
            }
        }
    }
}

/**
 * Slim banner shown at the top of the screen when the device is offline.
 */
@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Offline mode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bottom navigation bar component
 */
@Composable
private fun BottomNavigationBar(
    currentRoute: String?,
    onNavigateToDestination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavDestination.destinations.forEach { destination ->
                BottomNavItem(
                    destination = destination,
                    isSelected = currentRoute == destination.route,
                    onClick = { onNavigateToDestination(destination.route) }
                )
            }
        }
    }
}

/**
 * Individual bottom navigation item
 */
@Composable
private fun BottomNavItem(
    destination: BottomNavDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = if (isSelected) destination.selectedIcon() else destination.unselectedIcon(),
            contentDescription = destination.title,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = destination.title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
