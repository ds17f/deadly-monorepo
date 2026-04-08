import GoogleSignIn
import Intents
import SwiftUI
import UIKit

/// App Delegate to handle background URL session events, CarPlay scene routing, and Siri intents.
class DeadlyAppDelegate: NSObject, UIApplicationDelegate {
    static private(set) var shared: DeadlyAppDelegate!
    lazy var container = AppContainer()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        DeadlyAppDelegate.shared = self

        // Force opaque nav bar so scroll content doesn't show through
        let navAppearance = UINavigationBarAppearance()
        navAppearance.configureWithOpaqueBackground()
        UINavigationBar.appearance().standardAppearance = navAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navAppearance
        UINavigationBar.appearance().compactAppearance = navAppearance

        // Force opaque tab bar
        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithOpaqueBackground()
        UITabBar.appearance().standardAppearance = tabAppearance
        UITabBar.appearance().scrollEdgeAppearance = tabAppearance
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if connectingSceneSession.role == .carTemplateApplication {
            let config = UISceneConfiguration(name: "CarPlay", sessionRole: .carTemplateApplication)
            config.delegateClass = CarPlaySceneDelegate.self
            return config
        }
        let config = UISceneConfiguration(name: "Default", sessionRole: connectingSceneSession.role)
        return config
    }

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        guard identifier == "com.grateful.deadly.downloads" else {
            completionHandler()
            return
        }
        container.downloadService.handleBackgroundSessionCompletion(completionHandler)
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        if GIDSignIn.sharedInstance.handle(url) {
            return true
        }
        return false
    }

    func application(_ application: UIApplication, handlerFor intent: INIntent) -> Any? {
        if intent is INPlayMediaIntent {
            return DeadlyMediaIntentHandler(container: container)
        }
        return nil
    }
}

@main
struct deadlyApp: App {
    @UIApplicationDelegateAdaptor(DeadlyAppDelegate.self) private var appDelegate
    @State private var showingImport = false
    @Environment(\.scenePhase) private var scenePhase

    private var container: AppContainer { appDelegate.container }

    var body: some Scene {
        WindowGroup {
            MainNavigation()
                .tint(DeadlyColors.primary)
                .environment(\.appContainer, container)
                .fullScreenCover(isPresented: $showingImport, onDismiss: {
                    Task {
                        if let sourceTypes = try? RecordingDAO(database: container.database).fetchAllSourceTypes() {
                            ShowArtworkService.shared.populate(sourceTypes)
                        }
                        await container.homeService.refresh()
                        await container.playbackRestorationService.restoreIfAvailable()
                    }
                }) {
                    DataImportScreen(isPresented: $showingImport)
                        .environment(\.appContainer, container)
                }
                .task {
                    // Show the import screen if the DB has no data yet.
                    let hasData = ((try? container.database.read { db in
                        try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM data_version") ?? 0
                    }) ?? 0) > 0
                    if !hasData {
                        showingImport = true
                    } else {
                        // Populate source type badge service from database
                        if let sourceTypes = try? RecordingDAO(database: container.database).fetchAllSourceTypes() {
                            ShowArtworkService.shared.populate(sourceTypes)
                        }
                        ShowArtworkService.shared.badgeStyle = SourceBadgeStyle.fromString(container.appPreferences.sourceBadgeStyle)
                        // Start Connect first so it can receive shared state before local restore
                        container.connectService.startIfAuthenticated()
                        // Brief wait for WebSocket to connect and receive state
                        try? await Task.sleep(for: .seconds(1.5))
                        // Restore last playback position only if Connect has no shared session
                        await container.playbackRestorationService.restoreIfAvailable()
                    }
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                container.playbackRestorationService.saveNow()
                container.connectService.stop()
            } else if newPhase == .active {
                container.connectService.startIfAuthenticated()
            }
        }
    }
}
