import Foundation

/// Current playback progress information.
public struct PlaybackProgress: Sendable, Equatable {
    public let currentTime: TimeInterval
    public let duration: TimeInterval

    public init(currentTime: TimeInterval = 0, duration: TimeInterval = 0) {
        self.currentTime = currentTime
        self.duration = duration
    }

    /// Progress fraction from 0.0 to 1.0. Returns 0 if duration is zero or invalid.
    public var progress: Double {
        guard duration > 0 else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    /// Remaining time in seconds.
    public var remaining: TimeInterval {
        max(duration - currentTime, 0)
    }

    public static let zero = PlaybackProgress()
}
