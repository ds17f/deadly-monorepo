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
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Search", systemImage: "magnifyingglass", value: .search) {
                NavigationStack {
                    SearchScreen(resetToken: searchResetToken)
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                }
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Library", systemImage: "books.vertical", value: .library) {
                NavigationStack {
                    LibraryScreen()
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                }
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
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
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Settings", systemImage: "gearshape", value: .settings) {
                NavigationStack {
                    SettingsScreen()
                }
                .offlineBanner(isConnected: container.networkMonitor.isConnected)
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
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
                if newTab == .search && selectedTab == .search {
                    // Re-tapped search — toggle back to browse
                    searchResetToken += 1
                }
                selectedTab = newTab
            }
        )
    }
}

// MARK: - Tab enum

enum AppTab: String, Hashable {
    case home, search, library, collections, settings

    var title: String { rawValue.capitalized }
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

// MARK: - SettingsScreen

struct SettingsScreen: View {
    @Environment(\.appContainer) private var container
    @State private var showingImport = false
    @State private var dataVersion: String?

    var body: some View {
        List {
            Section("Preferences") {
                Toggle(isOn: Binding(
                    get: { container.appPreferences.showOnlyRecordedShows },
                    set: { container.appPreferences.showOnlyRecordedShows = $0 }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Hide shows without recordings")
                        Text("Only show concerts that have audio recordings available")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Section("Database") {
                if let v = dataVersion {
                    LabeledContent("Version", value: v)
                } else {
                    LabeledContent("Version", value: "No data")
                }
                Button("Force Re-Import") {
                    showingImport = true
                }
                .foregroundStyle(.red)
            }
            Section("Developer") {
                NavigationLink("Cornell '77 Audio Demo") {
                    CornellDemoView()
                }
            }
        }
        .navigationTitle("Settings")
        .fullScreenCover(isPresented: $showingImport) {
            DataImportScreen(isPresented: $showingImport, force: true)
                .environment(\.appContainer, container)
        }
        .task {
            dataVersion = try? container.database.read { db in
                try String.fetchOne(db, sql: "SELECT dataVersion FROM data_version WHERE id = 1")
            }
        }
        .onChange(of: showingImport) { _, isShowing in
            if !isShowing {
                // Refresh version after re-import
                Task {
                    dataVersion = try? container.database.read { db in
                        try String.fetchOne(db, sql: "SELECT dataVersion FROM data_version WHERE id = 1")
                    }
                }
            }
        }
    }
}

#Preview {
    MainNavigation()
}
