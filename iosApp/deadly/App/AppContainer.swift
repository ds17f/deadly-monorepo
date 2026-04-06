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
            let connect = MainActor.assumeIsolated {
                ConnectService(authService: auth, appPreferences: prefs)
            }
            connectService = connect
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
                let svc = MiniPlayerServiceImpl(streamPlayer: player, connectService: connect)
                svc.startInterpolationLoop()
                return svc
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
                // When true, the diff-detection loop skips one reaction cycle.
                // Set before applying a server-directed state change so the resulting
                // user_state echo doesn't re-trigger the same action.
                var suppressDiffReaction = false

                // When true, the announce observer skips one cycle.
                // Set before the diff loop applies a server-originated change
                // so the resulting local state change doesn't echo back.
                var suppressAnnounce = false

                // When true, the periodic 5s reporter skips sending updates.
                // Prevents the brief muted play inside seekAndSettle from leaking
                // a "playing" status to the server.
                var suppressUpdates = false

                // Previous user_state for diff detection
                var prevIsPlaying: Bool?
                var prevTrackIndex: Int?
                var prevPositionMs: Int?

                let sendUpdate: (String, Bool) -> Void = { [weak playlistSvc, weak player] status, includeTracks in
                    guard let playlistSvc, let player else { return }
                    guard let show = playlistSvc.currentShow else { return }
                    let posMs = Int(player.progress.currentTime * 1000)
                    let durMs = Int(player.progress.duration * 1000)
                    let trackIdx = player.queueState.currentIndex
                    connectLog.notice("[ConnectPlayback] sendUpdate: status=\(status, privacy: .public), track=\(trackIdx), pos=\(posMs)ms, dur=\(durMs)ms")
                    let tracks: [SessionTrack]? = includeTracks ? playlistSvc.tracks.map { track in
                        SessionTrack(
                            title: track.title,
                            duration: track.durationInterval ?? 0
                        )
                    } : nil
                    connectSvc.sendSessionUpdate(OutgoingPlaybackState(
                        showId: show.id,
                        recordingId: playlistSvc.currentRecording?.identifier ?? "",
                        trackIndex: trackIdx,
                        positionMs: posMs,
                        durationMs: durMs,
                        trackTitle: player.currentTrack?.title,
                        status: status,
                        date: show.date,
                        venue: show.venue.name,
                        location: show.location.displayText,
                        tracks: tracks
                    ))
                }

                connectSvc.onPlaybackEvent = { [weak playlistSvc, weak player] event in
                    Task { @MainActor in
                        guard let playlistSvc, let player else {
                            connectLog.warning("[ConnectPlayback] playlistSvc or player deallocated, ignoring event")
                            return
                        }
                        switch event {
                        case .playOn(let state, let relayedAt):
                            // Compensate position for server relay + network transit time
                            let nowMs = Int(Date().timeIntervalSince1970 * 1000)
                            let shouldPlay = state.status != "paused"
                            let adjustedMs: Int
                            if let relayedAt, shouldPlay {
                                adjustedMs = state.positionMs + (nowMs - relayedAt)
                            } else {
                                adjustedMs = state.positionMs
                            }
                            connectLog.notice("[ConnectPlayback] PlayOn: showId=\(state.showId, privacy: .public), recording=\(state.recordingId, privacy: .public), track=\(state.trackIndex), positionMs=\(state.positionMs), adjustedMs=\(adjustedMs)")
                            // Suppress updates until seek is settled to avoid broadcasting pos=0
                            // Also suppress diff reactions so the diff loop doesn't pause
                            // the player while we're setting up playback
                            suppressUpdates = true
                            suppressDiffReaction = true
                            await playlistSvc.loadShow(state.showId)
                            connectLog.notice("[ConnectPlayback] After loadShow: tracks=\(playlistSvc.tracks.count), recording=\(playlistSvc.currentRecording?.identifier ?? "nil", privacy: .public)")
                            if state.recordingId != playlistSvc.currentRecording?.identifier,
                               let recording = try? showRepo.getRecordingById(state.recordingId) {
                                await playlistSvc.selectRecording(recording)
                                connectLog.notice("[ConnectPlayback] Switched to recording: \(recording.identifier, privacy: .public)")
                            }
                            let loaded = playlistSvc.playTrack(at: state.trackIndex)
                            connectLog.notice("[ConnectPlayback] playTrack(\(state.trackIndex)) → \(loaded ? "OK" : "FAILED", privacy: .public), currentTrack=\(player.currentTrack?.title ?? "nil", privacy: .public)")
                            guard loaded else {
                                suppressUpdates = false
                                connectLog.error("[ConnectPlayback] Aborting — playTrack failed, not sending update to avoid corrupting server state")
                                return
                            }
                            // Wait for engine to be ready before seeking
                            var readyLoops = 0
                            for _ in 0..<50 {
                                if player.playbackState == .playing || player.playbackState == .buffering { break }
                                try? await Task.sleep(for: .milliseconds(100))
                                readyLoops += 1
                            }
                            if readyLoops >= 50 {
                                connectLog.warning("[ConnectPlayback] Timed out waiting for playing/buffering (state=\(String(describing: player.playbackState)))")
                            }
                            if shouldPlay {
                                if !player.playbackState.isPlaying {
                                    player.play()
                                    connectLog.notice("[ConnectPlayback] Explicit play() called")
                                }
                                if adjustedMs > 0 {
                                    // Use seekAndSettle so the seek actually takes effect
                                    await player.seekAndSettle(to: TimeInterval(adjustedMs) / 1000.0, shouldPause: false, delayMs: connectSvc.config.seekSettleDelayMs)
                                    connectLog.notice("[ConnectPlayback] seekAndSettle to \(adjustedMs)ms (playing)")
                                }
                            } else if adjustedMs > 0 {
                                await player.seekAndSettle(to: TimeInterval(adjustedMs) / 1000.0, shouldPause: true, delayMs: connectSvc.config.seekSettleDelayMs)
                                connectLog.notice("[ConnectPlayback] seekAndSettle to \(adjustedMs)ms (paused)")
                            } else {
                                player.pause()
                                connectLog.notice("[ConnectPlayback] Paused after loading (source was paused)")
                            }
                            suppressUpdates = false
                            // Suppress the diff loop from reacting to the server's
                            // echo of this update (which may carry a stale position)
                            suppressDiffReaction = true
                            // Send update with tracks so server can resolve next/prev
                            let reportedStatus = shouldPlay ? "playing" : "paused"
                            sendUpdate(reportedStatus, true)
                            connectLog.notice("[ConnectPlayback] Sent update: show=\(playlistSvc.currentShow?.id ?? "nil", privacy: .public), recording=\(playlistSvc.currentRecording?.identifier ?? "nil", privacy: .public), track=\(player.queueState.currentIndex), pos=\(Int(player.progress.currentTime * 1000))ms, status=\(reportedStatus, privacy: .public)")
                        case .stop:
                            connectLog.notice("[ConnectPlayback] Stop: pausing playback")
                            player.pause()
                            sendUpdate("stopped", false)
                        case .syncState(let state):
                            // Initial sync: always load the server's show/track so the
                            // UI reflects canonical state. Only start audio when this
                            // device is the active player.
                            guard let showId = state.showId, !showId.isEmpty else {
                                connectLog.notice("[ConnectPlayback] Initial sync: no show in server state")
                                break
                            }
                            let isThisDevice = state.activeDeviceId == connectSvc.deviceId
                            let shouldPlay = isThisDevice && state.isPlaying
                            connectLog.notice("[ConnectPlayback] Initial sync: show=\(showId, privacy: .public), track=\(state.trackIndex), playing=\(state.isPlaying), activeDevice=\(state.activeDeviceName ?? "none", privacy: .public), isThisDevice=\(isThisDevice)")
                            suppressAnnounce = true
                            suppressUpdates = true
                            await playlistSvc.loadShow(showId)
                            if let recId = state.recordingId,
                               recId != playlistSvc.currentRecording?.identifier,
                               let recording = try? showRepo.getRecordingById(recId) {
                                await playlistSvc.selectRecording(recording)
                            }
                            let loaded = playlistSvc.playTrack(at: state.trackIndex)
                            if loaded {
                                if shouldPlay {
                                    if state.positionMs > 0 {
                                        await player.seekAndSettle(to: TimeInterval(state.positionMs) / 1000.0, shouldPause: false, delayMs: connectSvc.config.seekSettleDelayMs)
                                    }
                                    connectLog.notice("[ConnectPlayback] Initial sync: resumed playback at track=\(state.trackIndex), pos=\(state.positionMs)ms")
                                } else if state.positionMs > 0 {
                                    await player.seekAndSettle(to: TimeInterval(state.positionMs) / 1000.0, shouldPause: true, delayMs: connectSvc.config.seekSettleDelayMs)
                                    connectLog.notice("[ConnectPlayback] Initial sync: loaded track=\(state.trackIndex), pos=\(state.positionMs)ms (paused)")
                                } else {
                                    player.pause()
                                    connectLog.notice("[ConnectPlayback] Initial sync: loaded track=\(state.trackIndex) (paused)")
                                }
                            }
                            suppressUpdates = false
                            suppressDiffReaction = true
                            connectLog.notice("[ConnectPlayback] Initial sync complete")
                        }
                    }
                }

                // React to server state diffs when this device is the active player
                Task { @MainActor [weak player] in
                    var lastUserState: UserPlaybackState?
                    while !Task.isCancelled {
                        try? await Task.sleep(for: .milliseconds(200))
                        guard let player else { return }
                        let current = connectSvc.userState

                        // Track prev state even when not active device
                        guard let state = current else {
                            lastUserState = nil
                            prevIsPlaying = nil
                            prevTrackIndex = nil
                            prevPositionMs = nil
                            continue
                        }

                        let isActive = state.activeDeviceId == connectSvc.deviceId

                        if !isActive {
                            prevIsPlaying = state.isPlaying
                            prevTrackIndex = state.trackIndex
                            prevPositionMs = state.positionMs
                            lastUserState = state
                            continue
                        }

                        guard let pPlaying = prevIsPlaying,
                              let pTrackIndex = prevTrackIndex,
                              let pPositionMs = prevPositionMs else {
                            prevIsPlaying = state.isPlaying
                            prevTrackIndex = state.trackIndex
                            prevPositionMs = state.positionMs
                            lastUserState = state
                            continue
                        }

                        // Only react if updatedAt actually changed (new server broadcast)
                        if state.updatedAt == (lastUserState?.updatedAt ?? 0) {
                            continue
                        }

                        // Skip this cycle if a local action (e.g. session_play_on) just
                        // sent an update — the echo would carry stale position data
                        if suppressDiffReaction {
                            suppressDiffReaction = false
                            connectLog.notice("[ConnectPlayback] Diff suppressed (echo): track=\(state.trackIndex), playing=\(state.isPlaying), pos=\(state.positionMs)ms")
                            prevIsPlaying = state.isPlaying
                            prevTrackIndex = state.trackIndex
                            prevPositionMs = state.positionMs
                            lastUserState = state
                            continue
                        }

                        connectLog.notice("[ConnectPlayback] Diff eval: server(track=\(state.trackIndex), playing=\(state.isPlaying), pos=\(state.positionMs)ms) vs prev(track=\(pTrackIndex), playing=\(pPlaying), pos=\(pPositionMs)ms), playerTrack=\(player.currentTrack?.title ?? "nil", privacy: .public)")

                        // Play/pause diff
                        if state.isPlaying != pPlaying {
                            suppressAnnounce = true
                            if state.isPlaying {
                                player.play()
                                connectLog.notice("[ConnectPlayback] State diff: play")
                            } else {
                                player.pause()
                                connectLog.notice("[ConnectPlayback] State diff: pause")
                            }
                        }

                        // Track index diff (next/prev)
                        if state.trackIndex != pTrackIndex {
                            suppressAnnounce = true
                            connectLog.notice("[ConnectPlayback] State diff: track \(pTrackIndex) → \(state.trackIndex), calling \(state.trackIndex > pTrackIndex ? "next()" : "previous()")")
                            if state.trackIndex > pTrackIndex {
                                player.next()
                            } else {
                                player.previous()
                            }
                        }

                        // Seek diff (divergence > threshold, same track & play state)
                        let seekThreshold = connectSvc.config.seekDivergenceThresholdMs
                        if state.trackIndex == pTrackIndex &&
                           state.isPlaying == pPlaying &&
                           abs(state.positionMs - pPositionMs) > seekThreshold {
                            let currentMs = Int(player.progress.currentTime * 1000)
                            if abs(state.positionMs - currentMs) > seekThreshold {
                                suppressAnnounce = true
                                player.seek(to: TimeInterval(state.positionMs) / 1000.0)
                                connectLog.notice("[ConnectPlayback] State diff: seek to \(state.positionMs)ms")
                            }
                        }

                        prevIsPlaying = state.isPlaying
                        prevTrackIndex = state.trackIndex
                        prevPositionMs = state.positionMs
                        lastUserState = state
                    }
                }

                // Periodic session_update while playing (keeps web progress bar in sync)
                // Only sends when THIS device is the active player.
                Task { @MainActor [weak player] in
                    while !Task.isCancelled {
                        let cfg = connectSvc.config
                        let intervalMs = cfg.positionUpdateIntervalMs
                        // Keep audio engine's redirect max age in sync with server config
                        player?.redirectMaxAge = TimeInterval(cfg.redirectMaxAgeSec)
                        try? await Task.sleep(for: .milliseconds(intervalMs))
                        guard !suppressUpdates else { continue }
                        guard connectSvc.receivedInitialState else { continue }
                        guard let player, player.playbackState.isPlaying else { continue }
                        guard connectSvc.connectionState == .connected else { continue }
                        // Only report when this device owns playback
                        guard connectSvc.userState?.activeDeviceId == connectSvc.deviceId else { continue }
                        sendUpdate("playing", false)
                    }
                }

                // Immediate announce: send session_update when local playback
                // state changes (play/pause, track change).  Mirrors the web's
                // useEffect that watches status + currentTrackIndex.
                // Only announces when this device is the active player or no
                // device is active (claiming the session on first local play).
                Task { @MainActor [weak player, weak playlistSvc] in
                    var lastIsPlaying: Bool?
                    var lastTrackIndex: Int?
                    while !Task.isCancelled {
                        try? await Task.sleep(for: .milliseconds(200))
                        guard let player, let playlistSvc else { return }
                        guard !suppressUpdates else { continue }
                        guard connectSvc.receivedInitialState else { continue }
                        guard connectSvc.connectionState == .connected else { continue }
                        // Only announce if there's a show loaded (skip startup noise)
                        guard playlistSvc.currentShow != nil else { continue }

                        let isPlaying = player.playbackState.isPlaying
                        let trackIndex = player.queueState.currentIndex

                        // Skip the first iteration (baseline capture)
                        guard lastIsPlaying != nil else {
                            lastIsPlaying = isPlaying
                            lastTrackIndex = trackIndex
                            continue
                        }

                        let playChanged = isPlaying != lastIsPlaying
                        let trackChanged = trackIndex != lastTrackIndex

                        lastIsPlaying = isPlaying
                        lastTrackIndex = trackIndex

                        guard playChanged || trackChanged else { continue }

                        if suppressAnnounce {
                            suppressAnnounce = false
                            connectLog.notice("[ConnectPlayback] Announce suppressed (server-originated)")
                            continue
                        }

                        // Only announce if this device is active or no device is active
                        let activeId = connectSvc.userState?.activeDeviceId
                        guard activeId == nil || activeId == connectSvc.deviceId else {
                            connectLog.notice("[ConnectPlayback] Announce skipped (another device is active)")
                            continue
                        }

                        let status = isPlaying ? "playing" : "paused"
                        // Include tracks on play start or track change so server has full context
                        let includeTracks = trackChanged || (playChanged && isPlaying)
                        sendUpdate(status, includeTracks)
                        // Suppress the diff loop from echoing this back
                        suppressDiffReaction = true
                        connectLog.notice("[ConnectPlayback] Announced: status=\(status, privacy: .public), includeTracks=\(includeTracks)")
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
    // Must NOT create a new AppContainer() here — that would open a second
    // WebSocket connection, and the server would route events to whichever
    // connected last, leaving the views' instance deaf.
    nonisolated(unsafe) static var defaultValue: AppContainer {
        DeadlyAppDelegate.shared.container
    }
}

extension EnvironmentValues {
    var appContainer: AppContainer {
        get { self[AppContainerKey.self] }
        set { self[AppContainerKey.self] = newValue }
    }
}
