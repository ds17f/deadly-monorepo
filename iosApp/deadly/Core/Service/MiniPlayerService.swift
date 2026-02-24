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

    /// Toggle between play and pause states.
    func togglePlayPause()

    /// Skip to the next track in the queue.
    func skipNext()
}
