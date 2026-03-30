import AVFoundation
import SwiftUI
import SwiftAudioStreamEx
#if canImport(UIKit)
import UIKit
#endif

/// Manual DI container. Owns all service instances for the app lifetime.
/// Inject via `.environment(\.appContainer, container)` at the root.
@Observable
final class AppContainer {
    let database: AppDatabase
    let appPreferences: AppPreferences
    let dataImportService: DataImportService
    let favoritesImportExportService: FavoritesImportExportService
    let reviewService: ReviewService
    let showRepository: any ShowRepository
    let searchService: SearchServiceImpl
    let homeService: HomeServiceImpl
    let favoritesService: FavoritesServiceImpl
    let collectionsService: CollectionsServiceImpl
    let streamPlayer: StreamPlayer
    let playlistService: PlaylistServiceImpl
    let panelContentService: PanelContentService
    let networkMonitor: NetworkMonitor
    let recentShowsService: RecentShowsServiceImpl
    let miniPlayerService: MiniPlayerServiceImpl
    let archiveClient: URLSessionArchiveMetadataClient
    let downloadService: DownloadServiceImpl
    let equalizerService: EqualizerService
    let authService: AuthService
    let playbackRestorationService: PlaybackRestorationService
    let analyticsService: AnalyticsService

    init() {
        let initStart = CFAbsoluteTimeGetCurrent()
        // Configure audio session for background playback at app launch
        #if os(iOS)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            print("Failed to configure audio session at launch: \(error)")
        }
        #endif

        do {
            let db = try AppDatabase.makeDefault()
            database = db
            let prefs = AppPreferences()
            appPreferences = prefs
            let analytics = AnalyticsService(appPreferences: prefs, apiKey: Secrets.analyticsApiKey)
            authService = MainActor.assumeIsolated { AuthService(appPreferences: prefs) }
            dataImportService = DataImportService(
                gitHubClient: URLSessionGitHubReleasesClient(),
                zipExtractor: ZipExtractor(),
                showDAO: ShowDAO(database: db),
                recordingDAO: RecordingDAO(database: db),
                collectionsDAO: CollectionsDAO(database: db),
                showSearchDAO: ShowSearchDAO(database: db),
                dataVersionDAO: DataVersionDAO(database: db),
                favoritesDAO: FavoritesDAO(database: db),
                analyticsService: analytics
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
                appPreferences: prefs,
                analyticsService: analytics
            )
            let revService = ReviewService(
                showReviewDAO: ShowReviewDAO(database: db),
                favoriteSongDAO: FavoriteSongDAO(database: db),
                showPlayerTagDAO: ShowPlayerTagDAO(database: db),
                showDAO: ShowDAO(database: db)
            )
            reviewService = revService
            favoritesService = FavoritesServiceImpl(
                database: db,
                favoritesDAO: FavoritesDAO(database: db),
                showReviewDAO: ShowReviewDAO(database: db),
                showRepository: showRepo,
                reviewService: revService,
                analyticsService: analytics
            )
            favoritesImportExportService = FavoritesImportExportService(
                favoritesDAO: FavoritesDAO(database: db),
                showDAO: ShowDAO(database: db),
                showReviewDAO: ShowReviewDAO(database: db),
                favoriteSongDAO: FavoriteSongDAO(database: db),
                playerTagDAO: ShowPlayerTagDAO(database: db),
                recordingPreferenceDAO: RecordingPreferenceDAO(database: db),
                appPreferences: appPreferences
            )
            collectionsService = CollectionsServiceImpl(
                collectionsDAO: CollectionsDAO(database: db),
                showRepository: showRepo
            )

            // StreamPlayer is @MainActor; AppContainer is always created on the main
            // thread (from deadlyApp which is @MainActor), so assumeIsolated is safe.
            let player = MainActor.assumeIsolated { StreamPlayer() }
            streamPlayer = player

            // Fallback artwork for lock screen / Now Playing when artwork unavailable.
            // Uses a music note SF Symbol rendered to a square image.
            #if os(iOS)
            MainActor.assumeIsolated {
                let size: CGFloat = 300
                let config = UIImage.SymbolConfiguration(pointSize: 120, weight: .light)
                if let symbol = UIImage(systemName: "music.note", withConfiguration: config) {
                    let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size))
                    let fallback = renderer.image { ctx in
                        UIColor(red: 0x1A / 255, green: 0x1A / 255, blue: 0x1A / 255, alpha: 1).setFill()
                        ctx.fill(CGRect(origin: .zero, size: CGSize(width: size, height: size)))
                        let tinted = symbol.withTintColor(.white.withAlphaComponent(0.5), renderingMode: .alwaysOriginal)
                        let symSize = tinted.size
                        tinted.draw(in: CGRect(
                            x: (size - symSize.width) / 2,
                            y: (size - symSize.height) / 2,
                            width: symSize.width,
                            height: symSize.height
                        ))
                    }
                    player.setFallbackArtwork(fallback)
                }
            }
            #endif

            // EqualizerService is @MainActor; attaches AVAudioUnitEQ to the player's audio engine
            equalizerService = MainActor.assumeIsolated {
                EqualizerService(streamPlayer: player, preferences: prefs)
            }

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

            // HomeService depends on RecentShowsService
            homeService = HomeServiceImpl(
                showRepository: showRepo,
                collectionsDAO: CollectionsDAO(database: db),
                recentShowsService: recentService
            )

            let archive = URLSessionArchiveMetadataClient()
            archiveClient = archive

            // DownloadService is @MainActor; manage offline downloads
            let downloadSvc = MainActor.assumeIsolated {
                DownloadServiceImpl(
                    archiveClient: archive,
                    showRepository: showRepo,
                    favoritesDAO: FavoritesDAO(database: db),
                    downloadTaskDAO: DownloadTaskDAO(database: db),
                    storageManager: DownloadStorageManager(),
                    analyticsService: analytics
                )
            }
            downloadService = downloadSvc

            // PlaylistService depends on RecentShowsService and DownloadService
            let playlistSvc = PlaylistServiceImpl(
                showRepository: showRepo,
                archiveClient: archive,
                recentShowsService: recentService,
                recordingPreferenceDAO: RecordingPreferenceDAO(database: db),
                streamPlayer: player,
                downloadService: downloadSvc,
                analyticsService: analytics
            )
            playlistService = playlistSvc

            // PlaybackRestorationService — persists and restores playback position across kills
            let restorationSvc = MainActor.assumeIsolated {
                PlaybackRestorationService(
                    store: LastPlayedTrackStore(),
                    streamPlayer: player,
                    playlistService: playlistSvc
                )
            }
            playbackRestorationService = restorationSvc
            MainActor.assumeIsolated { restorationSvc.startMonitoring() }

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

            // Analytics — fire-and-forget anonymous usage tracking
            analyticsService = analytics
            let coldStartMs = Int((CFAbsoluteTimeGetCurrent() - initStart) * 1000)
            analytics.track("app_open")
            analytics.track("cold_start", props: ["duration_ms": coldStartMs])
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
