import Foundation
import os
import SwiftAudioStreamEx

/// Persists playback position across app kills and restores it on launch.
///
/// The ONLY writer of `LastPlayedTrackStore`. Saves every 5 seconds while
/// playback is active, immediately on pause and on track change (observed on
/// StreamPlayer), and when the app enters the background. Restores once per
/// process, at app level (scheduled by the app delegate's container, so a
/// CarPlay-only cold start restores too), and only into an idle player.
@MainActor
final class PlaybackRestorationService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "PlaybackRestoration")
    private let store: LastPlayedTrackStore
    private let streamPlayer: StreamPlayer
    private let playlistService: PlaylistServiceImpl
    var connectService: ConnectService?
    private var monitorTask: Task<Void, Never>?
    private var transitionObserverTask: Task<Void, Never>?

    /// Launch restore runs at most once per process. `.pending` again after an
    /// attempt that could not load tracks (e.g. empty catalog before the first
    /// data import), so the post-import retry can still run it.
    private enum LaunchRestoreState { case pending, running, done }
    private var launchRestoreState: LaunchRestoreState = .pending
    /// Process-wide guard: only one container schedules the launch restore.
    private static var launchRestoreScheduled = false
    /// Delay before the launch restore, so the root view can start Connect and
    /// receive the shared session first (the Connect guard below needs it).
    private static let launchRestoreDelay: Duration = .seconds(1.5)

    init(
        store: LastPlayedTrackStore,
        streamPlayer: StreamPlayer,
        playlistService: PlaylistServiceImpl
    ) {
        self.store = store
        self.streamPlayer = streamPlayer
        self.playlistService = playlistService
    }

    // MARK: - Monitoring

    func startMonitoring() {
        monitorTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                guard !Task.isCancelled else { break }
                self?.saveCurrentStateIfActive()
            }
        }
        startTransitionObservation()
    }

    func stopMonitoring() {
        monitorTask?.cancel()
        monitorTask = nil
        transitionObserverTask?.cancel()
        transitionObserverTask = nil
    }

    /// Immediately persist current playback position. Call this when the app enters background.
    func saveNow() {
        saveCurrentStateIfActive()
    }

    /// Save immediately on pause and on track change, in addition to the 5s timer,
    /// so a kill (or a CarPlay disconnect) right after either never restores a
    /// stale track/position. Observes StreamPlayer directly: every pause source
    /// (in-app, lock screen, headset, CarPlay, interruption) and every track change
    /// (auto-advance, skip, new queue) passes through these two properties.
    private func startTransitionObservation() {
        transitionObserverTask?.cancel()
        transitionObserverTask = Task { [weak self] in
            guard let self else { return }
            var lastTrackId = self.streamPlayer.currentTrack?.id
            var lastState = self.streamPlayer.playbackState
            while !Task.isCancelled {
                await withCheckedContinuation { continuation in
                    withObservationTracking {
                        _ = self.streamPlayer.playbackState
                        _ = self.streamPlayer.currentTrack
                    } onChange: {
                        continuation.resume()
                    }
                }
                guard !Task.isCancelled else { break }
                let state = self.streamPlayer.playbackState
                let trackId = self.streamPlayer.currentTrack?.id
                let trackChanged = trackId != nil && trackId != lastTrackId
                let paused = state == .paused && lastState != .paused
                lastTrackId = trackId
                lastState = state
                if trackChanged || paused {
                    self.logger.debug("[PB] Save on \(trackChanged ? "track change" : "pause", privacy: .public)")
                    self.saveCurrentStateIfActive()
                }
            }
        }
    }

    // MARK: - Restore

    /// Schedule the one-per-process launch restore. Called once from the app
    /// delegate's container, so it runs for a phone launch AND a CarPlay-only cold
    /// start (CarPlaySceneDelegate creates the same container). In the CarPlay case
    /// this is what gives the car's play button a queue to resume.
    func scheduleLaunchRestore() {
        guard !Self.launchRestoreScheduled else {
            logger.info("[PB] Launch restore already scheduled in this process — ignoring")
            return
        }
        Self.launchRestoreScheduled = true
        logger.info("[PB] Launch restore scheduled (in \(Self.launchRestoreDelay, privacy: .public))")
        Task { [weak self] in
            try? await Task.sleep(for: Self.launchRestoreDelay)
            await self?.restoreOnLaunchIfNeeded(trigger: "launch")
        }
    }

    /// Idempotent entry for the launch restore. Safe to call again (e.g. after the
    /// first data import): it is a no-op once a restore attempt has completed.
    func restoreOnLaunchIfNeeded(trigger: String) async {
        switch launchRestoreState {
        case .done:
            logger.info("[PB] Restore (\(trigger, privacy: .public)) skipped — already done in this process")
            return
        case .running:
            logger.info("[PB] Restore (\(trigger, privacy: .public)) skipped — already running")
            return
        case .pending:
            break
        }
        launchRestoreState = .running
        let completed = await restoreIfAvailable(trigger: trigger)
        launchRestoreState = completed ? .done : .pending
    }

    /// Why the restore must not touch the player right now, or nil if it may.
    /// The player is busy when it already has a queue or is active (CarPlay or
    /// Siri started playback, or Connect loaded the shared show) — reloading it
    /// would call loadQueue → engine.stop() and kill live audio.
    private func restoreBlocker() -> String? {
        if let connectRec = connectService?.connectState?.recordingId {
            return "Connect has recording \(connectRec)"
        }
        if let track = streamPlayer.currentTrack {
            return "player already has a queue ('\(track.title)', state=\(streamPlayer.playbackState))"
        }
        if streamPlayer.playbackState.isActive {
            return "player is active (state=\(streamPlayer.playbackState))"
        }
        return nil
    }

    /// Restore the last saved playback position, if one exists and the player is idle.
    /// Loads the show from the database, fetches tracks from the network,
    /// then loads the queue WITHOUT auto-playing — the saved seek is stashed
    /// on `streamPlayer.pendingSeekOnFirstPlay` and applied when the user
    /// eventually presses play. No audio is produced during restore, and Connect
    /// is NOT told about the restored queue.
    /// Returns false only when tracks could not be loaded, so a later retry may run.
    private func restoreIfAvailable(trigger: String) async -> Bool {
        // If Connect already has a recording loaded, skip local restore —
        // Connect's reactToState will load the correct shared show.
        if let blocker = restoreBlocker() {
            logger.info("[PB] Skipping restore (\(trigger, privacy: .public)) — \(blocker, privacy: .public)")
            return true
        }

        guard let saved = store.load() else {
            logger.info("[PB] No saved playback state to restore (\(trigger, privacy: .public))")
            return true
        }
        logger.info("[PB] Restoring (\(trigger, privacy: .public)): '\(saved.trackTitle)' track \(saved.trackIndex) at \(saved.positionMs)ms")

        // Pass the saved recordingId so we restore the EXACT recording we
        // were playing — not whatever the user's "preferred" or the show's
        // "best" recording happens to be now. Different recordings can have
        // different track listings (e.g. one has a "Tuning" track, the other
        // doesn't) and different title metadata.
        await playlistService.loadShow(saved.showId, recordingId: saved.recordingId)

        // The track fetch is a network round-trip. CarPlay, Siri or Connect may
        // have started playback meanwhile — re-check before touching the player.
        if let blocker = restoreBlocker() {
            logger.info("[PB] Abandoning restore after track load — \(blocker, privacy: .public)")
            return true
        }

        guard !playlistService.tracks.isEmpty else {
            logger.warning("[PB] No tracks loaded for show \(saved.showId), skipping restore (error: \(self.playlistService.trackLoadError ?? "none", privacy: .public)) — will retry on next trigger")
            return false
        }

        let trackIndex = min(saved.trackIndex, playlistService.tracks.count - 1)
        let seekPosition = TimeInterval(saved.positionMs) / 1000.0

        // Two cooperating gates suppress the phantom playback_start that
        // would otherwise fire when the user later presses play:
        //   1. `suppressNextStartEmission` — the +1s dwell `commitPendingPlayback`
        //      stashes the start info into `deferredStartInfo` instead of emitting.
        //   2. `isRestoring` — held for the entire restore body so the playback-state
        //      observer doesn't fire spurious analytics during the silent queue load.
        // The defer guarantees `isRestoring` clears even on early return.
        playlistService.isRestoring = true
        defer { playlistService.isRestoring = false }
        playlistService.suppressNextStartEmission = true

        // Load the queue without auto-playing. The slider will show the saved
        // position immediately via `pendingSeekOnFirstPlay` + the duration
        // fallback in `StreamPlayer.onProgressUpdate` (uses TrackItem.duration
        // when the engine hasn't reported one yet).
        // Suppress the Connect notify: a restored snapshot is old local state and
        // must not overwrite the shared session (sendLoad) or route to a remote
        // device.
        let previousSuppress = playlistService.suppressConnectNotify
        playlistService.suppressConnectNotify = true
        playlistService.playTrack(at: trackIndex, source: "restore", autoPlay: false)
        playlistService.suppressConnectNotify = previousSuppress
        if seekPosition > 0 {
            streamPlayer.pendingSeekOnFirstPlay = seekPosition
            // Optimistically reflect the saved position in the published progress
            // so the slider and time label match before any engine ticks arrive.
            streamPlayer.applyOptimisticProgress(currentTime: seekPosition)
        }
        logger.info("[PB] Restored at track \(trackIndex), pendingSeek=\(seekPosition, format: .fixed(precision: 1))s — idle until user presses play (remote commands registered, Connect not notified)")
        return true
    }

    // MARK: - Private

    private func saveCurrentStateIfActive() {
        guard streamPlayer.playbackState.isActive else { return }
        guard let track = streamPlayer.currentTrack else { return }
        guard let showId = track.metadata["showId"],
              let recordingId = track.metadata["recordingId"],
              !showId.isEmpty, !recordingId.isEmpty else { return }

        let saved = LastPlayedTrack(
            showId: showId,
            recordingId: recordingId,
            trackIndex: streamPlayer.queueState.currentIndex,
            positionMs: Int64(streamPlayer.progress.currentTime * 1000),
            trackTitle: track.title,
            showDate: track.metadata["showDate"] ?? "",
            venue: track.metadata["venue"],
            location: track.metadata["location"]
        )
        store.save(saved)
        logger.debug("Saved: '\(saved.trackTitle)' at \(saved.positionMs)ms")
    }
}
