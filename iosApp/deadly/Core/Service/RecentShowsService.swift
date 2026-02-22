import Foundation

/// RecentShowsService API contract
///
/// Provides recently played shows based on user listening behavior.
/// Tracks show-level plays and maintains chronological history for
/// quick access to recently enjoyed content.
///
/// Key responsibilities:
/// - Track when shows are played (any meaningful track play from show counts)
/// - Maintain recency-ordered list of played shows with deduplication
/// - Provide reactive updates when new shows are played
/// - Filter for meaningful listens using hybrid threshold (10sec OR 25% of track)
/// - Use UPSERT pattern to maintain single record per show
///
/// Architecture:
/// - Observes StreamPlayer for track changes
/// - Persists to database using RecentShowDAO
/// - Exposes reactive AsyncSequence for UI consumption
/// - Clean separation from business logic services
@MainActor
protocol RecentShowsService: AnyObject {

    /// Current list of recently played shows, ordered by most recent first.
    /// Updates automatically when new shows are played and pass meaningful play threshold.
    var recentShows: [Show] { get }

    /// AsyncSequence that emits updates when recent shows change.
    /// Subscribe to this for reactive UI updates.
    var recentShowsStream: AsyncStream<[Show]> { get }

    /// Manually record that a show was played.
    ///
    /// Typically called internally when track-level observation detects meaningful play,
    /// but can be used for explicit show-level tracking (e.g., "Play Random Track" button).
    ///
    /// Uses UPSERT logic:
    /// - If show exists: update lastPlayedTimestamp, increment playCount
    /// - If new show: insert with current timestamp and playCount = 1
    ///
    /// - Parameter showId: The ID of the show that was played
    func recordShowPlay(showId: String)

    /// Get recent shows with custom limit.
    ///
    /// Useful for different UI contexts that need different amounts:
    /// - HomeScreen: 8 shows for quick access
    /// - RecentShowsScreen: 20+ shows for full history
    /// - Recommendations: 5 shows for "more like recent" suggestions
    ///
    /// - Parameter limit: Maximum number of recent shows to return
    /// - Returns: List of shows ordered by most recent play first
    func getRecentShows(limit: Int) async -> [Show]

    /// Check if a specific show is in the recent shows list.
    ///
    /// Useful for:
    /// - UI highlighting ("Recently Played" badge)
    /// - Conditional behavior (skip recent shows in discovery)
    /// - Analytics (track re-plays of recent content)
    ///
    /// - Parameter showId: The show ID to check
    /// - Returns: True if show is in recent shows list
    func isShowInRecent(showId: String) async -> Bool

    /// Remove a specific show from recent shows history.
    ///
    /// User privacy feature - allows hiding specific shows from recent list.
    ///
    /// - Parameter showId: The show ID to remove from recent history
    func removeShow(showId: String) async

    /// Clear all recent shows history.
    ///
    /// Complete privacy reset - removes all recent show tracking.
    func clearRecentShows() async

    /// Start observing playback for automatic play tracking.
    /// Call this after the service is initialized and StreamPlayer is ready.
    func startObservingPlayback()

    /// Stop observing playback.
    func stopObservingPlayback()
}
