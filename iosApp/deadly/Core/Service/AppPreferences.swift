import Foundation

@Observable
final class AppPreferences {
    private static let showOnlyRecordedKey = "show_only_recorded_shows"
    private static let forceOnlineKey = "force_online"
    private static let libraryDisplayModeKey = "library_display_mode"

    var showOnlyRecordedShows: Bool {
        didSet { UserDefaults.standard.set(showOnlyRecordedShows, forKey: Self.showOnlyRecordedKey) }
    }

    var forceOnline: Bool {
        didSet { UserDefaults.standard.set(forceOnline, forKey: Self.forceOnlineKey) }
    }

    var libraryDisplayMode: String {
        didSet { UserDefaults.standard.set(libraryDisplayMode, forKey: Self.libraryDisplayModeKey) }
    }

    init() {
        UserDefaults.standard.register(defaults: [
            Self.showOnlyRecordedKey: true,
            Self.forceOnlineKey: false,
            Self.libraryDisplayModeKey: "LIST",
        ])
        showOnlyRecordedShows = UserDefaults.standard.bool(forKey: Self.showOnlyRecordedKey)
        forceOnline = UserDefaults.standard.bool(forKey: Self.forceOnlineKey)
        libraryDisplayMode = UserDefaults.standard.string(forKey: Self.libraryDisplayModeKey) ?? "LIST"
    }
}
