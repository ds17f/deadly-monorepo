import Foundation

@Observable
final class AppPreferences {
    private static let showOnlyRecordedKey = "show_only_recorded_shows"
    private static let forceOnlineKey = "force_online"

    var showOnlyRecordedShows: Bool {
        didSet { UserDefaults.standard.set(showOnlyRecordedShows, forKey: Self.showOnlyRecordedKey) }
    }

    var forceOnline: Bool {
        didSet { UserDefaults.standard.set(forceOnline, forKey: Self.forceOnlineKey) }
    }

    init() {
        UserDefaults.standard.register(defaults: [
            Self.showOnlyRecordedKey: true,
            Self.forceOnlineKey: false,
        ])
        showOnlyRecordedShows = UserDefaults.standard.bool(forKey: Self.showOnlyRecordedKey)
        forceOnline = UserDefaults.standard.bool(forKey: Self.forceOnlineKey)
    }
}
