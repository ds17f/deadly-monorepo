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
                        // Restore last playback position if the app was killed mid-playback.
                        await container.playbackRestorationService.restoreIfAvailable()
                    }
                }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .background {
                container.playbackRestorationService.saveNow()
            }
        }
    }
}
