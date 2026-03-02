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
    /// starts playback, then immediately pauses at the saved seek position.
    func restoreIfAvailable() async {
        guard let saved = store.load() else {
            logger.info("No saved playback state to restore")
            return
        }
        logger.info("Restoring: '\(saved.trackTitle)' track \(saved.trackIndex) at \(saved.positionMs)ms")

        await playlistService.loadShow(saved.showId)

        guard !playlistService.tracks.isEmpty else {
            logger.warning("No tracks loaded for show \(saved.showId), skipping restore")
            return
        }

        let trackIndex = min(saved.trackIndex, playlistService.tracks.count - 1)
        let seekPosition = TimeInterval(saved.positionMs) / 1000.0

        playlistService.playTrack(at: trackIndex)

        // The engine resolves redirects asynchronously before audio starts.
        // Poll until playing (or time out after 10 seconds).
        let deadline = Date.now.addingTimeInterval(10)
        while streamPlayer.playbackState != .playing && Date.now < deadline {
            try? await Task.sleep(for: .milliseconds(100))
        }

        guard streamPlayer.playbackState == .playing else {
            logger.warning("Timed out waiting for playback, skipping pause+seek")
            return
        }

        streamPlayer.pause()
        if seekPosition > 0 {
            streamPlayer.seek(to: seekPosition)
        }
        logger.info("Restored at track \(trackIndex), position \(seekPosition, format: .fixed(precision: 1))s — paused")
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
