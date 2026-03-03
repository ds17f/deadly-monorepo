import SwiftUI
import SwiftAudioStreamEx

/// Root TabView with independent NavigationStack per tab.
struct MainNavigation: View {
    @Environment(\.appContainer) private var container
    @State private var showFullPlayer = false
    @State private var homeStack = NavigationPath()
    @State private var pendingShowNavigation: String?
    @State private var pendingDeepLink: DeepLink?
    @State private var selectedTab: AppTab = .home
    @State private var searchResetToken = 0
    @State private var libraryStack = NavigationPath()

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
                NavigationStack {
                    SearchScreen(resetToken: searchResetToken)
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                }
                .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer)
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
            }
            Tab("Library", systemImage: "books.vertical", value: .library) {
                NavigationStack(path: $libraryStack) {
                    LibraryScreen()
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                        .navigationDestination(for: LibraryRoute.self) { route in
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
                NavigationStack {
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
            if isOffline && newTab != .library && newTab != .settings {
                // Use async to avoid modifying state during view update
                DispatchQueue.main.async {
                    navigateToDownloads()
                }
            }
        }
        .onOpenURL { url in
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
                onAddToLibrary: {
                    pendingDeepLink = nil
                    guard case .show(let showId, _, _) = link else { return }
                    try? container.libraryService.addToLibrary(showId: showId)
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
                    selectedTab = .home
                    homeStack = NavigationPath()
                    homeStack.append(showId)
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
        libraryStack = NavigationPath()
        libraryStack.append(LibraryRoute.downloads)
        selectedTab = .library
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
    case home, search, library, collections, settings

    var title: String { rawValue.capitalized }
}

// MARK: - Library Routes

enum LibraryRoute: Hashable {
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
