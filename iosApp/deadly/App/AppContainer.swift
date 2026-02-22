import SwiftUI
import SwiftAudioStreamEx

/// Manual DI container. Owns all service instances for the app lifetime.
/// Inject via `.environment(\.appContainer, container)` at the root.
@Observable
final class AppContainer {
    let database: AppDatabase
    let dataImportService: DataImportService
    let showRepository: any ShowRepository
    let searchService: any SearchService
    let homeService: HomeServiceImpl
    let libraryService: LibraryServiceImpl
    let collectionsService: CollectionsServiceImpl
    let streamPlayer: StreamPlayer
    let playlistService: PlaylistServiceImpl
    let panelContentService: PanelContentService

    init() {
        do {
            let db = try AppDatabase.makeDefault()
            database = db
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
                recordingDAO: RecordingDAO(database: db)
            )
            showRepository = showRepo
            searchService = SearchServiceImpl(
                showSearchDAO: ShowSearchDAO(database: db),
                showDAO: ShowDAO(database: db),
                showRepository: showRepo
            )
            homeService = HomeServiceImpl(
                showRepository: showRepo,
                collectionsDAO: CollectionsDAO(database: db),
                recentShowDAO: RecentShowDAO(database: db)
            )
            libraryService = LibraryServiceImpl(
                database: db,
                libraryDAO: LibraryDAO(database: db),
                showRepository: showRepo
            )
            collectionsService = CollectionsServiceImpl(
                collectionsDAO: CollectionsDAO(database: db),
                showRepository: showRepo
            )
            // StreamPlayer is @MainActor; AppContainer is always created on the main
            // thread (from deadlyApp which is @MainActor), so assumeIsolated is safe.
            let player = MainActor.assumeIsolated { StreamPlayer() }
            streamPlayer = player
            playlistService = PlaylistServiceImpl(
                showRepository: showRepo,
                archiveClient: URLSessionArchiveMetadataClient(),
                recentShowDAO: RecentShowDAO(database: db),
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
