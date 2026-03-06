import SwiftUI
import SwiftAudioStreamEx

/// Root TabView with independent NavigationStack per tab.
struct MainNavigation: View {
    @Environment(\.appContainer) private var container
    @State private var showFullPlayer = false
    @State private var homeStack = NavigationPath()
    @State private var searchStack = NavigationPath()
    @State private var favoritesStack = NavigationPath()
    @State private var collectionsStack = NavigationPath()
    @State private var pendingShowNavigation: String?
    @State private var pendingDeepLink: DeepLink?
    @State private var selectedTab: AppTab = .home
    @State private var playerSourceTab: AppTab = .home
    @State private var searchResetToken = 0

    private var isOffline: Bool { !container.networkMonitor.isConnected }

    var body: some View {
        TabView(selection: tabSelection) {
            Tab("Home", systemImage: "house", value: .home) {
                NavigationStack(path: $homeStack) {
                    HomeScreen()
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                        .navigationDestination(for: CollectionRoute.self) { route in
                            switch route {
                            case .detail(let id):
                                CollectionDetailScreen(collectionId: id)
                            }
                        }
                }
                .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer)
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
            }
            Tab("Search", systemImage: "magnifyingglass", value: .search) {
                NavigationStack(path: $searchStack) {
                    SearchScreen(resetToken: searchResetToken)
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                }
                .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer)
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
            }
            Tab("Favorites", systemImage: "heart.fill", value: .favorites) {
                NavigationStack(path: $favoritesStack) {
                    FavoritesScreen()
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                        .navigationDestination(for: FavoritesRoute.self) { route in
                            switch route {
                            case .downloads:
                                DownloadsScreen()
                            }
                        }
                }
                .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer)
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
            }
            Tab("Collections", systemImage: "square.stack", value: .collections) {
                NavigationStack(path: $collectionsStack) {
                    CollectionsScreen()
                        .navigationDestination(for: CollectionRoute.self) { route in
                            switch route {
                            case .detail(let id):
                                CollectionDetailScreen(collectionId: id)
                            }
                        }
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                }
                .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer)
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
            }
            Tab("Settings", systemImage: "gearshape", value: .settings) {
                NavigationStack {
                    SettingsScreen()
                }
                .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer)
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
            }
        }
        .onChange(of: showFullPlayer) { _, isPresented in
            if isPresented { playerSourceTab = selectedTab }
        }
        .onChange(of: container.networkMonitor.isConnected) { _, isConnected in
            if !isConnected {
                // When going offline, navigate to Downloads (unless in Settings or Player)
                if selectedTab != .settings && !showFullPlayer {
                    navigateToDownloads()
                }
            }
        }
        .onChange(of: selectedTab) { oldTab, newTab in
            // When offline and user switches to a restricted tab, redirect to Downloads
            if isOffline && newTab != .favorites && newTab != .settings {
                // Use async to avoid modifying state during view update
                DispatchQueue.main.async {
                    navigateToDownloads()
                }
            }
        }
        .onOpenURL { url in
            handleDeepLink(url)
        }
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
            guard let url = activity.webpageURL else { return }
            handleDeepLink(url)
        }
        .sheet(item: $pendingDeepLink) { link in
            DeepLinkActionSheet(
                deepLink: link,
                onPlayNow: {
                    pendingDeepLink = nil
                    guard case .show(let showId, let recordingId, let trackNumber) = link else { return }
                    Task {
                        await container.playlistService.loadShow(showId)
                        if let rid = recordingId,
                           container.playlistService.currentRecording?.identifier != rid,
                           let rec = try? container.showRepository.getRecordingById(rid) {
                            await container.playlistService.selectRecording(rec)
                        }
                        let idx = trackNumber.map { max(0, $0 - 1) } ?? 0
                        container.playlistService.playTrack(at: idx)
                        container.playlistService.recordRecentPlay()
                        showFullPlayer = true
                    }
                },
                onGoToShow: {
                    pendingDeepLink = nil
                    guard case .show(let showId, _, _) = link else { return }
                    showFullPlayer = false
                    selectedTab = .home
                    homeStack = NavigationPath()
                    homeStack.append(showId)
                },
                onAddToFavorites: {
                    pendingDeepLink = nil
                    guard case .show(let showId, _, _) = link else { return }
                    try? container.favoritesService.addToFavorites(showId: showId)
                },
                onIgnore: {
                    pendingDeepLink = nil
                }
            )
        }
        .fullScreenCover(isPresented: $showFullPlayer, onDismiss: {
            if let showId = pendingShowNavigation {
                pendingShowNavigation = nil
                DispatchQueue.main.async {
                    navigateToShow(showId: showId, on: playerSourceTab)
                }
            }
            // When dismissing player while offline, redirect to Downloads
            if isOffline && selectedTab != .settings {
                navigateToDownloads()
            }
        }) {
            PlayerScreen(
                streamPlayer: container.streamPlayer,
                isPresented: $showFullPlayer,
                onViewShow: { showId in
                    pendingShowNavigation = showId
                    Task {
                        await container.playlistService.loadShow(showId)
                        if let rid = container.streamPlayer.currentTrack?.metadata["recordingId"],
                           rid != container.playlistService.currentRecording?.identifier,
                           let rec = try? container.showRepository.getRecordingById(rid) {
                            await container.playlistService.selectRecording(rec)
                        }
                    }
                    showFullPlayer = false
                }
            )
        }
    }

    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { selectedTab },
            set: { newTab in
                if newTab == .search && selectedTab == .search {
                    // Re-tapped search — toggle back to browse
                    searchResetToken += 1
                }
                selectedTab = newTab
            }
        )
    }

    private func navigateToDownloads() {
        favoritesStack = NavigationPath()
        favoritesStack.append(FavoritesRoute.downloads)
        selectedTab = .favorites
    }

    private func navigateToShow(showId: String, on tab: AppTab) {
        switch tab {
        case .home:
            homeStack = NavigationPath()
            homeStack.append(showId)
            selectedTab = .home
        case .search:
            searchStack = NavigationPath()
            searchStack.append(showId)
            selectedTab = .search
        case .favorites:
            favoritesStack = NavigationPath()
            favoritesStack.append(showId)
            selectedTab = .favorites
        case .collections:
            collectionsStack = NavigationPath()
            collectionsStack.append(showId)
            selectedTab = .collections
        case .settings:
            homeStack = NavigationPath()
            homeStack.append(showId)
            selectedTab = .home
        }
    }

    private func handleDeepLink(_ url: URL) {
        guard let link = DeepLink.parse(url) else { return }
        switch link {
        case .show:
            pendingDeepLink = link
        case .collection:
            selectedTab = .collections
        }
    }
}

// MARK: - Tab enum

enum AppTab: String, Hashable {
    case home, search, favorites, collections, settings

    var title: String { rawValue.capitalized }
}

// MARK: - Favorites Routes

enum FavoritesRoute: Hashable {
    case downloads
}

// MARK: - PlaceholderScreen

struct PlaceholderScreen: View {
    let tab: AppTab

    var body: some View {
        ContentUnavailableView(
            tab.title,
            systemImage: "hammer",
            description: Text("Coming soon")
        )
        .navigationTitle(tab.title)
    }
}

#Preview {
    MainNavigation()
}
