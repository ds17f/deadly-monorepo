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

    /// True while the mini player is showing a skeleton from saved state
    /// (e.g. on launch before the queue is loaded). Controls are disabled
    /// in this state because there's no engine queue yet to act on.
    var isSkeleton: Bool { get }

    /// True while the first-play seek dance is running. UI should show a
    /// spinner instead of the play/pause button during this window.
    var isPreparing: Bool { get }

    /// True while the engine is loading / buffering audio before playback
    /// can begin. Includes initial buffer after tapping play, and stalls
    /// mid-track when the network can't keep up.
    var isBuffering: Bool { get }

    /// Toggle between play and pause states.
    func togglePlayPause()

    /// Skip to the next track in the queue.
    func skipNext()
}
