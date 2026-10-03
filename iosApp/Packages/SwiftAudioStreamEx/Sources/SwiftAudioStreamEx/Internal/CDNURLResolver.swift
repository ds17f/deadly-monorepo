import Foundation

/// Status and final URL of one ranged request, after redirects. The response
/// body is never read.
struct CDNHeaderResponse: Sendable, Equatable {
    let finalURL: URL
    let statusCode: Int
}

/// Fetches only the response headers for `url`. Injected so tests can run the
/// resolver without a network.
typealias CDNHeaderFetcher = @Sendable (_ url: URL, _ timeout: TimeInterval) async throws -> CDNHeaderResponse

/// Privacy-safe record of one validation request. No path, query, or body.
struct CDNResolveAttempt: Sendable, Equatable {
    /// Position of the candidate in the ordered candidate list.
    let index: Int
    let requestedHost: String?
    let finalHost: String?
    let statusCode: Int?
    /// `domain:code` of the transport error, when there was no response.
    let errorCode: String?
    let durationMs: Int

    var isSuccess: Bool {
        statusCode == 200 || statusCode == 206
    }

    /// Host to remember as unhealthy: where the request ended, else where it started.
    var failedHost: String? {
        isSuccess ? nil : (finalHost ?? requestedHost)
    }

    var logDescription: String {
        [
            "cand=\(index)",
            "requestedHost=\(requestedHost ?? "nil")",
            "finalHost=\(finalHost ?? "nil")",
            "status=\(statusCode.map(String.init) ?? "nil")",
            "error=\(errorCode ?? "nil")",
            "ms=\(durationMs)",
        ].joined(separator: " ")
    }
}

struct CDNResolution: Sendable, Equatable {
    /// Final URL of the first candidate that passed validation.
    let resolvedURL: URL?
    let attempts: [CDNResolveAttempt]
}

/// Finds a healthy playback URL for one track. A candidate is healthy only if a
/// ranged GET (`bytes=0-1`) that follows redirects returns HTTP 200 or 206.
struct CDNURLResolver: Sendable {
    let requestTimeout: TimeInterval
    let fetch: CDNHeaderFetcher
#if DEBUG
    /// Debug hook: when it returns true for a candidate's final host, that
    /// candidate is treated as failed whatever the response said.
    var rejectFinalHost: (@Sendable (String) -> Bool)?
#endif

    init(requestTimeout: TimeInterval = 5, fetch: @escaping CDNHeaderFetcher) {
        self.requestTimeout = requestTimeout
        self.fetch = fetch
    }

    /// Candidate order for one track: canonical first, then the fallbacks. A
    /// candidate whose host is in `failedHosts` moves behind all the others.
    /// Order is otherwise unchanged, and duplicates are removed.
    static func orderedCandidates(canonical: URL, fallbacks: [URL], failedHosts: Set<String>) -> [URL] {
        var seen = Set<URL>()
        let unique = ([canonical] + fallbacks).filter { seen.insert($0).inserted }
        func isFailed(_ url: URL) -> Bool {
            url.host.map { failedHosts.contains($0) } ?? false
        }
        return unique.filter { !isFailed($0) } + unique.filter(isFailed)
    }

    /// Try each candidate in order and return the first that validates. Stops
    /// early when `deadline` has passed.
    func resolve(candidates: [URL], deadline: Date? = nil) async -> CDNResolution {
        var attempts: [CDNResolveAttempt] = []
        for (index, url) in candidates.enumerated() {
            if Task.isCancelled { break }
            var timeout = requestTimeout
            if let deadline {
                let remaining = deadline.timeIntervalSinceNow
                if remaining <= 0 { break }
                timeout = min(timeout, max(remaining, 1))
            }

            let started = Date.now
            do {
                let response = try await fetch(url, timeout)
                let attempt = CDNResolveAttempt(
                    index: index,
                    requestedHost: url.host,
                    finalHost: response.finalURL.host,
                    statusCode: response.statusCode,
                    errorCode: nil,
                    durationMs: Self.elapsedMs(since: started)
                )
#if DEBUG
                if let host = response.finalURL.host, rejectFinalHost?(host) == true {
                    attempts.append(CDNResolveAttempt(
                        index: index,
                        requestedHost: url.host,
                        finalHost: host,
                        statusCode: nil,
                        errorCode: "debug:forcedFallback",
                        durationMs: Self.elapsedMs(since: started)
                    ))
                    continue
                }
#endif
                attempts.append(attempt)
                if attempt.isSuccess {
                    return CDNResolution(resolvedURL: response.finalURL, attempts: attempts)
                }
            } catch {
                let nsError = error as NSError
                attempts.append(CDNResolveAttempt(
                    index: index,
                    requestedHost: url.host,
                    finalHost: nil,
                    statusCode: nil,
                    errorCode: "\(nsError.domain):\(nsError.code)",
                    durationMs: Self.elapsedMs(since: started)
                ))
            }
        }
        return CDNResolution(resolvedURL: nil, attempts: attempts)
    }

    private static func elapsedMs(since start: Date) -> Int {
        Int(Date.now.timeIntervalSince(start) * 1000)
    }

    /// Production resolver. Uses an ephemeral, non-caching session and returns as
    /// soon as the response headers arrive, so a server that ignores `Range`
    /// cannot make the app download the whole track.
    static func live(requestTimeout: TimeInterval = 5) -> CDNURLResolver {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        let session = URLSession(configuration: configuration)
        return CDNURLResolver(requestTimeout: requestTimeout) { url, timeout in
            try await Self.fetchHeaders(session: session, url: url, timeout: timeout)
        }
    }

    /// Ranged GET that returns at the response headers.
    static func fetchHeaders(session: URLSession, url: URL, timeout: TimeInterval) async throws -> CDNHeaderResponse {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = timeout
        request.setValue("bytes=0-1", forHTTPHeaderField: "Range")
        request.setValue("*/*", forHTTPHeaderField: "Accept")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        // `bytes(for:)` returns when the headers arrive. Cancel the task and do
        // not iterate the body.
        let (bytes, response) = try await session.bytes(for: request)
        bytes.task.cancel()
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        return CDNHeaderResponse(finalURL: http.url ?? url, statusCode: http.statusCode)
    }
}

/// Pure queue arithmetic for the foreground CDN recovery window.
enum CDNRecoveryPlanner {
    /// Index of the track after `currentIndex` that must be validated before
    /// playback restarts. Nil for the last track, or when the next track is a
    /// local file (it needs no validation).
    static func nextIndexToValidate(resolved: [URL], currentIndex: Int) -> Int? {
        let next = currentIndex + 1
        guard next < resolved.count, !resolved[next].isFileURL else { return nil }
        return next
    }

    /// Replace the current (and validated next) mapping. Tracks after the next
    /// one keep their existing URL. `pending` is every URL after the current
    /// track, in order, for AudioStreaming's forward queue.
    static func install(
        resolved: [URL],
        currentIndex: Int,
        currentURL: URL,
        nextIndex: Int?,
        nextURL: URL?
    ) -> (resolved: [URL], pending: [URL]) {
        var updated = resolved
        if updated.indices.contains(currentIndex) {
            updated[currentIndex] = currentURL
        }
        if let nextIndex, let nextURL, updated.indices.contains(nextIndex) {
            updated[nextIndex] = nextURL
        }
        let pending = currentIndex + 1 < updated.count ? Array(updated[(currentIndex + 1)...]) : []
        return (updated, pending)
    }
}
