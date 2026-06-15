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
    /// Sheet the player's "⋯" menu asked the show-detail screen to open
    /// (ADR-0014). Consumed + cleared by ShowDetailScreen on appear.
    @State private var pendingShowSheet: ShowDetailSheet?
    @State private var pendingDeepLink: DeepLink?
    @State private var selectedTab: AppTab = .home
    @State private var playerSourceTab: AppTab = .home
    @State private var searchResetToken = 0
    @State private var showingSettings = false
    @State private var showingEqualizer = false
    @State private var notificationToast: NewArrival?
    @State private var lastToastKey: Int64?

    private var isOffline: Bool { !container.networkMonitor.isConnected }

    private var playbackBannerError: String? {
        if case .error(let err) = container.streamPlayer.playbackState {
            if case .networkError = err {
                return "Can't reach Archive.org"
            }
            return "Playback error"
        }
        return nil
    }

    /// Width (pt) at/above which the bottom TabView becomes a left icon rail.
    /// Gated on width (not `horizontalSizeClass`) so a regular iPhone in
    /// landscape — which reports `.compact` — still gets the wide layout.
    private static let wideBreakpoint: CGFloat = 600

    var body: some View {
        GeometryReader { geo in
            rootLayout(isWide: geo.size.width >= Self.wideBreakpoint)
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
        // Transient new-message toast (decision C): tap opens the inbox.
        .overlay(alignment: .bottom) {
            if let toast = notificationToast {
                NotificationToastView(arrival: toast) {
                    container.analyticsService.trackNotificationToastTap(toast)
                    notificationToast = nil
                    selectedTab = .home
                    homeStack.append(NotificationRoute.inbox)
                }
                .padding(.bottom, 96)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        // App-wide transient toast (e.g. the Autoplay toggle confirmation).
        .overlay(alignment: .bottom) {
            if let message = container.toastPresenter.message {
                ActionToastView(message: message)
                    .padding(.bottom, 96)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.3), value: container.toastPresenter.message)
        .onChange(of: container.notificationStore.lastArrival) { _, arrival in
            guard let arrival, arrival.key != lastToastKey else { return }
            lastToastKey = arrival.key
            container.analyticsService.trackNotificationToastShown(arrival)
            withAnimation { notificationToast = arrival }
            Task {
                try? await Task.sleep(for: .seconds(4))
                if notificationToast?.key == arrival.key {
                    withAnimation { notificationToast = nil }
                }
            }
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
        .onChange(of: container.showQueueTabRequested) { _, requested in
            // "View Show Queue" from a player/playlist menu — switch to the
            // Favorites tab; FavoritesScreen selects its Show Queue sub-tab.
            if requested { selectedTab = .favorites }
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
                        container.playlistService.playTrack(at: idx, source: "deeplink")
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
                onViewShow: { showId, sheet in
                    pendingShowNavigation = showId
                    pendingShowSheet = sheet
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

    // MARK: - Root layout (wide rail vs. compact TabView)

    @ViewBuilder
    private func rootLayout(isWide: Bool) -> some View {
        if isWide {
            // Wide (iPad, any phone in landscape): icon rail + selected section,
            // plus a contextual docked side player that replaces the bottom mini
            // bar whenever a track is loaded.
            HStack(spacing: 0) {
                NavSidebar(
                    selectedTab: tabSelection,
                    onSettings: { showingSettings = true },
                    onNotifications: {
                        selectedTab = .home
                        homeStack.append(NotificationRoute.inbox)
                    }
                )
                Divider()
                sectionContent(for: selectedTab)
                Divider()
                // The side player column is always present in the wide layout so
                // the three-pane balance holds even before anything is playing —
                // it shows a quiet placeholder when idle and the live player once
                // a track loads.
                if container.miniPlayerService.isVisible {
                    SidePlayerView(
                        service: container.miniPlayerService,
                        showFullPlayer: $showFullPlayer,
                        onViewShow: { showId, sheet in
                            pendingShowSheet = sheet
                            Task {
                                await container.playlistService.loadShow(showId)
                                if let rid = container.streamPlayer.currentTrack?.metadata["recordingId"],
                                   rid != container.playlistService.currentRecording?.identifier,
                                   let rec = try? container.showRepository.getRecordingById(rid) {
                                    await container.playlistService.selectRecording(rec)
                                }
                            }
                            navigateToShow(showId: showId, on: selectedTab)
                        }
                    )
                } else {
                    SidePlayerPlaceholder()
                }
            }
        } else {
            // Compact (phone portrait): byte-for-byte today's TabView UX.
            TabView(selection: tabSelection) {
                Tab("Home", systemImage: "house", value: .home) { homeSection() }
                Tab("Search", systemImage: "magnifyingglass", value: .search) { searchSection() }
                Tab("Favorites", systemImage: "heart.fill", value: .favorites) { favoritesSection() }
                Tab("Collections", systemImage: "square.stack", value: .collections) { collectionsSection() }
            }
        }
    }

    // MARK: - Section content (shared by the TabView and the wide rail layout)

    @ViewBuilder
    private func sectionContent(for tab: AppTab) -> some View {
        // Wide layout: the bottom mini player is replaced by the docked side
        // player, so suppress it inside each section.
        switch tab {
        case .home: homeSection(suppressMini: true, wide: true)
        case .search: searchSection(suppressMini: true, wide: true)
        case .favorites: favoritesSection(suppressMini: true, wide: true)
        case .collections: collectionsSection(suppressMini: true, wide: true)
        }
    }

    private func homeSection(suppressMini: Bool = false, wide: Bool = false) -> some View {
        NavigationStack(path: $homeStack) {
            HomeScreen()
                .settingsLogoButton($showingSettings, title: "Home", wide: wide)
                .toolbar {
                    // Bell lives on the rail in the wide layout, so drop it from
                    // the (now hidden) nav bar there to avoid a duplicate.
                    if !wide {
                        ToolbarItem(placement: .topBarTrailing) {
                            NotificationBell()
                        }
                    }
                }
                .navigationDestination(for: String.self) { showId in
                    ShowDetailScreen(showId: showId, pendingSheet: $pendingShowSheet)
                }
                .navigationDestination(for: CollectionRoute.self) { route in
                    switch route {
                    case .detail(let id):
                        CollectionDetailScreen(collectionId: id)
                    }
                }
                .navigationDestination(for: NotificationRoute.self) { _ in
                    NotificationsInboxScreen()
                }
        }
        .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer, enabled: !suppressMini)
        .offlineBanner(isConnected: container.networkMonitor.isConnected, isRetrying: container.streamPlayer.isRetrying, errorMessage: playbackBannerError)
    }

    private func searchSection(suppressMini: Bool = false, wide: Bool = false) -> some View {
        NavigationStack(path: $searchStack) {
            SearchScreen(resetToken: searchResetToken)
                .settingsLogoButton($showingSettings, title: "Search", wide: wide)
                .navigationDestination(for: String.self) { showId in
                    ShowDetailScreen(showId: showId, pendingSheet: $pendingShowSheet)
                }
        }
        .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer, enabled: !suppressMini)
        .offlineBanner(isConnected: container.networkMonitor.isConnected, isRetrying: container.streamPlayer.isRetrying, errorMessage: playbackBannerError)
    }

    private func favoritesSection(suppressMini: Bool = false, wide: Bool = false) -> some View {
        NavigationStack(path: $favoritesStack) {
            FavoritesScreen()
                .settingsLogoButton($showingSettings, title: "Favorites", wide: wide)
                .navigationDestination(for: String.self) { showId in
                    ShowDetailScreen(showId: showId, pendingSheet: $pendingShowSheet)
                }
                .navigationDestination(for: FavoritesRoute.self) { route in
                    switch route {
                    case .downloads:
                        DownloadsScreen()
                    }
                }
        }
        .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer, enabled: !suppressMini)
        .offlineBanner(isConnected: container.networkMonitor.isConnected, isRetrying: container.streamPlayer.isRetrying, errorMessage: playbackBannerError)
    }

    private func collectionsSection(suppressMini: Bool = false, wide: Bool = false) -> some View {
        NavigationStack(path: $collectionsStack) {
            CollectionsScreen()
                .settingsLogoButton($showingSettings, title: "Collections", wide: wide)
                .navigationDestination(for: CollectionRoute.self) { route in
                    switch route {
                    case .detail(let id):
                        CollectionDetailScreen(collectionId: id)
                    }
                }
                .navigationDestination(for: String.self) { showId in
                    ShowDetailScreen(showId: showId, pendingSheet: $pendingShowSheet)
                }
        }
        .miniPlayer(miniPlayerService: container.miniPlayerService, showFullPlayer: $showFullPlayer, enabled: !suppressMini)
        .offlineBanner(isConnected: container.networkMonitor.isConnected, isRetrying: container.streamPlayer.isRetrying, errorMessage: playbackBannerError)
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

enum AppTab: String, Hashable, CaseIterable {
    case home, search, favorites, collections

    var title: String { rawValue.capitalized }

    var systemImage: String {
        switch self {
        case .home: return "house"
        case .search: return "magnifyingglass"
        case .favorites: return "heart.fill"
        case .collections: return "square.stack"
        }
    }
}

// MARK: - Navigation Sidebar (wide layout)

/// Icon-only vertical rail shown at regular width in place of the bottom tab
/// bar. Drives the same `selectedTab` the TabView uses.
private struct NavSidebar: View {
    @Environment(\.appContainer) private var container
    @Binding var selectedTab: AppTab
    var onSettings: () -> Void
    var onNotifications: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                let isSelected = tab == selectedTab
                Button {
                    selectedTab = tab
                } label: {
                    Image(systemName: tab.systemImage)
                        .font(.title2)
                        .frame(width: 44, height: 44)
                        .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(isSelected ? Color.accentColor.opacity(0.15) : Color.clear)
                        )
                }
                .accessibilityLabel(tab.title)
            }

            Spacer()

            // Notifications bell just above settings — the wide layout's home for
            // the bell that sits in the nav bar on narrow screens.
            notificationsButton

            // Settings pinned to the bottom of the rail (uses the otherwise
            // empty vertical space the wide rail frees up).
            Button {
                onSettings()
            } label: {
                Image(systemName: "gearshape")
                    .font(.title2)
                    .frame(width: 44, height: 44)
                    .foregroundStyle(Color.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Settings")
        }
        .padding(.vertical, 12)
        .frame(width: 72)
        .frame(maxHeight: .infinity)
        .background(.bar)
    }

    /// Rail bell with the unread badge. Unlike the nav-bar `NotificationBell`
    /// (a NavigationLink), the rail has no NavigationStack, so this drives the
    /// home stack via the `onNotifications` closure instead.
    private var notificationsButton: some View {
        let store = container.notificationStore
        let unread = store.notifications.unreadCount(appVersion: store.appVersion)
        return Button {
            onNotifications()
        } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell")
                    .font(.title2)
                    .frame(width: 44, height: 44)
                    .foregroundStyle(Color.secondary)
                if unread > 0 {
                    Text(unread > 99 ? "99+" : "\(unread)")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(Color.red, in: Capsule())
                        .offset(x: -4, y: 4)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(unread > 0 ? "Notifications, \(unread) unread" : "Notifications")
    }
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
    @ViewBuilder
    func settingsLogoButton(_ showingSettings: Binding<Bool>, title: String, wide: Bool = false) -> some View {
        if wide {
            // Wide layout: the rail carries settings + the bell and the selected
            // icon already names the section, so the nav bar (with its "Home"/
            // "Search"/… title) is pure overhead. Hide it to reclaim the height.
            self.toolbar(.hidden, for: .navigationBar)
        } else {
            self.navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { showingSettings.wrappedValue = true } label: {
                            HStack(spacing: 8) {
                                Image("deadly_logo")
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 28, height: 28)
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
                // Cap the width so the drawer reads as a left panel rather than
                // (nearly) the whole screen on a wide landscape / tablet layout.
                .frame(width: min(UIScreen.main.bounds.width * 0.82, 400))
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
