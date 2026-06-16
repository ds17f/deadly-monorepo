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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.grateful.deadly.core.design.component.AppToast
import com.grateful.deadly.core.design.component.ShowArtwork
import com.grateful.deadly.core.design.resources.IconResources
import com.grateful.deadly.core.design.scaffold.AppScaffold
import com.grateful.deadly.playback.AutoAdvanceOverlay
import com.grateful.deadly.playback.AutoAdvanceTakeover
import com.grateful.deadly.core.model.Show
import com.grateful.deadly.feature.home.navigation.homeGraph
import com.grateful.deadly.notifications.NotificationBell
import com.grateful.deadly.notifications.NotificationViewModel
import com.grateful.deadly.notifications.NotificationsScreen
import com.grateful.deadly.feature.settings.SettingsScreen
import com.grateful.deadly.feature.settings.navigation.CONNECT_ROUTE
import com.grateful.deadly.feature.settings.navigation.settingsGraph
import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.grateful.deadly.feature.splash.navigation.splashGraph
import com.grateful.deadly.feature.search.navigation.searchGraph
import com.grateful.deadly.feature.playlist.navigation.playlistGraph
import com.grateful.deadly.feature.playlist.navigation.navigateToPlaylist
import com.grateful.deadly.feature.player.navigation.playerScreen
import com.grateful.deadly.feature.player.navigation.PLAYER_ROUTE
import com.grateful.deadly.feature.player.screens.main.PlayerSidePanel
import com.grateful.deadly.feature.miniplayer.screens.main.MiniPlayerScreen
import com.grateful.deadly.feature.favorites.navigation.favoritesNavigation
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

