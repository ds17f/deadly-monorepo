import Foundation
import SwiftAudioStreamEx
import os

/// RecentShowsService implementation with database persistence and StreamPlayer observation.
///
/// Architecture:
/// - Observes StreamPlayer for track changes
/// - Applies smart filtering: 10 seconds MAX (25% only for tracks <40 seconds)
/// - Uses UPSERT pattern with RecentShowDAO for deduplication
/// - Provides reactive AsyncStream by converting database records to domain models
///
/// Key features:
/// - Smart play detection prevents rapid-skip spam
/// - Show-level tracking from track-level events
/// - Real-time UI updates via AsyncStream
/// - Persistent across app restarts
/// - Privacy controls (clear, remove specific shows)
@Observable
@MainActor
final class RecentShowsServiceImpl: RecentShowsService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "RecentShowsService")

    // MARK: - Constants

    private static let defaultRecentLimit = 8
    private static let meaningfulPlayDurationSeconds: TimeInterval = 10.0
    private static let meaningfulPlayPercentage: Double = 0.25
    private static let shortTrackThresholdSeconds: TimeInterval = 40.0

    // MARK: - Dependencies

    private let recentShowDAO: RecentShowDAO
    private let showRepository: any ShowRepository
    private let streamPlayer: StreamPlayer

    // MARK: - Observable state

    private(set) var recentShows: [Show] = []

    // MARK: - Internal tracking state

    private var currentTrackShowId: String?
    private var currentTrackStartTime: TimeInterval = 0
    private var hasRecordedCurrentTrack = false
    private var observationTask: Task<Void, Never>?
    private var streamContinuation: AsyncStream<[Show]>.Continuation?

    // MARK: - Init

    nonisolated init(
        recentShowDAO: RecentShowDAO,
        showRepository: some ShowRepository,
        streamPlayer: StreamPlayer
    ) {
        self.recentShowDAO = recentShowDAO
        self.showRepository = showRepository
        self.streamPlayer = streamPlayer
    }

    // MARK: - RecentShowsService

    var recentShowsStream: AsyncStream<[Show]> {
        AsyncStream { continuation in
            self.streamContinuation = continuation
            // Emit current value immediately
            continuation.yield(self.recentShows)

            continuation.onTermination = { _ in
                Task { @MainActor in
                    self.streamContinuation = nil
                }
            }
        }
    }

    func recordShowPlay(showId: String) {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        do {
            try recentShowDAO.upsert(showId: showId, timestamp: timestamp)
            logger.info("Recorded show play: \(showId)")
            Task {
                await refreshRecentShows()
            }
        } catch {
            logger.error("Failed to record show play: \(error.localizedDescription)")
        }
    }

    func getRecentShows(limit: Int) async -> [Show] {
        do {
            let records = try recentShowDAO.fetchRecent(limit: limit)
            let showIds = records.map(\.showId)
            let shows = try showRepository.getShowsByIds(showIds)
            // Maintain order from records (most recent first)
            let showsById = Dictionary(shows.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            return showIds.compactMap { showsById[$0] }
        } catch {
            logger.error("Failed to get recent shows: \(error.localizedDescription)")
            return []
        }
    }

    func isShowInRecent(showId: String) async -> Bool {
        do {
            return try recentShowDAO.fetchById(showId) != nil
        } catch {
            logger.error("Failed to check if show is recent: \(error.localizedDescription)")
            return false
        }
    }

    func removeShow(showId: String) async {
        do {
            try recentShowDAO.database.write { db in
                try db.execute(sql: "DELETE FROM recent_shows WHERE showId = ?", arguments: [showId])
            }
            logger.info("Removed show from recent: \(showId)")
            await refreshRecentShows()
        } catch {
            logger.error("Failed to remove show: \(error.localizedDescription)")
        }
    }

    func clearRecentShows() async {
        do {
            try recentShowDAO.clearAll()
            logger.info("Cleared all recent shows")
            await refreshRecentShows()
        } catch {
            logger.error("Failed to clear recent shows: \(error.localizedDescription)")
        }
    }

    // MARK: - Playback Observation

    func startObservingPlayback() {
        logger.info("Starting playback observation")

        // Load initial recent shows
        Task {
            await refreshRecentShows()
        }

        // Observe StreamPlayer state changes
        observationTask = Task { [weak self] in
            guard let self else { return }

            var lastTrackId: UUID?

            while !Task.isCancelled {
                // Poll for changes (withObservationTracking would be better but this works)
                try? await Task.sleep(for: .milliseconds(500))

                guard !Task.isCancelled else { break }

                let currentTrack = self.streamPlayer.currentTrack
                let playbackState = self.streamPlayer.playbackState
                let progress = self.streamPlayer.progress

                // Track changed
                if currentTrack?.id != lastTrackId {
                    lastTrackId = currentTrack?.id
                    self.handleTrackChange(track: currentTrack)
                }

                // Check for meaningful play while playing
                if playbackState.isPlaying && !self.hasRecordedCurrentTrack {
                    self.checkMeaningfulPlay(progress: progress)
                }
            }
        }
    }

    func stopObservingPlayback() {
        logger.info("Stopping playback observation")
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Private: Playback Handling

    private func handleTrackChange(track: TrackItem?) {
        // Reset tracking state
        hasRecordedCurrentTrack = false
        currentTrackStartTime = Date().timeIntervalSince1970

        // Extract showId from track metadata
        let showId = track?.metadata["showId"]
        currentTrackShowId = showId

        if let showId {
            logger.debug("Track changed, now tracking show: \(showId)")
        } else {
            logger.debug("Track changed, no showId in metadata")
        }
    }

    private func checkMeaningfulPlay(progress: PlaybackProgress) {
        guard let showId = currentTrackShowId else { return }

        let position = progress.currentTime
        let duration = progress.duration

        if shouldRecordPlay(position: position, duration: duration) {
            logger.info("Meaningful play detected for show: \(showId)")
            recordShowPlay(showId: showId)
            hasRecordedCurrentTrack = true
        }
    }

    /// Smart filtering: 10 seconds MAX, or 25% for very short tracks (<40 seconds)
    private func shouldRecordPlay(position: TimeInterval, duration: TimeInterval) -> Bool {
        // For tracks longer than 40 seconds: simple 10 second rule
        if duration > Self.shortTrackThresholdSeconds {
            return position >= Self.meaningfulPlayDurationSeconds
        }

        // For very short tracks (<=40 seconds): use 25% rule with 10s maximum
        if duration > 0 {
            let percentageThreshold = duration * Self.meaningfulPlayPercentage
            let actualThreshold = min(percentageThreshold, Self.meaningfulPlayDurationSeconds)
            return position >= actualThreshold
        }

        return false
    }

    // MARK: - Private: Data Loading

    private func refreshRecentShows() async {
        let shows = await getRecentShows(limit: Self.defaultRecentLimit)
        recentShows = shows
        streamContinuation?.yield(shows)
        logger.debug("Recent shows refreshed: \(shows.count) shows")
    }
}
