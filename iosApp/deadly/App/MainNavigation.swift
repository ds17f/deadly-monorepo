import SwiftUI
import SwiftAudioStreamEx

/// Root TabView with independent NavigationStack per tab.
struct MainNavigation: View {
    @Environment(\.appContainer) private var container
    @State private var showFullPlayer = false
    @State private var homeStack = NavigationPath()
    @State private var lastPushedShowId: String?
    @State private var pendingShowNavigation: String?
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
                // When going offline, navigate to Downloads (unless in Settings)
                if selectedTab != .settings {
                    navigateToDownloads()
                }
            }
        }
        .fullScreenCover(isPresented: $showFullPlayer, onDismiss: {
            if let showId = pendingShowNavigation {
                homeStack.append(showId)
                lastPushedShowId = showId
                pendingShowNavigation = nil
            }
        }) {
            PlayerScreen(
                streamPlayer: container.streamPlayer,
                isPresented: $showFullPlayer,
                onViewShow: { showId in
                    if lastPushedShowId == showId {
                        // Show is already the top of the home stack — just dismiss.
                        showFullPlayer = false
                    } else {
                        // Show isn't behind us — dismiss and push it.
                        pendingShowNavigation = showId
                        showFullPlayer = false
                    }
                }
            )
        }
    }

    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { selectedTab },
            set: { newTab in
                // When offline, only allow Library and Settings
                if isOffline && newTab != .library && newTab != .settings {
                    navigateToDownloads()
                    return
                }

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
