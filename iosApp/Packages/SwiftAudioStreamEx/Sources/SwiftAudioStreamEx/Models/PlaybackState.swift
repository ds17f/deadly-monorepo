import Foundation

/// The current state of the audio player.
public enum PlaybackState: Sendable, Equatable {
    case idle
    case loading
    case buffering
    case playing
    case paused
    case ended
    case error(StreamPlayerError)

    public var isPlaying: Bool {
        self == .playing
    }

    public var isPaused: Bool {
        self == .paused
    }

    public var isActive: Bool {
        switch self {
        case .playing, .paused, .buffering, .loading:
            return true
        default:
            return false
        }
    }

    public var isIdle: Bool {
        self == .idle
    }

    public static func == (lhs: PlaybackState, rhs: PlaybackState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle), (.loading, .loading), (.buffering, .buffering),
             (.playing, .playing), (.paused, .paused), (.ended, .ended):
            return true
        case (.error(let lhsError), .error(let rhsError)):
            return lhsError == rhsError
        default:
            return false
        }
    }
}
