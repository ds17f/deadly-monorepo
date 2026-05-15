import Foundation

@Observable
final class AppPreferences {
    private static let includeShowsWithoutRecordingsKey = "include_shows_without_recordings"
    private static let forceOnlineKey = "force_online"
    private static let favoritesDisplayModeKey = "favorites_display_mode"
    private static let legacyLibraryDisplayModeKey = "library_display_mode"
    private static let eqEnabledKey = "eq_enabled"
    private static let eqPresetKey = "eq_preset"
    private static let eqBandGainsKey = "eq_band_gains"
    private static let shareAttachImageKey = "share_attach_image"
    private static let sourceBadgeStyleKey = "source_badge_style"
    private static let useBetaShareLinksKey = "use_beta_share_links"
    private static let useBetaModeKey = "use_beta_mode"
    private static let serverEnvironmentKey = "server_environment"
    private static let customServerUrlKey = "custom_server_url"
    private static let customDevEmailKey = "custom_dev_email"
    private static let analyticsEnabledKey = "analytics_enabled"
    private static let installIdKey = "install_id"
    private static let showTicketImagesKey = "show_ticket_images"
    private static let hermeticModeEnabledKey = "hermetic_mode_enabled"
    private static let hermeticBaseURLKey = "hermetic_base_url"

    /// Server environment: "prod", "beta", or "custom".
    var serverEnvironment: String {
        didSet {
            UserDefaults.standard.set(serverEnvironment, forKey: Self.serverEnvironmentKey)
            // Keep legacy keys in sync
            let isBeta = serverEnvironment == "beta"
            UserDefaults.standard.set(isBeta, forKey: Self.useBetaModeKey)
            UserDefaults.standard.set(isBeta, forKey: Self.useBetaShareLinksKey)
        }
    }

    /// Custom server URL for local dev testing (e.g. "http://192.168.1.100:3000").
    var customServerUrl: String {
        didSet { UserDefaults.standard.set(customServerUrl, forKey: Self.customServerUrlKey) }
    }

    /// Email for dev token endpoint on custom server.
    var customDevEmail: String {
        didSet { UserDefaults.standard.set(customDevEmail, forKey: Self.customDevEmailKey) }
    }

    /// Backward-compatible computed property for auth key namespacing.
    var useBetaMode: Bool { serverEnvironment == "beta" }

    var shareBaseUrl: String {
        switch serverEnvironment {
        case "beta": return "https://share.beta.thedeadly.app"
        case "custom": return customServerUrl
        default: return "https://share.thedeadly.app"
        }
    }

    var apiBaseUrl: String {
        switch serverEnvironment {
        case "beta": return "https://beta.thedeadly.app"
        case "custom": return customServerUrl
        default: return "https://thedeadly.app"
        }
    }

    var includeShowsWithoutRecordings: Bool {
        didSet { UserDefaults.standard.set(includeShowsWithoutRecordings, forKey: Self.includeShowsWithoutRecordingsKey) }
    }

    var forceOnline: Bool {
        didSet { UserDefaults.standard.set(forceOnline, forKey: Self.forceOnlineKey) }
    }

    var favoritesDisplayMode: String {
        didSet { UserDefaults.standard.set(favoritesDisplayMode, forKey: Self.favoritesDisplayModeKey) }
    }

    var eqEnabled: Bool {
        didSet { UserDefaults.standard.set(eqEnabled, forKey: Self.eqEnabledKey) }
    }

    var eqPreset: String {
        didSet { UserDefaults.standard.set(eqPreset, forKey: Self.eqPresetKey) }
    }

    var shareAttachImage: Bool {
        didSet { UserDefaults.standard.set(shareAttachImage, forKey: Self.shareAttachImageKey) }
    }

    var sourceBadgeStyle: String {
        didSet { UserDefaults.standard.set(sourceBadgeStyle, forKey: Self.sourceBadgeStyleKey) }
    }

    var analyticsEnabled: Bool {
        didSet { UserDefaults.standard.set(analyticsEnabled, forKey: Self.analyticsEnabledKey) }
    }

    var showTicketImages: Bool {
        didSet { UserDefaults.standard.set(showTicketImages, forKey: Self.showTicketImagesKey) }
    }

    // MARK: - Hermetic test server
    //
    // When `hermeticModeEnabled` is on and `hermeticBaseURL` is non-empty,
    // outbound traffic is rerouted to that URL, with the original host
    // pushed in as the first path segment. URLSession-based traffic is
    // intercepted transparently by `HermeticURLProtocol`; audio playback
    // (AVPlayer) bypasses URLProtocol and so audio URL construction must
    // call `hermeticRewrite(_:)` explicitly. See DEAD-350 / hermetic/README.md.

    var hermeticModeEnabled: Bool {
        didSet { UserDefaults.standard.set(hermeticModeEnabled, forKey: Self.hermeticModeEnabledKey) }
    }

    var hermeticBaseURL: String {
        didSet { UserDefaults.standard.set(hermeticBaseURL, forKey: Self.hermeticBaseURLKey) }
    }

    /// Non-nil only when hermetic mode is on AND a valid URL is configured.
    /// Read by `HermeticURLProtocol` on every request.
    var effectiveHermeticBaseURL: URL? {
        guard hermeticModeEnabled, !hermeticBaseURL.isEmpty else { return nil }
        return URL(string: hermeticBaseURL)
    }

