import Foundation

/// Protocol for managing offline downloads.
@MainActor
protocol DownloadService: AnyObject {
    /// All current download progress, keyed by show ID.
    var allProgress: [String: ShowDownloadProgress] { get }

    /// Get the download status for a show.
    func downloadStatus(for showId: String) -> LibraryDownloadStatus

    /// Check if a track is downloaded.
    func isTrackDownloaded(recordingId: String, trackFilename: String) -> Bool

    /// Get the local URL for a downloaded track.
    func localURL(for recordingId: String, trackFilename: String) -> URL?

    /// Get per-track download states for a show, keyed by track filename.
    func trackDownloadStates(for showId: String) -> [String: TrackDownloadState]

    /// Start downloading a show.
    func downloadShow(_ showId: String, recordingId: String?) async throws

    /// Pause downloads for a show.
    func pauseShow(_ showId: String)

    /// Resume downloads for a show.
    func resumeShow(_ showId: String)

    /// Cancel downloads for a show (removes partial files).
    func cancelShow(_ showId: String)

    /// Remove downloaded files for a show.
    func removeShow(_ showId: String)

    /// Remove all downloaded files.
    func removeAll()

    /// Get total storage used by downloads.
    func totalStorageUsed() -> Int64

    /// Get storage used by a specific show's downloads.
    func showStorageUsed(_ showId: String) -> Int64

    /// Get all show IDs that have downloads.
    func allDownloadedShowIds() -> [String]

    /// Handle background session completion (call from app delegate).
    func handleBackgroundSessionCompletion(_ completionHandler: @escaping () -> Void)
}
