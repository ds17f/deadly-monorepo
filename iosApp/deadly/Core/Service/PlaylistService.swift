import Foundation

@MainActor
protocol PlaylistService: AnyObject {
    var currentShow: Show? { get }
    var currentRecording: Recording? { get }
    var tracks: [ArchiveTrack] { get }
    var isLoadingTracks: Bool { get }
    var trackLoadError: String? { get }

    // Reviews
    var reviews: [Review] { get }
    var isLoadingReviews: Bool { get }
    var reviewsError: String? { get }
    func loadReviews() async

    // Show navigation
    var hasNextShow: Bool { get }
    var hasPreviousShow: Bool { get }

    func loadShow(_ showId: String) async
    func selectRecording(_ recording: Recording) async
    /// Begin playback at `index`. `source` records where the play originated
    /// (e.g. "browse", "library_favorites", "deeplink", "restore") and is
    /// emitted on the resulting `playback_start` analytics event. If `autoPlay`
    /// is false, the queue is loaded but playback does not start — used by
    /// restore so the user has to actually press play to begin audio.
    func playTrack(at index: Int, source: String, autoPlay: Bool)
    func recordRecentPlay()

    /// Record that the user is about to skip forward/back so the next
    /// `playback_end` is attributed to the correct reason. Call immediately
    /// before invoking `streamPlayer.next()` / `.previous()`.
    func noteUserSkip(forward: Bool)

    // Navigate to adjacent shows by date
    func navigateToNextShow() async -> Bool
    func navigateToPreviousShow() async -> Bool
}

extension PlaylistService {
    /// Convenience that defaults `autoPlay` to true, preserving the pre-restore call sites.
    func playTrack(at index: Int, source: String) {
        playTrack(at: index, source: source, autoPlay: true)
    }
}
