import SwiftUI
import SwiftAudioStreamEx

/// Root TabView with independent NavigationStack per tab.
struct MainNavigation: View {
    @Environment(\.appContainer) private var container
    @State private var showFullPlayer = false

    var body: some View {
        TabView {
            Tab("Home", systemImage: "house") {
                NavigationStack {
                    HomeScreen()
                        .navigationDestination(for: String.self) { showId in
                            ShowDetailScreen(showId: showId)
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
                    PlaceholderScreen(tab: .library)
                }
                .miniPlayer(streamPlayer: container.streamPlayer, showFullPlayer: $showFullPlayer)
            }
            Tab("Collections", systemImage: "square.stack") {
                NavigationStack {
                    PlaceholderScreen(tab: .collections)
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
        .fullScreenCover(isPresented: $showFullPlayer) {
            PlayerScreen(
                streamPlayer: container.streamPlayer,
                isPresented: $showFullPlayer
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
