import AVFoundation
import os.log
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
    let connectService: ConnectService
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
            let auth = MainActor.assumeIsolated { AuthService(appPreferences: prefs) }
            authService = auth
            connectService = MainActor.assumeIsolated {
                ConnectService(authService: auth, appPreferences: prefs)
            }
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

            // Wire Connect playback events to the player
            let connectLog = Logger(subsystem: "com.grateful.deadly", category: "ConnectPlayback")
            let connectSvc = connectService
            MainActor.assumeIsolated {
                let sendUpdate: (String) -> Void = { [weak playlistSvc, weak player] status in
                    guard let playlistSvc, let player else { return }
                    guard let show = playlistSvc.currentShow else { return }
                    connectSvc.sendSessionUpdate(OutgoingPlaybackState(
                        showId: show.id,
                        recordingId: playlistSvc.currentRecording?.identifier ?? "",
                        trackIndex: player.queueState.currentIndex,
                        positionMs: Int(player.progress.currentTime * 1000),
                        durationMs: Int(player.progress.duration * 1000),
                        trackTitle: player.currentTrack?.title,
                        status: status,
                        date: show.date,
                        venue: show.venue.name,
                        location: show.location.displayText
                    ))
                }

                connectSvc.onPlaybackEvent = { [weak playlistSvc, weak player] event in
                    Task { @MainActor in
                        guard let playlistSvc, let player else {
                            connectLog.warning("[ConnectPlayback] playlistSvc or player deallocated, ignoring event")
                            return
                        }
                        switch event {
                        case .playOn(let state):
                            connectLog.info("[ConnectPlayback] PlayOn: showId=\(state.showId), recording=\(state.recordingId), track=\(state.trackIndex), positionMs=\(state.positionMs)")
                            await playlistSvc.loadShow(state.showId)
                            connectLog.info("[ConnectPlayback] After loadShow: tracks=\(playlistSvc.tracks.count), recording=\(playlistSvc.currentRecording?.identifier ?? "nil")")
                            if state.recordingId != playlistSvc.currentRecording?.identifier,
                               let recording = try? showRepo.getRecordingById(state.recordingId) {
                                await playlistSvc.selectRecording(recording)
                                connectLog.info("[ConnectPlayback] Switched to recording: \(recording.identifier)")
                            }
                            playlistSvc.playTrack(at: state.trackIndex)
                            connectLog.info("[ConnectPlayback] After playTrack: playbackState=\(String(describing: player.playbackState))")
                            // Safety: ensure playback starts even if loadQueue doesn't auto-play
                            // in this non-user-initiated context
                            if !player.playbackState.isPlaying {
                                player.play()
                                connectLog.info("[ConnectPlayback] Explicit play() called")
                            }
                            if state.positionMs > 0 {
                                // Wait for the player to finish loading before seeking,
                                // otherwise the seek is lost
                                for _ in 0..<50 {
                                    if player.playbackState == .playing || player.playbackState == .buffering {
                                        break
                                    }
                                    try? await Task.sleep(for: .milliseconds(100))
                                }
                                player.seek(to: TimeInterval(state.positionMs) / 1000.0)
                                connectLog.info("[ConnectPlayback] Seeked to \(state.positionMs)ms")
                            }
                            sendUpdate("playing")
                        case .command(let cmd):
                            connectLog.info("[ConnectPlayback] Command: \(cmd.action)")
                            switch cmd.action {
                            case "play":
                                player.play()
                                sendUpdate("playing")
                            case "pause":
                                player.pause()
                                sendUpdate("paused")
                            case "next": player.next()
                            case "prev": player.previous()
                            case "seek":
                                if let ms = cmd.seekMs {
                                    player.seek(to: TimeInterval(ms) / 1000.0)
                                }
                            default: break
                            }
                        case .stop:
                            connectLog.info("[ConnectPlayback] Stop: pausing playback")
                            player.pause()
                            sendUpdate("stopped")
                        }
                    }
                }
            }

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
