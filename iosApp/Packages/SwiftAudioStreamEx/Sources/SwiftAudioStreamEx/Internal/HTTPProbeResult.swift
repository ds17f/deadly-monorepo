import Foundation

/// Privacy-safe summary of an HTTP playback probe. Deliberately excludes the
/// URL path, query, client address, and response body from diagnostic logs.
struct HTTPProbeResult: Sendable, Equatable {
    let requestedHost: String?
    let finalHost: String?
    let statusCode: Int?
    let contentRange: String?
    let server: String?
    let retryAfter: String?
    let errorCode: String?

    var logDescription: String {
        [
            "requestedHost=\(requestedHost ?? "nil")",
            "finalHost=\(finalHost ?? "nil")",
            "status=\(statusCode.map(String.init) ?? "nil")",
            "contentRange=\(contentRange ?? "nil")",
            "server=\(server ?? "nil")",
            "retryAfter=\(retryAfter ?? "nil")",
            "error=\(errorCode ?? "nil")",
        ].joined(separator: " ")
    }
}