/** Content for the shared AppToast overlay. [onAction] non-null = a tappable pill. */
private data class ToastUi(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prevIsOffline = remember { mutableStateOf<Boolean?>(null) }
    var pendingDeepLink by remember { mutableStateOf<PendingDeepLink?>(null) }

    // The new-message toast and the app-wide transient toast share one
    // top-of-z-stack overlay (AppToast), which — unlike the scaffold
    // SnackbarHost — renders above the bottom bar + mini player. `toastUi` is
    // the retained content (kept through the exit animation), `toastVisible` is
    // the show/hide trigger, and `toastNonce` re-arms the auto-dismiss timer on
    // each new toast (so identical back-to-back messages still re-trigger it).
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val lastToastKey = remember { mutableStateOf<Long?>(null) }
    val toastUi = remember { mutableStateOf<ToastUi?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    var toastNonce by remember { mutableStateOf(0) }

    fun showToast(ui: ToastUi) {
        toastUi.value = ui
        toastVisible = true
        toastNonce++
    }

    // New in-app messages: tap the pill to open the inbox. Deduped by arrival
    // key so rotation/recomposition can't re-toast (decision C).
    LaunchedEffect(Unit) {
        notificationViewModel.newArrivals.collect { arrival ->
            if (lastToastKey.value == arrival.key) return@collect
            lastToastKey.value = arrival.key
            notificationViewModel.onToastShown(arrival)
            val msg = if (arrival.count > 1) "${arrival.count} new messages" else "New: ${arrival.title}"
            showToast(ToastUi(text = msg, actionLabel = "View", onAction = {
                notificationViewModel.onToastTap(arrival)
                navController.navigate("notifications")
            }))
        }
    }

    // App-wide transient confirmations (e.g. the Autoplay toggle) — non-actionable.
    LaunchedEffect(Unit) {
        appViewModel.toasts.collect { msg ->
            showToast(ToastUi(text = msg))
        }
    }

    LaunchedEffect(toastNonce) {
        if (toastVisible) {
            delay(2500)
            toastVisible = false
        }
    }

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
            val isSafe = route == "downloads" || route == "player" || route == "splash" || route == "legal" || route == "mission" || route == "developer"
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
            "show", "shows" -> {
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
        isOffline = isOffline,
    )

    // Augment top bar with logo click to open drawer, and add the notifications
    // bell to the home screen's actions (the app's primary landing surface).
    val baseTopBar = barConfig.topBar
    val augmentedTopBar = baseTopBar?.copy(
        onLogoClick = {
            scope.launch { drawerState.open() }
        },
        actions = if (currentRoute == "home") {
            { NotificationBell(onClick = { navController.navigate("notifications") }) }
        } else {
            baseTopBar.actions
        }
    )

    // Landscape/tablet: when the window is wide, the bottom tab bar (which eats
    // ~1/5 of the short landscape height) is replaced by a vertical icon-only
    // nav rail on the left. Keyed off width, not device — so phone landscape,
    // tablets, foldables and DeX all get it. Narrow stays today's bottom bar.
    val isWide = LocalConfiguration.current.screenWidthDp >= WIDE_BREAKPOINT_DP
    val useSideNav = isWide && barConfig.bottomBar?.visible == true
    // On wide tabbed screens the bottom mini player is replaced by a docked
    // side player in the right column (contextual — only when a track plays).
    val showSidePlayer = useSideNav && barConfig.miniPlayer?.visible == true

    // Shared tab-navigation action used by both the bottom bar and the rail.
    val onNavigateToDestination: (String) -> Unit = { route ->
        val target = if (isOffline && route != "downloads") "downloads" else route
        navController.navigate(target) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Defined outside the wide-layout Row below so RowScope doesn't leak into
    // the overlay AnimatedVisibility calls (which need the top-level overload).
    val scaffoldContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val autoAdvanceCountdown by appViewModel.autoAdvanceCountdown.collectAsState()

            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.padding(paddingValues)
            ) {
                // Splash feature - handles database initialization
                splashGraph(navController)

                // Home feature - main hub screen
                homeGraph(navController)

                // Favorites feature - user's saved content
                favoritesNavigation(navController)

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
                    onNavigateToPlaylist = { showId, recordingId, openSheet ->
                        navController.navigateToPlaylist(showId, recordingId, openSheet = openSheet) {
                            popUpTo("player") { inclusive = true }
                        }
                    }
                )

                // Settings feature - app configuration and about page
                settingsGraph(navController)

                // Notifications inbox (in-app messaging)
                composable("notifications") {
                    NotificationsScreen(onNavigateBack = { navController.popBackStack() })
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

            // ADR-0010: end-of-show "Next up in Ns" countdown — shown on the
            // active device and remotes alike. On the open player screen it's a
            // full-screen "Up Next" takeover previewing the next show (parity
            // with web's HeaderPlayer); everywhere else it's a docked card above
            // the mini player.
            autoAdvanceCountdown?.let { countdown ->
                if (currentRoute == PLAYER_ROUTE) {
                    AutoAdvanceTakeover(
                        countdown = countdown,
                        onPlayNow = { appViewModel.playNextNow() },
                        onCancel = { appViewModel.cancelAutoAdvance() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    AutoAdvanceOverlay(
                        countdown = countdown,
                        onPlayNow = { appViewModel.playNextNow() },
                        onCancel = { appViewModel.cancelAutoAdvance() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp)
                            .padding(bottom = paddingValues.calculateBottomPadding() + 8.dp),
                    )
                }
            }

            // App-wide transient toast — last child = topmost z, visible above
            // every screen and the mini player. Content is retained in `toastUi`
            // so it stays rendered through the exit animation after hide.
            androidx.compose.animation.AnimatedVisibility(
                visible = toastVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = paddingValues.calculateBottomPadding() + 24.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                toastUi.value?.let { t ->
                    AppToast(message = t.text, actionLabel = t.actionLabel, onClick = t.onAction)
                }
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                SettingsScreen(
                    onNavigateToDownloads = {
                        scope.launch { drawerState.close() }
                        navController.navigate("downloads")
                    },
                    onNavigateToEqualizer = {
                        scope.launch { drawerState.close() }
                        navController.navigate("equalizer")
                    },
                    onNavigateToLegal = {
                        scope.launch { drawerState.close() }
                        navController.navigate("legal")
                    },
                    onNavigateToMission = {
                        scope.launch { drawerState.close() }
                        navController.navigate("mission")
                    },
                    onNavigateToDeveloper = {
                        scope.launch { drawerState.close() }
                        navController.navigate("developer")
                    },
                    onNavigateToPrivacyData = {
                        scope.launch { drawerState.close() }
                        navController.navigate("privacy_data")
                    },
                    onNavigateToConnect = {
                        scope.launch { drawerState.close() }
                        navController.navigate(CONNECT_ROUTE)
                    }
                )
            }
        }
    ) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (useSideNav) {
            NavigationRailBar(
                currentRoute = currentRoute,
                onNavigateToDestination = onNavigateToDestination,
                onOpenNotifications = { navController.navigate("notifications") },
                onOpenSettings = { scope.launch { drawerState.open() } }
            )
        }
    AppScaffold(
        modifier = Modifier.weight(1f),
        // Wide layout: drop the top bar entirely. Its title ("Home"/"Search"/…)
        // is redundant next to the selected rail icon, and removing it reclaims
        // the scarce vertical height in landscape. The bell + settings it carried
        // live on the rail instead.
        topBarConfig = if (useSideNav) null else augmentedTopBar,
        bottomBarConfig = barConfig.bottomBar,
        bottomNavigationContent = if (!useSideNav && barConfig.bottomBar?.visible == true) {
            {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigateToDestination = onNavigateToDestination
                )
            }
        } else null,
        miniPlayerConfig = barConfig.miniPlayer,
        miniPlayerContent = if (showSidePlayer) null else {
            {
                MiniPlayerScreen(
                    onTapToExpand = { _ ->
                        Log.d("MainNavigation", "MiniPlayer tapped - navigating to player")
                        navController.navigate("player")
                    }
                )
            }
        },
        onNavigationClick = {
            navController.popBackStack()
        },
        snackbarHostState = snackbarHostState
    ) { paddingValues ->
        scaffoldContent(paddingValues)
    }
        if (showSidePlayer) {
            PlayerSidePanel(
                onTapToExpand = { navController.navigate("player") },
                onNavigateToPlaylist = { showId, recordingId, openSheet ->
                    navController.navigateToPlaylist(showId, recordingId, openSheet = openSheet)
                }
            )
        }
    } // Row
    } // ModalNavigationDrawer

    // ── In-App Review ────────────────────────────────────────────────

    val showReviewDialog by appViewModel.showReviewDialog.collectAsState()
    val launchReview by appViewModel.launchInAppReview.collectAsState()

    if (showReviewDialog) {
        AlertDialog(
            onDismissRequest = { appViewModel.onReviewDismiss() },
            title = { Text("Enjoying Deadly?") },
            text = { Text("Would you mind taking a moment to rate it? Your feedback helps other Deadheads find the app.") },
            confirmButton = {
                TextButton(onClick = { appViewModel.onReviewYes() }) {
                    Text("Yes!")
                }
            },
            dismissButton = {
                TextButton(onClick = { appViewModel.onReviewDismiss() }) {
                    Text("Not really")
                }
            }
        )
    }

    val activity = LocalContext.current as? Activity
    if (launchReview && activity != null) {
        if (BuildConfig.DEBUG) {
            AlertDialog(
                onDismissRequest = { appViewModel.onInAppReviewLaunched() },
                title = { Text("[Debug] In-App Review") },
                text = { Text("In production, the Google Play review dialog would appear here.") },
                confirmButton = {
                    TextButton(onClick = { appViewModel.onInAppReviewLaunched() }) {
                        Text("OK")
                    }
                }
            )
        } else {
            LaunchedEffect(Unit) {
                try {
                    val manager = ReviewManagerFactory.create(activity)
                    val reviewInfo = manager.requestReviewFlow().await()
                    manager.launchReviewFlow(activity, reviewInfo).await()
                } catch (_: Exception) {
                }
                appViewModel.onInAppReviewLaunched()
            }
        }
    }

    pendingDeepLink?.let { deepLink ->
        DeepLinkActionSheet(
            deepLink = deepLink,
            onPlayNow = { showId, recordingId, trackNumber ->
                navController.navigateToPlaylist(showId, recordingId, trackNumber, autoPlay = true) {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
                pendingDeepLink = null
            },
            onGoToShow = { showId ->
                navController.navigateToPlaylist(showId) {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
                pendingDeepLink = null
            },
            onAddToFavorites = { showId ->
                appViewModel.addToFavorites(showId)
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

/** Width (dp) at/above which the bottom tab bar becomes a left icon rail. */
private const val WIDE_BREAKPOINT_DP = 600

/**
 * Vertical icon-only navigation rail — the wide-layout replacement for the
 * bottom bar (phone landscape, tablets). Mirrors [BottomNavigationBar]'s
 * destinations and selection logic; drops the labels to stay thin and to
 * reclaim the vertical height the bottom bar wastes in landscape.
 */
@Composable
private fun NavigationRailBar(
    currentRoute: String?,
    onNavigateToDestination: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .systemBarsPadding()
                // Keep the icons clear of the camera cutout — in landscape the
                // notch sits on the side edge the rail occupies.
                .displayCutoutPadding()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tab icons pinned to the top of the rail.
            BottomNavDestination.destinations.forEach { destination ->
                NavigationRailItem(
                    destination = destination,
                    isSelected = currentRoute == destination.route,
                    onClick = { onNavigateToDestination(destination.route) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Notifications bell pinned just above settings — the wide layout's
            // home for the bell that lives in the top bar on narrow screens.
            NotificationBell(onClick = onOpenNotifications)

            // Settings pinned to the bottom of the rail.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenSettings() }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = IconResources.Navigation.Settings(),
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Individual icon-only rail item. */
@Composable
private fun NavigationRailItem(
    destination: BottomNavDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = if (isSelected) destination.selectedIcon() else destination.unselectedIcon(),
            contentDescription = destination.title,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
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
    onGoToShow: (showId: String) -> Unit,
    onAddToFavorites: (showId: String) -> Unit,
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
                            headlineContent = { Text("Go to Show") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Content.PlaylistPlay(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                onGoToShow(deepLink.showId)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Add Show to Favorites") },
                            leadingContent = {
                                Icon(
                                    painter = IconResources.Content.Favorite(),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.clickable {
                                onAddToFavorites(deepLink.showId)
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
