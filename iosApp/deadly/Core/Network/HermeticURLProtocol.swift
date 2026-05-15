import Foundation

/// URLSession-layer hook that rewrites outbound HTTP(S) requests when the
/// app is in hermetic mode. The original host is pushed into the path as
/// the first segment so a single WireMock instance can serve any number of
/// upstreams:
///
///     https://archive.org/metadata/foo  →  http://10.0.2.2:8090/archive.org/metadata/foo
///     https://api.github.com/repos/...  →  http://10.0.2.2:8090/api.github.com/repos/...
///
/// Register once at app launch:
///
///     URLProtocol.registerClass(HermeticURLProtocol.self)
///     HermeticURLProtocol.setBaseURLProvider { AppDelegate.shared.container.appPreferences.effectiveHermeticBaseURL }
///
/// Limitations
/// -----------
/// `URLProtocol` does not intercept:
///   - AVPlayer / CFNetwork-direct traffic (audio playback). Use
///     `AppPreferences.hermeticRewrite(_:)` at the URL construction site
///     instead.
///   - Background `URLSession` configurations.
///   - WebSocket connections.
///
/// Tracked in DEAD-350. See `hermetic/README.md`.
final class HermeticURLProtocol: URLProtocol {

    private static let handledKey = "HermeticURLProtocolHandled"
    private static let lock = NSLock()
    private static var baseURLProvider: () -> URL? = { nil }

    /// Wire this up at app launch with a closure that returns the
    /// currently-configured hermetic base URL (or `nil` when disabled).
    static func setBaseURLProvider(_ provider: @escaping () -> URL?) {
        lock.lock()
        defer { lock.unlock() }
        baseURLProvider = provider
    }

    private static var currentBaseURL: URL? {
        lock.lock()
        defer { lock.unlock() }
        return baseURLProvider()
    }

    // MARK: - URLProtocol

    override class func canInit(with request: URLRequest) -> Bool {
        // Avoid re-entering on our own forwarded request.
        if URLProtocol.property(forKey: handledKey, in: request) != nil { return false }
        guard let scheme = request.url?.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else { return false }
        return currentBaseURL != nil
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        return request
    }

    private var forwardedTask: URLSessionDataTask?

    override func startLoading() {
        guard let originalURL = request.url,
              let base = Self.currentBaseURL,
              let rewritten = Self.rewrite(originalURL, base: base)
        else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }

        guard let mutable = (request as NSURLRequest).mutableCopy() as? NSMutableURLRequest else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        URLProtocol.setProperty(true, forKey: Self.handledKey, in: mutable)
        mutable.url = rewritten

        // Forward via a session that does NOT include HermeticURLProtocol,
        // so we don't re-enter ourselves.
        let config = URLSessionConfiguration.default
        config.protocolClasses = (config.protocolClasses ?? []).filter { $0 != HermeticURLProtocol.self }
        let session = URLSession(configuration: config)

        forwardedTask = session.dataTask(with: mutable as URLRequest) { [weak self] data, response, error in
            guard let self = self else { return }
            if let error = error {
                self.client?.urlProtocol(self, didFailWithError: error)
                return
            }
            if let response = response {
                self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            }
            if let data = data {
                self.client?.urlProtocol(self, didLoad: data)
            }
            self.client?.urlProtocolDidFinishLoading(self)
        }
        forwardedTask?.resume()
    }

    override func stopLoading() {
        forwardedTask?.cancel()
        forwardedTask = nil
    }

    // MARK: - URL rewriting

    /// Rewrite `https://host/path?q=v` → `http://hermetic:port/host/path?q=v`.
    /// Visible internally so unit tests can exercise it without standing up URLSession.
    static func rewrite(_ url: URL, base: URL) -> URL? {
        guard let host = url.host,
              var components = URLComponents(url: base, resolvingAgainstBaseURL: false)
        else { return nil }

        let originalPath = url.path
        var newPath = components.path
        if !newPath.hasSuffix("/") { newPath += "/" }
        newPath += host
        if !originalPath.isEmpty && !originalPath.hasPrefix("/") { newPath += "/" }
        newPath += originalPath

        components.path = newPath
        components.query = url.query
        components.fragment = url.fragment
        return components.url
    }
}
