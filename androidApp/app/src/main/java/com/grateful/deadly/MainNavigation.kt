package com.grateful.deadly

import android.net.Uri
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
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.AppScaffold
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.feature.home.navigation.homeGraph
import com.grateful.deadly.feature.settings.navigation.settingsGraph
import com.grateful.deadly.feature.splash.navigation.splashGraph
import com.grateful.deadly.feature.search.navigation.searchGraph
import com.grateful.deadly.feature.playlist.navigation.playlistGraph
import com.grateful.deadly.feature.playlist.navigation.navigateToPlaylist
import com.grateful.deadly.feature.player.navigation.playerScreen
import com.grateful.deadly.feature.miniplayer.screens.main.MiniPlayerScreen
import com.grateful.deadly.feature.library.navigation.libraryNavigation
import com.grateful.deadly.feature.collections.navigation.collectionsGraph
import com.grateful.deadly.feature.downloads.navigation.downloadsNavigation

sealed interface PendingDeepLink {
    data class ShowLink(
        val showId: String,
        val recordingId: String?,
        val trackNumber: Int?,
        val show: Show? = null
    ) : PendingDeepLink

    data class CollectionLink(
        val collectionId: String
    ) : PendingDeepLink
}
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
fun MainNavigation(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val appViewModel: AppViewModel = hiltViewModel()
    val isOffline by appViewModel.isOffline.collectAsState()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val snackbarHostState = remember { SnackbarHostState() }
    val prevIsOffline = remember { mutableStateOf<Boolean?>(null) }
    var pendingDeepLink by remember { mutableStateOf<PendingDeepLink?>(null) }

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
            val isSafe = route == "downloads" || route == "player" || route == "splash" || route == "settings"
            if (!isSafe) {
                navController.navigate("downloads") { launchSingleTop = true }
            }
        }
    }

    // Handle incoming deep links once DB is initialized (splash is the only pre-init route)
    LaunchedEffect(currentRoute, deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute == "splash") return@LaunchedEffect
        val segments = uri.pathSegments
        when (segments.getOrNull(0)) {
            "show" -> {
                val showId = segments.getOrNull(1) ?: return@LaunchedEffect
                val recordingId = segments.getOrNull(3)
                val trackNumber = if (segments.getOrNull(4) == "track") {
                    segments.getOrNull(5)?.toIntOrNull()
                } else null
                pendingDeepLink = PendingDeepLink.ShowLink(showId, recordingId, trackNumber)
            }
            "collection" -> {
                val collectionId = segments.getOrNull(1) ?: return@LaunchedEffect
                pendingDeepLink = PendingDeepLink.CollectionLink(collectionId)
            }
        }
        onDeepLinkHandled()
    }

    // Load show metadata asynchronously so the deep link handler above doesn't block playback
    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink
        if (link is PendingDeepLink.ShowLink && link.show == null) {
            val show = appViewModel.getShow(link.showId)
            // Only update if still the same pending link
            if (pendingDeepLink == link) {
                pendingDeepLink = link.copy(show = show)
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
                        val target = if (isOffline && route != "downloads" && route != "settings") "downloads" else route
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
                        navController.navigateToPlaylist(showId, recordingId) {
                            popUpTo("player") { inclusive = true }
                        }
                    }
                )

                // Settings feature - app configuration and about page
                settingsGraph(navController)
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

    pendingDeepLink?.let { deepLink ->
        DeepLinkActionSheet(
            deepLink = deepLink,
            onPlayNow = { showId, recordingId, trackNumber ->
                navController.navigateToPlaylist(showId, recordingId, trackNumber) {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
                pendingDeepLink = null
            },
            onAddToLibrary = { showId ->
                appViewModel.addToLibrary(showId)
                pendingDeepLink = null
            },
            onViewCollection = { collectionId ->
                navController.navigate("collections/$collectionId") {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
                pendingDeepLink = null
            },
            onDismiss = { pendingDeepLink = null }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeepLinkActionSheet(
    deepLink: PendingDeepLink,
    onPlayNow: (showId: String, recordingId: String?, trackNumber: Int?) -> Unit,
    onAddToLibrary: (showId: String) -> Unit,
    onViewCollection: (collectionId: String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (deepLink) {
                is PendingDeepLink.ShowLink -> {
                    val show = deepLink.show

                    // Header with artwork
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShowArtwork(
                            recordingId = deepLink.recordingId ?: show?.bestRecordingId,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            imageUrl = show?.coverImageUrl
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = show?.date ?: deepLink.showId,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (show != null) {
                                Text(
                                    text = show.venue.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = show.location.displayText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Actions
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ListItem(
                            headlineContent = { Text("Play Now") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.PlayerControls.Play(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                onPlayNow(deepLink.showId, deepLink.recordingId, deepLink.trackNumber)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Add Show to Library") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Content.LibraryAdd(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                onAddToLibrary(deepLink.showId)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Ignore") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Navigation.Close(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onDismiss() }
                        )
                    }
                }

                is PendingDeepLink.CollectionLink -> {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = IconResources.Navigation.Collections(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Shared Collection",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Actions
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ListItem(
                            headlineContent = { Text("View Collection") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Navigation.Collections(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                onViewCollection(deepLink.collectionId)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Ignore") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Navigation.Close(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable { onDismiss() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
