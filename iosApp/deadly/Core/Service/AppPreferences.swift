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

    var eqBandGains: [Float] {
        didSet {
            let strings = eqBandGains.map { String($0) }
            UserDefaults.standard.set(strings.joined(separator: ","), forKey: Self.eqBandGainsKey)
        }
    }

    init() {
        UserDefaults.standard.register(defaults: [
            Self.includeShowsWithoutRecordingsKey: false,
            Self.forceOnlineKey: false,
            Self.favoritesDisplayModeKey: "LIST",
            Self.eqEnabledKey: false,
            Self.eqPresetKey: "flat",
            Self.shareAttachImageKey: false,
            Self.sourceBadgeStyleKey: "LONG",
        ])
        includeShowsWithoutRecordings = UserDefaults.standard.bool(forKey: Self.includeShowsWithoutRecordingsKey)
        forceOnline = UserDefaults.standard.bool(forKey: Self.forceOnlineKey)
        // Read new key first, fall back to legacy key for migration
        favoritesDisplayMode = UserDefaults.standard.string(forKey: Self.favoritesDisplayModeKey)
            ?? UserDefaults.standard.string(forKey: Self.legacyLibraryDisplayModeKey)
            ?? "LIST"
        shareAttachImage = UserDefaults.standard.bool(forKey: Self.shareAttachImageKey)
        sourceBadgeStyle = UserDefaults.standard.string(forKey: Self.sourceBadgeStyleKey) ?? "LONG"
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
