import Foundation

enum PlaybackNetworkFailureKind: String, Sendable {
    case connectivity
    case cdnServer = "cdn"
}

enum NetworkFailureSource: String, Sendable {
    case player
    case watchdog
    case developer
    case manualRetry
}

/// Keeps the AudioStreaming 1.4.4 description-based adapter in one place.
/// Its error cases are not exposed through a stable public discriminator.
enum PlaybackNetworkFailureClassifier {
    static func classify(errorDescription: String) -> PlaybackNetworkFailureKind? {
        let description = errorDescription.lowercased()
        if description.contains("servererror") {
            return .cdnServer
        }
        if description.contains("networkerror") {
            return .connectivity
        }
        return nil
    }
}
