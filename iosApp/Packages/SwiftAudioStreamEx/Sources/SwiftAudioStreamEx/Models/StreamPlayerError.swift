import Foundation

/// Errors that can occur during playback.
public enum StreamPlayerError: Error, Sendable, Equatable {
    case trackLoadFailed(url: URL, reason: String)
    case networkError(String)
    case audioSessionError(String)
    case invalidQueueIndex(Int)
    case engineError(String)
    case unknown(String)

    public var localizedDescription: String {
        switch self {
        case .trackLoadFailed(let url, let reason):
            return "Failed to load track \(url.lastPathComponent): \(reason)"
        case .networkError(let reason):
            return "Network error: \(reason)"
        case .audioSessionError(let reason):
            return "Audio session error: \(reason)"
        case .invalidQueueIndex(let index):
            return "Invalid queue index: \(index)"
        case .engineError(let reason):
            return "Engine error: \(reason)"
        case .unknown(let reason):
            return "Unknown error: \(reason)"
        }
    }
}
