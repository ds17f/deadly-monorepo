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
    @State private var showingSettings = false
    @State private var showingEqualizer = false

    private var isOffline: Bool { !container.networkMonitor.isConnected }

    var body: some View {
        TabView(selection: tabSelection) {
            Tab("Home", systemImage: "house", value: .home) {
                NavigationStack(path: $homeStack) {
                    HomeScreen()
                        .settingsLogoButton($showingSettings, title: "Home")
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
                        .settingsLogoButton($showingSettings, title: "Search")
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
                        .settingsLogoButton($showingSettings, title: "Favorites")
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
                        .settingsLogoButton($showingSettings, title: "Collections")
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
        }
        .overlay {
            SettingsDrawer(isOpen: $showingSettings, onNavigateToDownloads: {
                showingSettings = false
                navigateToDownloads()
            }, onNavigateToEqualizer: {
                showingSettings = false
                showingEqualizer = true
            })
        }
        .sheet(isPresented: $showingEqualizer) {
            EqualizerSheet()
                .presentationDetents([.medium, .large])
        }
        .onChange(of: showFullPlayer) { _, isPresented in
            if isPresented { playerSourceTab = selectedTab }
        }
        .onChange(of: container.networkMonitor.isConnected) { _, isConnected in
            if !isConnected {
                // When going offline, navigate to Downloads (unless in Settings or Player)
                if !showFullPlayer {
                    navigateToDownloads()
                }
            }
        }
        .onChange(of: selectedTab) { oldTab, newTab in
            // When offline and user switches to a restricted tab, redirect to Downloads
            if isOffline && newTab != .favorites {
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
            if isOffline {
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
    case home, search, favorites, collections

    var title: String { rawValue.capitalized }
}

// MARK: - Favorites Routes

enum FavoritesRoute: Hashable {
    case downloads
}

// MARK: - Settings Routes

enum SettingsRoute: Hashable {
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

// MARK: - Settings Logo Button

private extension View {
    func settingsLogoButton(_ showingSettings: Binding<Bool>, title: String) -> some View {
        self.navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showingSettings.wrappedValue = true } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "music.note")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundStyle(DeadlyColors.primary)
                            Text(title)
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        }
                    }
                }
            }
    }
}

// MARK: - Settings Drawer

private struct SettingsDrawer: View {
    @Binding var isOpen: Bool
    var onNavigateToDownloads: () -> Void
    var onNavigateToEqualizer: () -> Void
    @GestureState private var dragOffset: CGFloat = 0

    var body: some View {
        ZStack(alignment: .leading) {
            if isOpen {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                    .onTapGesture { isOpen = false }
                    .transition(.opacity)

                NavigationStack {
                    SettingsScreen(onNavigateToDownloads: onNavigateToDownloads, onNavigateToEqualizer: onNavigateToEqualizer)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("Done") { isOpen = false }
                            }
                        }
                }
                .frame(width: UIScreen.main.bounds.width * 0.82)
                .offset(x: min(0, dragOffset))
                .gesture(
                    DragGesture()
                        .updating($dragOffset) { value, state, _ in
                            if value.translation.width < 0 {
                                state = value.translation.width
                            }
                        }
                        .onEnded { value in
                            if value.translation.width < -80 {
                                isOpen = false
                            }
                        }
                )
                .transition(.move(edge: .leading))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: isOpen)
    }
}

#Preview {
    MainNavigation()
}
