import Foundation

@Observable
final class AppPreferences {
    private static let showOnlyRecordedKey = "show_only_recorded_shows"

    var showOnlyRecordedShows: Bool {
        didSet { UserDefaults.standard.set(showOnlyRecordedShows, forKey: Self.showOnlyRecordedKey) }
    }

    init() {
        UserDefaults.standard.register(defaults: [Self.showOnlyRecordedKey: true])
        showOnlyRecordedShows = UserDefaults.standard.bool(forKey: Self.showOnlyRecordedKey)
    }
}
