import Foundation

/// All possible playback states, mapping to AVPlayer states.
enum PlaybackState: String, Codable, Sendable, Equatable, CaseIterable {
    /// No media loaded, player inactive.
    case idle = "IDLE"
    /// Media items being prepared (setItem + prepare in progress).
    case loading = "LOADING"
    /// Network loading content, can't play yet.
    case buffering = "BUFFERING"
    /// Ready to play but currently paused.
    case ready = "READY"
    /// Currently playing audio.
    case playing = "PLAYING"
    /// Playback finished, at end of track.
    case ended = "ENDED"

    static let `default` = PlaybackState.idle

    var isLoading: Bool { self == .loading || self == .buffering }
    var isReady: Bool { self == .ready || self == .playing || self == .ended }
    var isPlaying: Bool { self == .playing }
    var canPlay: Bool { self == .ready || self == .ended }
    var canPause: Bool { self == .playing }
}

struct PlaybackStatus: Codable, Sendable, Equatable {
    /// Current playback position in milliseconds.
    let currentPosition: Int64
    /// Track duration in milliseconds.
    let duration: Int64

    var progress: Float { duration > 0 ? Float(currentPosition) / Float(duration) : 0 }
    var hasValidDuration: Bool { duration > 0 }
    var hasStarted: Bool { currentPosition > 0 }

    static let empty = PlaybackStatus(currentPosition: 0, duration: 0)
}

struct QueueInfo: Sendable, Equatable {
    let currentIndex: Int
    let totalTracks: Int
    let isEmpty: Bool

    init(currentIndex: Int, totalTracks: Int) {
        self.currentIndex = currentIndex
        self.totalTracks = totalTracks
        self.isEmpty = totalTracks == 0
    }

    var isFirstTrack: Bool { currentIndex == 0 }
    var isLastTrack: Bool { currentIndex == totalTracks - 1 }
    var hasNext: Bool { !isEmpty && !isLastTrack }
    var hasPrevious: Bool { !isEmpty && !isFirstTrack }
    var queueProgress: Float { totalTracks > 0 ? Float(currentIndex) / Float(totalTracks) : 0 }

    static let empty = QueueInfo(currentIndex: 0, totalTracks: 0)
}

struct CurrentTrackInfo: Codable, Sendable, Equatable {
    // Track identification
    let trackUrl: String
    let recordingId: String
    let showId: String

    // Denormalized show data for immediate display
    let showDate: String        // e.g. "1977-05-08"
    let venue: String?          // e.g. "Barton Hall"
    let location: String?       // e.g. "Cornell University, Ithaca, NY"
    let coverImageUrl: String?

    // Track-specific data
    let songTitle: String       // e.g. "Scarlet Begonias"
    let artist: String          // e.g. "Grateful Dead"
    let album: String           // e.g. "May 8, 1977 - Barton Hall"
    let trackNumber: Int?
    let filename: String
    let format: String          // e.g. "mp3", "flac"

    // Playback state
    let playbackState: PlaybackState
    let position: Int64         // Current position in milliseconds
    let duration: Int64         // Track duration in milliseconds

    var displayTitle: String { songTitle.isEmpty ? "Unknown Track" : songTitle }

    /// Formatted show date, e.g. "May 8, 1977"
    var displayDate: String { DateFormatting.formatShowDate(showDate, style: .short) }

    /// Subtitle combining date and venue/location.
    var displaySubtitle: String {
        var result = ""
        if !showDate.isEmpty { result = showDate }
        if let v = venue, !v.isEmpty, v != "Unknown Venue" {
            if !showDate.isEmpty { result += " - " }
            result += v
        } else if let l = location, !l.isEmpty {
            if !showDate.isEmpty { result += " - " }
            result += l
        }
        return result
    }
}
