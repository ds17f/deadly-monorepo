import Foundation

/// MiniPlayerService API contract
///
/// Thin reactive adapter that decouples `MiniPlayerOverlay` from the
/// `StreamPlayer` (SwiftAudioStreamEx) package. Exposes simplified,
/// pre-computed state for the mini player UI.
///
/// All properties are read from the underlying player and propagate
/// observation automatically via `@Observable`.
@MainActor
protocol MiniPlayerService: AnyObject {

    /// Whether the mini player should be visible (playback is active).
    var isVisible: Bool { get }

    /// Title of the currently playing track.
    var trackTitle: String? { get }

    /// Album title of the currently playing track.
    var albumTitle: String? { get }

    /// Archive.org recording ID extracted from the current track URL.
    /// Used to fetch show artwork.
    var artworkRecordingId: String? { get }

    /// Artwork URL string from the current track metadata.
    var artworkURL: String? { get }

    /// Whether audio is actively playing (not paused/buffering).
    var isPlaying: Bool { get }

    /// Whether there is a next track available in the queue.
    var hasNext: Bool { get }

    /// Whether there is a previous track available in the queue.
    var hasPrevious: Bool { get }

    /// Whether playback is in an error state.
    var hasError: Bool { get }

    /// Error message if playback failed, nil otherwise.
    var errorMessage: String? { get }

    /// Playback progress as a fraction from 0.0 to 1.0.
    var playbackProgress: Double { get }

    /// Show date from the currently playing track metadata.
    var showDate: String? { get }

    /// Venue name from the currently playing track metadata.
    var venue: String? { get }

    /// Formatted subtitle: "date - venue" matching Android format.
    var displaySubtitle: String? { get }

    /// Whether a command has been sent and we're waiting for the server to confirm.
    var isPendingCommand: Bool { get }

    /// Playback position in milliseconds (for remote state display).
    var positionMs: Int { get }

    /// Track duration in milliseconds (for remote state display).
    var durationMs: Int { get }

    /// Current track index (0-based).
    var trackIndex: Int { get }

    /// Total number of tracks in the session.
    var trackCount: Int { get }

    /// Toggle between play and pause states.
    func togglePlayPause()

    /// Skip to the next track in the queue.
    func skipNext()

    /// Skip to the previous track in the queue.
    func skipPrev()

    /// Seek to a position expressed as a fraction (0.0–1.0).
    func seek(fraction: Double)
}
