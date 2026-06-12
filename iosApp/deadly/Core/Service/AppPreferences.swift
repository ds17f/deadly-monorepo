import Foundation

@Observable
final class AppPreferences {
    private static let includeShowsWithoutRecordingsKey = "include_shows_without_recordings"
    private static let forceOnlineKey = "force_online"
    private static let localBackfilledV1Key = "local_backfilled_v1"
    private static let favoritesDisplayModeKey = "favorites_display_mode"
    private static let legacyLibraryDisplayModeKey = "library_display_mode"
    private static let eqEnabledKey = "eq_enabled"
    private static let eqPresetKey = "eq_preset"
    private static let eqBandGainsKey = "eq_band_gains"
    private static let shareAttachImageKey = "share_attach_image"
    private static let autoAdvanceEnabledKey = "auto_advance_enabled"
    private static let sourceBadgeStyleKey = "source_badge_style"
    private static let useBetaShareLinksKey = "use_beta_share_links"
    private static let useBetaModeKey = "use_beta_mode"
    private static let serverEnvironmentKey = "server_environment"
    private static let customServerUrlKey = "custom_server_url"
    private static let customDevEmailKey = "custom_dev_email"
    private static let analyticsEnabledKey = "analytics_enabled"
    private static let installIdKey = "install_id"
    private static let playerControlsStyleKey = "player_controls_style"
    private static let homeTrendingWindowKey = "home_trending_window"
    private static let homeTrendingAboveTodayKey = "home_trending_above_today"
    private static let homeRecentRowsKey = "home_recent_rows"
    private static let homeTrendingCardSizeKey = "home_trending_card_size"
    private static let homeTrendingIncludeAnniversariesKey = "home_trending_include_anniversaries"
    private static let homePopularEnabledKey = "home_popular_enabled"
    private static let homePopularCardSizeKey = "home_popular_card_size"
    private static let homePopularDecadeKey = "home_popular_decade"
    private static let homeTodayCardSizeKey = "home_today_card_size"
    private static let homeCollectionsCardSizeKey = "home_collections_card_size"

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

