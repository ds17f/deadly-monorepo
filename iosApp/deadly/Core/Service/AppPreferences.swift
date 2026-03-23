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
