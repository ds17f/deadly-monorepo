import SwiftUI

@main
struct deadlyApp: App {
    @State private var container = AppContainer()
    @State private var showingImport = false

    var body: some Scene {
        WindowGroup {
            MainNavigation()
                .environment(\.appContainer, container)
                .fullScreenCover(isPresented: $showingImport) {
                    DataImportScreen(isPresented: $showingImport)
                        .environment(\.appContainer, container)
                }
                .task {
                    // Show the import screen if the DB has no data yet.
                    if (try? container.database.read { db in
                        try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM data_version") ?? 0
                    }) == 0 {
                        showingImport = true
                    }
                }
        }
    }
}