    /// Set once local data (favorites + top recents) has been pushed to the
    /// server, so the one-time startup backfill doesn't re-run.
    var localBackfilledV1: Bool {
        didSet { UserDefaults.standard.set(localBackfilledV1, forKey: Self.localBackfilledV1Key) }
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

    /// ADR-0010: roll into the next show when one ends ("Autoplay"). Off by default.
    var autoAdvanceEnabled: Bool {
        didSet { UserDefaults.standard.set(autoAdvanceEnabled, forKey: Self.autoAdvanceEnabledKey) }
    }

    var analyticsEnabled: Bool {
        didSet { UserDefaults.standard.set(analyticsEnabled, forKey: Self.analyticsEnabledKey) }
    }

    /// Which transport controls appear on lock screen / CarPlay: "skipTrack", "skipSeconds", or "both".
    var playerControlsStyle: String {
        didSet { UserDefaults.standard.set(playerControlsStyle, forKey: Self.playerControlsStyleKey) }
    }

    /// Which trending window the home screen shows: "now"/"week"/"month"/"all".
    var homeTrendingWindow: String {
        didSet { UserDefaults.standard.set(homeTrendingWindow, forKey: Self.homeTrendingWindowKey) }
    }

    /// When true, Trending renders above Today in History; otherwise below.
    var homeTrendingAboveToday: Bool {
        didSet { UserDefaults.standard.set(homeTrendingAboveToday, forKey: Self.homeTrendingAboveTodayKey) }
    }

    /// Rows of Recently Played to render on home (1..4). Each row = 2 shows.
    var homeRecentRows: Int {
        didSet {
            let clamped = max(1, min(4, homeRecentRows))
            if clamped != homeRecentRows { homeRecentRows = clamped; return }
            UserDefaults.standard.set(homeRecentRows, forKey: Self.homeRecentRowsKey)
        }
    }

    /// Card size for the Trending carousel: "small" or "large".
    var homeTrendingCardSize: String {
        didSet { UserDefaults.standard.set(homeTrendingCardSize, forKey: Self.homeTrendingCardSizeKey) }
    }

    /// When true, "Today in Grateful Dead History" shows are included in the
    /// `now` trending window. Off by default so the 24h ranking surfaces
    /// organic momentum instead of echoing the OTD home rail.
    var homeTrendingIncludeAnniversaries: Bool {
        didSet { UserDefaults.standard.set(homeTrendingIncludeAnniversaries, forKey: Self.homeTrendingIncludeAnniversariesKey) }
    }

    /// Show the "Fan Favorites" home rail. On by default.
    var homePopularEnabled: Bool {
        didSet { UserDefaults.standard.set(homePopularEnabled, forKey: Self.homePopularEnabledKey) }
    }

    /// Card size for the Fan Favorites carousel: "small" or "large".
    var homePopularCardSize: String {
        didSet { UserDefaults.standard.set(homePopularCardSize, forKey: Self.homePopularCardSizeKey) }
    }

    /// Which decade the Fan Favorites home rail shows: "all"/"60s"/"70s"/"80s"/"90s".
    var homePopularDecade: String {
        didSet { UserDefaults.standard.set(homePopularDecade, forKey: Self.homePopularDecadeKey) }
    }

    /// Card size for the Today in History carousel: "small" or "large".
    var homeTodayCardSize: String {
        didSet { UserDefaults.standard.set(homeTodayCardSize, forKey: Self.homeTodayCardSizeKey) }
    }

    /// Card size for the Featured Collections carousel: "small" or "large".
    var homeCollectionsCardSize: String {
        didSet { UserDefaults.standard.set(homeCollectionsCardSize, forKey: Self.homeCollectionsCardSizeKey) }
    }

    /// Restore all Home Screen preferences to defaults.
    func resetHomePreferences() {
        homeTrendingWindow = "now"
        homeTrendingAboveToday = false
        homeRecentRows = 2
        homeTrendingCardSize = "small"
        homeTodayCardSize = "large"
        homeCollectionsCardSize = "large"
        homeTrendingIncludeAnniversaries = false
        homePopularEnabled = true
        homePopularCardSize = "small"
        homePopularDecade = "all"
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
            Self.localBackfilledV1Key: false,
            Self.favoritesDisplayModeKey: "LIST",
            Self.eqEnabledKey: false,
            Self.eqPresetKey: "flat",
            Self.shareAttachImageKey: false,
            Self.autoAdvanceEnabledKey: false,
            Self.sourceBadgeStyleKey: "LONG",
            Self.playerControlsStyleKey: "skipTrack",
            Self.homeTrendingWindowKey: "now",
            Self.homeTrendingAboveTodayKey: false,
            Self.homeRecentRowsKey: 2,
            Self.homeTrendingCardSizeKey: "small",
            Self.homeTodayCardSizeKey: "large",
            Self.homeCollectionsCardSizeKey: "large",
            Self.homeTrendingIncludeAnniversariesKey: false,
            Self.homePopularEnabledKey: true,
            Self.homePopularCardSizeKey: "small",
            Self.homePopularDecadeKey: "all",
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
        localBackfilledV1 = UserDefaults.standard.bool(forKey: Self.localBackfilledV1Key)
        // Read new key first, fall back to legacy key for migration
        favoritesDisplayMode = UserDefaults.standard.string(forKey: Self.favoritesDisplayModeKey)
            ?? UserDefaults.standard.string(forKey: Self.legacyLibraryDisplayModeKey)
            ?? "LIST"
        shareAttachImage = UserDefaults.standard.bool(forKey: Self.shareAttachImageKey)
        autoAdvanceEnabled = UserDefaults.standard.bool(forKey: Self.autoAdvanceEnabledKey)
        sourceBadgeStyle = UserDefaults.standard.string(forKey: Self.sourceBadgeStyleKey) ?? "LONG"
        playerControlsStyle = UserDefaults.standard.string(forKey: Self.playerControlsStyleKey) ?? "skipTrack"
        homeTrendingWindow = UserDefaults.standard.string(forKey: Self.homeTrendingWindowKey) ?? "now"
        homeTrendingAboveToday = UserDefaults.standard.bool(forKey: Self.homeTrendingAboveTodayKey)
        homeRecentRows = max(1, min(4, UserDefaults.standard.integer(forKey: Self.homeRecentRowsKey)))
        homeTrendingCardSize = UserDefaults.standard.string(forKey: Self.homeTrendingCardSizeKey) ?? "small"
        homeTodayCardSize = UserDefaults.standard.string(forKey: Self.homeTodayCardSizeKey) ?? "large"
        homeCollectionsCardSize = UserDefaults.standard.string(forKey: Self.homeCollectionsCardSizeKey) ?? "large"
        homeTrendingIncludeAnniversaries = UserDefaults.standard.bool(forKey: Self.homeTrendingIncludeAnniversariesKey)
        homePopularEnabled = UserDefaults.standard.object(forKey: Self.homePopularEnabledKey) == nil
            ? true
            : UserDefaults.standard.bool(forKey: Self.homePopularEnabledKey)
        homePopularCardSize = UserDefaults.standard.string(forKey: Self.homePopularCardSizeKey) ?? "small"
        homePopularDecade = UserDefaults.standard.string(forKey: Self.homePopularDecadeKey) ?? "all"
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