    /// Rewrites `url` to route through the hermetic server if hermetic mode is on,
    /// pushing the original host as the first path segment:
    ///
    ///     https://archive.org/download/foo  →  http://10.0.2.2:8090/archive.org/download/foo
    ///
    /// Returns the original URL unmodified when hermetic mode is off.
    /// Use this at sites that build URLs for AVPlayer / non-URLSession consumers,
    /// since URLProtocol does not intercept CFNetwork-direct traffic.
    func hermeticRewrite(_ url: URL) -> URL {
        guard let base = effectiveHermeticBaseURL,
              let host = url.host,
              var components = URLComponents(url: base, resolvingAgainstBaseURL: false)
        else { return url }

        // Idempotence guard: don't re-rewrite a URL that's already targeting
        // the hermetic host. Without this, URLs that were rewritten and then
        // persisted (e.g. PlaylistServiceImpl saving the AVPlayer URL to
        // restore last-played state) would gain another `/<host>/` prefix on
        // every restart.
        if host == base.host && url.port == base.port {
            return url
        }

        let originalPath = url.path
        var newPath = components.path
        if !newPath.hasSuffix("/") { newPath += "/" }
        newPath += host
        if !originalPath.isEmpty && !originalPath.hasPrefix("/") { newPath += "/" }
        newPath += originalPath
        components.path = newPath
        components.query = url.query
        components.fragment = url.fragment
        return components.url ?? url
    }

    /// Persistent install ID (UUID). Generated once on first access, survives opt-out/opt-in cycles.
    let installId: String

    var eqBandGains: [Float] {
        didSet {
            let strings = eqBandGains.map { String($0) }
            UserDefaults.standard.set(strings.joined(separator: ","), forKey: Self.eqBandGainsKey)
        }
    }

    init() {
        UserDefaults.standard.register(defaults: [
            Self.includeShowsWithoutRecordingsKey: false,
            Self.useBetaModeKey: false,
            Self.forceOnlineKey: false,
            Self.favoritesDisplayModeKey: "LIST",
            Self.eqEnabledKey: false,
            Self.eqPresetKey: "flat",
            Self.shareAttachImageKey: false,
            Self.sourceBadgeStyleKey: "LONG",
            Self.showTicketImagesKey: false,
            Self.hermeticModeEnabledKey: false,
        ])
        includeShowsWithoutRecordings = UserDefaults.standard.bool(forKey: Self.includeShowsWithoutRecordingsKey)
        customServerUrl = UserDefaults.standard.string(forKey: Self.customServerUrlKey) ?? ""
        customDevEmail = UserDefaults.standard.string(forKey: Self.customDevEmailKey) ?? ""
        // Migrate: read new key first, fall back to legacy beta mode keys
        if let env = UserDefaults.standard.string(forKey: Self.serverEnvironmentKey) {
            serverEnvironment = env
        } else if UserDefaults.standard.object(forKey: Self.useBetaModeKey) != nil {
            serverEnvironment = UserDefaults.standard.bool(forKey: Self.useBetaModeKey) ? "beta" : "prod"
        } else {
            serverEnvironment = UserDefaults.standard.bool(forKey: Self.useBetaShareLinksKey) ? "beta" : "prod"
        }
        forceOnline = UserDefaults.standard.bool(forKey: Self.forceOnlineKey)
        // Read new key first, fall back to legacy key for migration
        favoritesDisplayMode = UserDefaults.standard.string(forKey: Self.favoritesDisplayModeKey)
            ?? UserDefaults.standard.string(forKey: Self.legacyLibraryDisplayModeKey)
            ?? "LIST"
        shareAttachImage = UserDefaults.standard.bool(forKey: Self.shareAttachImageKey)
        sourceBadgeStyle = UserDefaults.standard.string(forKey: Self.sourceBadgeStyleKey) ?? "LONG"
        showTicketImages = UserDefaults.standard.bool(forKey: Self.showTicketImagesKey)
        hermeticModeEnabled = UserDefaults.standard.bool(forKey: Self.hermeticModeEnabledKey)
        hermeticBaseURL = UserDefaults.standard.string(forKey: Self.hermeticBaseURLKey) ?? ""
        analyticsEnabled = UserDefaults.standard.object(forKey: Self.analyticsEnabledKey) == nil
            ? true
            : UserDefaults.standard.bool(forKey: Self.analyticsEnabledKey)
        if let existing = UserDefaults.standard.string(forKey: Self.installIdKey), !existing.isEmpty {
            installId = existing
        } else {
            let newId = UUID().uuidString
            UserDefaults.standard.set(newId, forKey: Self.installIdKey)
            installId = newId
        }
        eqEnabled = UserDefaults.standard.bool(forKey: Self.eqEnabledKey)
        eqPreset = UserDefaults.standard.string(forKey: Self.eqPresetKey) ?? "flat"

        if let savedGains = UserDefaults.standard.string(forKey: Self.eqBandGainsKey) {
            eqBandGains = savedGains.split(separator: ",").compactMap { Float($0) }
            if eqBandGains.count != EQDefaults.frequencies.count {
                eqBandGains = Array(repeating: Float(0), count: EQDefaults.frequencies.count)
            }
        } else {
            eqBandGains = Array(repeating: Float(0), count: EQDefaults.frequencies.count)
        }
    }
}
