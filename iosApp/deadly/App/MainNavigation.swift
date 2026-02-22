import SwiftUI
import SwiftAudioStreamEx

/// Root TabView with independent NavigationStack per tab.
struct MainNavigation: View {
    @Environment(\.appContainer) private var container
    @State private var showFullPlayer = false
    @State private var homeStack = NavigationPath()
    @State private var lastPushedShowId: String?
    @State private var pendingShowNavigation: String?

    var body: some View {
        TabView {
            Tab("Home", systemImage: "house") {
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
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Search", systemImage: "magnifyingglass") {
                NavigationStack {
                    PlaceholderScreen(tab: .search)
                }
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Library", systemImage: "books.vertical") {
                NavigationStack {
                    LibraryScreen()
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
                        }
                }
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Collections", systemImage: "square.stack") {
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
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Settings", systemImage: "gearshape") {
                NavigationStack {
                    SettingsScreen()
                }
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
}

// MARK: - Tab enum

enum AppTab: String {
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
