import SwiftUI
import UIKit

/// App Delegate to handle background URL session events.
class DeadlyAppDelegate: NSObject, UIApplicationDelegate {
    var container: AppContainer?

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        guard identifier == "com.grateful.deadly.downloads" else {
            completionHandler()
            return
        }
        container?.downloadService.handleBackgroundSessionCompletion(completionHandler)
    }
}

@main
struct deadlyApp: App {
    @UIApplicationDelegateAdaptor(DeadlyAppDelegate.self) private var appDelegate
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
                    // Wire app delegate to container for background session handling
                    appDelegate.container = container

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
