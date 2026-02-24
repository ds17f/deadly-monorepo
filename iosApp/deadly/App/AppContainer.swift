import SwiftUI
import SwiftAudioStreamEx

/// Manual DI container. Owns all service instances for the app lifetime.
/// Inject via `.environment(\.appContainer, container)` at the root.
@Observable
final class AppContainer {
    let database: AppDatabase
    let appPreferences: AppPreferences
    let dataImportService: DataImportService
    let libraryImportExportService: LibraryImportExportService
    let showRepository: any ShowRepository
    let searchService: SearchServiceImpl
    let homeService: HomeServiceImpl
    let libraryService: LibraryServiceImpl
    let collectionsService: CollectionsServiceImpl
    let streamPlayer: StreamPlayer
    let playlistService: PlaylistServiceImpl
    let panelContentService: PanelContentService
    let networkMonitor: NetworkMonitor
    let recentShowsService: RecentShowsServiceImpl
    let miniPlayerService: MiniPlayerServiceImpl

    init() {
        do {
            let db = try AppDatabase.makeDefault()
            database = db
            let prefs = AppPreferences()
            appPreferences = prefs
            dataImportService = DataImportService(
                gitHubClient: URLSessionGitHubReleasesClient(),
                zipExtractor: ZipExtractor(),
                showDAO: ShowDAO(database: db),
                recordingDAO: RecordingDAO(database: db),
                collectionsDAO: CollectionsDAO(database: db),
                showSearchDAO: ShowSearchDAO(database: db),
                dataVersionDAO: DataVersionDAO(database: db),
                libraryDAO: LibraryDAO(database: db)
            )
            let showRepo = GRDBShowRepository(
                showDAO: ShowDAO(database: db),
                recordingDAO: RecordingDAO(database: db),
                appPreferences: prefs
            )
            showRepository = showRepo
            searchService = SearchServiceImpl(
                showSearchDAO: ShowSearchDAO(database: db),
                showDAO: ShowDAO(database: db),
                showRepository: showRepo,
                appPreferences: prefs
            )
            libraryService = LibraryServiceImpl(
                database: db,
                libraryDAO: LibraryDAO(database: db),
                showRepository: showRepo
            )
            libraryImportExportService = LibraryImportExportService(
                libraryDAO: LibraryDAO(database: db),
                showDAO: ShowDAO(database: db)
            )
            collectionsService = CollectionsServiceImpl(
                collectionsDAO: CollectionsDAO(database: db),
                showRepository: showRepo
            )

            // StreamPlayer is @MainActor; AppContainer is always created on the main
            // thread (from deadlyApp which is @MainActor), so assumeIsolated is safe.
            let player = MainActor.assumeIsolated { StreamPlayer() }
            streamPlayer = player

            // MiniPlayerService is @MainActor; thin adapter over StreamPlayer for the mini player UI
            let miniPlayer = MainActor.assumeIsolated {
                MiniPlayerServiceImpl(streamPlayer: player)
            }
            miniPlayerService = miniPlayer

            // RecentShowsService is @MainActor; wires up automatic play tracking via StreamPlayer
            let recentService = MainActor.assumeIsolated {
                RecentShowsServiceImpl(
                    recentShowDAO: RecentShowDAO(database: db),
                    showRepository: showRepo,
                    streamPlayer: player
                )
            }
            recentShowsService = recentService

            // HomeService and PlaylistService depend on RecentShowsService
            homeService = HomeServiceImpl(
                showRepository: showRepo,
                collectionsDAO: CollectionsDAO(database: db),
                recentShowsService: recentService
            )
            playlistService = PlaylistServiceImpl(
                showRepository: showRepo,
                archiveClient: URLSessionArchiveMetadataClient(),
                recentShowsService: recentService,
                libraryDAO: LibraryDAO(database: db),
                streamPlayer: player
            )

            // PanelContentService is @MainActor; AppContainer.init is always called on
            // the main thread (from deadlyApp which is @MainActor), so assumeIsolated is safe.
            panelContentService = MainActor.assumeIsolated {
                PanelContentService(
                    geniusService: URLSessionGeniusService(accessToken: Secrets.geniusAccessToken),
                    wikipediaService: URLSessionWikipediaService()
                )
            }

            // NetworkMonitor is @MainActor
            let monitor = MainActor.assumeIsolated { NetworkMonitor(appPreferences: prefs) }
            networkMonitor = monitor
            MainActor.assumeIsolated { monitor.start() }

            // Start playback observation after all services are wired
            MainActor.assumeIsolated { recentService.startObservingPlayback() }
        } catch {
            fatalError("Failed to open database: \(error)")
        }
    }
}

// MARK: - EnvironmentKey

private struct AppContainerKey: EnvironmentKey {
    static let defaultValue = AppContainer()
}

extension EnvironmentValues {
    var appContainer: AppContainer {
        get { self[AppContainerKey.self] }
        set { self[AppContainerKey.self] = newValue }
    }
}
