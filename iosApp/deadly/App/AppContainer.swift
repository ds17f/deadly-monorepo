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
    let trendingService: TrendingServiceImpl
    let popularService: PopularServiceImpl
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
    let userSyncAPIClient: UserSyncAPIClient
    let favoritesPushService: FavoritesPushService
    let userSyncApplyService: UserSyncApplyService
    let playbackRestorationService: PlaybackRestorationService
    let analyticsService: AnalyticsService
    let connectService: ConnectService

    /// True only during the first launch of the process. Cleared after Connect + restore complete.
    var isColdLaunch = true

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
            let auth = MainActor.assumeIsolated { AuthService(appPreferences: prefs, analyticsService: analytics) }
            authService = auth
            let userSync = MainActor.assumeIsolated {
                UserSyncAPIClient(appPreferences: prefs, authService: auth)
            }
            userSyncAPIClient = userSync
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
            // Build push service first — ReviewService needs it injected at
            // init for the song-favorite outbox enqueue.
            let pushSvc = MainActor.assumeIsolated {
                FavoritesPushService(
                    outbox: SyncOutboxDAO(database: db),
                    favoritesDAO: FavoritesDAO(database: db),
                    favoriteSongDAO: FavoriteSongDAO(database: db),
                    recentShowDAO: RecentShowDAO(database: db),
                    showReviewDAO: ShowReviewDAO(database: db),
                    showPlayerTagDAO: ShowPlayerTagDAO(database: db),
                    apiClient: userSync,
                    authService: auth
                )
            }
            favoritesPushService = pushSvc
            let revService = ReviewService(
                showReviewDAO: ShowReviewDAO(database: db),
                favoriteSongDAO: FavoriteSongDAO(database: db),
                showPlayerTagDAO: ShowPlayerTagDAO(database: db),
                showDAO: ShowDAO(database: db),
                analyticsService: analytics,
                favoritesPushService: pushSvc
            )
            reviewService = revService
            let favSvc = FavoritesServiceImpl(
                database: db,
                favoritesDAO: FavoritesDAO(database: db),
                favoriteSongDAO: FavoriteSongDAO(database: db),
                showReviewDAO: ShowReviewDAO(database: db),
                showRepository: showRepo,
                analyticsService: analytics
            )
            favoritesService = favSvc
            MainActor.assumeIsolated {
                favSvc.favoritesPushService = pushSvc
                // Start observation after construction so the @MainActor task
                // is launched in a defined place rather than inside init.
                favSvc.startObserving()
            }
            let applySvc = MainActor.assumeIsolated {
                UserSyncApplyService(
                    apiClient: userSync,
                    favoritesDAO: FavoritesDAO(database: db),
                    favoriteSongDAO: FavoriteSongDAO(database: db),
                    showReviewDAO: ShowReviewDAO(database: db),
                    showPlayerTagDAO: ShowPlayerTagDAO(database: db),
                    showDAO: ShowDAO(database: db),
                    authService: auth
                )
            }
            userSyncApplyService = applySvc
            MainActor.assumeIsolated { pushSvc.userSyncApplyService = applySvc }
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

            // Apply the persisted lock-screen / CarPlay control style.
            MainActor.assumeIsolated {
                player.setControlStyle(PlayerControlsStyle(rawValueOrDefault: prefs.playerControlsStyle))
            }

            // Set app logo as fallback for lock screen / Now Playing when artwork unavailable
            #if os(iOS)
            MainActor.assumeIsolated {
                if let logoImage = UIImage(named: "deadly_logo_square") {
                    player.setFallbackArtwork(logoImage)
                }
            }
            #endif

            // EqualizerService is @MainActor; attaches AVAudioUnitEQ to the player's audio engine
            equalizerService = MainActor.assumeIsolated {
                EqualizerService(streamPlayer: player, preferences: prefs)
            }

            // MiniPlayerService is @MainActor; thin adapter over StreamPlayer for the mini player UI.
            // Seed the skeleton from LastPlayedTrackStore so the bar renders immediately on launch,
            // before PlaybackRestorationService has finished loading the show/queue.
            let miniPlayer = MainActor.assumeIsolated { () -> MiniPlayerServiceImpl in
                let svc = MiniPlayerServiceImpl(streamPlayer: player)
                svc.seedRestoredTrack(LastPlayedTrackStore().load())
                return svc
            }
            miniPlayerService = miniPlayer

            // RecentShowsService is @MainActor; wires up automatic play tracking via StreamPlayer
            let recentService = MainActor.assumeIsolated {
                RecentShowsServiceImpl(
                    recentShowDAO: RecentShowDAO(database: db),
                    showRepository: showRepo,
                    streamPlayer: player,
                    favoritesPushService: pushSvc
                )
            }
            recentShowsService = recentService

            // One-time startup backfill: push all local data (favorites +
            // top recents) so a freshly-synced web profile isn't empty.
            // Best-effort — enqueued rows flush once signed in; the flag stops
            // it re-running. The same routine backs a manual "Sync now".
            MainActor.assumeIsolated {
                if !prefs.localBackfilledV1 {
                    prefs.localBackfilledV1 = true
                    Task { await pushSvc.enqueueAllLocalAndFlush() }
                }
            }

            // HomeService depends on RecentShowsService
            homeService = HomeServiceImpl(
                showRepository: showRepo,
                collectionsDAO: CollectionsDAO(database: db),
                recentShowsService: recentService
            )

            // Trending pulls from /api/trending and resolves IDs against
            // the local show catalog. @MainActor; safe to init inline.
            trendingService = MainActor.assumeIsolated {
                TrendingServiceImpl(
                    appPreferences: prefs,
                    showRepository: showRepo
                )
            }

            // "Fan Favorites" — show favorites ranked by kept/listened ratio.
            popularService = MainActor.assumeIsolated {
                PopularServiceImpl(
                    appPreferences: prefs,
                    showRepository: showRepo
                )
            }

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

            // ConnectService — WebSocket device presence + playback coordination
            let connect = MainActor.assumeIsolated {
                ConnectService(appPreferences: prefs, authService: auth, streamPlayer: player)
            }
            connectService = connect

            // Wire ConnectService into playback services (setter injection to avoid circular deps)
            MainActor.assumeIsolated {
                playlistSvc.connectService = connect
                miniPlayer.connectService = connect
                restorationSvc.connectService = connect
                connect.onLoadShow = { [weak playlistSvc, weak player] showId, trackIndex, positionMs, autoPlay in
                    guard let svc = playlistSvc else { return }
                    svc.suppressConnectNotify = true
                    defer { svc.suppressConnectNotify = false }
                    await svc.loadShow(showId)
                    guard !svc.tracks.isEmpty else { return }
                    let idx = min(trackIndex, svc.tracks.count - 1)
                    let seekPosition = TimeInterval(positionMs) / 1000.0
                    if seekPosition > 0 {
                        // Transfer with a position: load paused, arm the seek, then
                        // play() so playWithPendingSeek waits for .playing and lands on
                        // the right spot. loadQueue(autoPlay:true) would auto-start at 0
                        // and clear pendingSeekOnFirstPlay; the engine also drops seeks
                        // issued before it reaches .playing (the cold-network case).
                        svc.playTrack(at: idx, source: "connect", autoPlay: false)
                        player?.pendingSeekOnFirstPlay = seekPosition
                        // Reflect the position in the slider before any engine ticks arrive.
                        player?.applyOptimisticProgress(currentTime: seekPosition)
                        if autoPlay { player?.play() }
                    } else {
                        // New show / position 0: let the engine autoplay natively. Loading
                        // paused then calling play() races the async queue load and can
                        // stall ("changed tracks but never started"); native autoPlay
                        // starts playback once the stream is actually ready.
                        svc.playTrack(at: idx, source: "connect", autoPlay: autoPlay)
                    }
                }
            }
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
