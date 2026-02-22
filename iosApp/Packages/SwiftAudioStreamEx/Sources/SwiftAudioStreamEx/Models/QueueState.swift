import Foundation

/// Current state of the playback queue.
public struct QueueState: Sendable, Equatable {
    public let currentIndex: Int
    public let totalTracks: Int

    public init(currentIndex: Int = 0, totalTracks: Int = 0) {
        self.currentIndex = currentIndex
        self.totalTracks = totalTracks
    }

    public var hasNext: Bool {
        totalTracks > 0 && currentIndex < totalTracks - 1
    }

    public var hasPrevious: Bool {
        currentIndex > 0
    }

    public var isFirstTrack: Bool {
        currentIndex == 0
    }

    public var isLastTrack: Bool {
        totalTracks > 0 && currentIndex == totalTracks - 1
    }

    public var isEmpty: Bool {
        totalTracks == 0
    }

    public static let empty = QueueState()
}
