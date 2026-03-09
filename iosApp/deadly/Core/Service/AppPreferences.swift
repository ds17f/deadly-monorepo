import Foundation

@Observable
final class AppPreferences {
    private static let includeShowsWithoutRecordingsKey = "include_shows_without_recordings"
    private static let forceOnlineKey = "force_online"
    private static let favoritesDisplayModeKey = "favorites_display_mode"
    private static let legacyLibraryDisplayModeKey = "library_display_mode"

    var includeShowsWithoutRecordings: Bool {
        didSet { UserDefaults.standard.set(includeShowsWithoutRecordings, forKey: Self.includeShowsWithoutRecordingsKey) }
    }

    var forceOnline: Bool {
        didSet { UserDefaults.standard.set(forceOnline, forKey: Self.forceOnlineKey) }
    }

    var favoritesDisplayMode: String {
        didSet { UserDefaults.standard.set(favoritesDisplayMode, forKey: Self.favoritesDisplayModeKey) }
    }

    init() {
        UserDefaults.standard.register(defaults: [
            Self.includeShowsWithoutRecordingsKey: false,
            Self.forceOnlineKey: false,
            Self.favoritesDisplayModeKey: "LIST",
        ])
        includeShowsWithoutRecordings = UserDefaults.standard.bool(forKey: Self.includeShowsWithoutRecordingsKey)
        forceOnline = UserDefaults.standard.bool(forKey: Self.forceOnlineKey)
        // Read new key first, fall back to legacy key for migration
        favoritesDisplayMode = UserDefaults.standard.string(forKey: Self.favoritesDisplayModeKey)
            ?? UserDefaults.standard.string(forKey: Self.legacyLibraryDisplayModeKey)
            ?? "LIST"
    }
}
