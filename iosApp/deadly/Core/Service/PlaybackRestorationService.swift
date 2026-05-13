import Foundation
import os
import SwiftAudioStreamEx

/// Persists playback position across app kills and restores it on launch.
///
/// Saves state every 5 seconds while playback is active, and immediately
/// when the app enters the background. On the next launch, restores the
/// show, track index, and seek position before presenting the mini player.
@MainActor
final class PlaybackRestorationService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "PlaybackRestoration")
    private let store: LastPlayedTrackStore
    private let streamPlayer: StreamPlayer
    private let playlistService: PlaylistServiceImpl
    private var monitorTask: Task<Void, Never>?

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
    }

    func stopMonitoring() {
        monitorTask?.cancel()
        monitorTask = nil
    }

    /// Immediately persist current playback position. Call this when the app enters background.
    func saveNow() {
        saveCurrentStateIfActive()
    }

    // MARK: - Restore

    /// Restore the last saved playback position, if one exists.
    /// Loads the show from the database, fetches tracks from the network,
    /// then loads the queue WITHOUT auto-playing — the saved seek is stashed
    /// on `streamPlayer.pendingSeekOnFirstPlay` and applied when the user
    /// eventually presses play. No audio is produced during restore.
    func restoreIfAvailable() async {
        guard let saved = store.load() else {
            logger.info("[PB] No saved playback state to restore")
            return
        }
        logger.info("[PB] Restoring: '\(saved.trackTitle)' track \(saved.trackIndex) at \(saved.positionMs)ms")

        await playlistService.loadShow(saved.showId)

        guard !playlistService.tracks.isEmpty else {
            logger.warning("[PB] No tracks loaded for show \(saved.showId), skipping restore")
            return
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
        playlistService.playTrack(at: trackIndex, source: "restore", autoPlay: false)
        if seekPosition > 0 {
            streamPlayer.pendingSeekOnFirstPlay = seekPosition
            // Optimistically reflect the saved position in the published progress
            // so the slider and time label match before any engine ticks arrive.
            streamPlayer.applyOptimisticProgress(currentTime: seekPosition)
        }
        logger.info("[PB] Restored at track \(trackIndex), pendingSeek=\(seekPosition, format: .fixed(precision: 1))s — idle until user presses play")
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
