import Foundation

@Observable
final class AppPreferences {
    private static let showOnlyRecordedKey = "show_only_recorded_shows"
    private static let forceOnlineKey = "force_online"
    private static let favoritesDisplayModeKey = "favorites_display_mode"
    private static let legacyLibraryDisplayModeKey = "library_display_mode"

    var showOnlyRecordedShows: Bool {
        didSet { UserDefaults.standard.set(showOnlyRecordedShows, forKey: Self.showOnlyRecordedKey) }
    }

    var forceOnline: Bool {
        didSet { UserDefaults.standard.set(forceOnline, forKey: Self.forceOnlineKey) }
    }

    var favoritesDisplayMode: String {
        didSet { UserDefaults.standard.set(favoritesDisplayMode, forKey: Self.favoritesDisplayModeKey) }
    }

    init() {
        UserDefaults.standard.register(defaults: [
            Self.showOnlyRecordedKey: true,
            Self.forceOnlineKey: false,
            Self.favoritesDisplayModeKey: "LIST",
        ])
        showOnlyRecordedShows = UserDefaults.standard.bool(forKey: Self.showOnlyRecordedKey)
        forceOnline = UserDefaults.standard.bool(forKey: Self.forceOnlineKey)
        // Read new key first, fall back to legacy key for migration
        favoritesDisplayMode = UserDefaults.standard.string(forKey: Self.favoritesDisplayModeKey)
            ?? UserDefaults.standard.string(forKey: Self.legacyLibraryDisplayModeKey)
            ?? "LIST"
    }
}
