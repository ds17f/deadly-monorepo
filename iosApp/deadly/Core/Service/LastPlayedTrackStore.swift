import Foundation

struct LastPlayedTrack: Codable, Sendable, Equatable {
    let showId: String
    let recordingId: String
    let trackIndex: Int
    let positionMs: Int64
    let trackTitle: String
    let showDate: String
    let venue: String?
    let location: String?
}

final class LastPlayedTrackStore: Sendable {
    private static let key = "last_played_track"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func save(_ track: LastPlayedTrack) {
        guard let data = try? JSONEncoder().encode(track) else { return }
        defaults.set(data, forKey: Self.key)
    }

    func load() -> LastPlayedTrack? {
        guard let data = defaults.data(forKey: Self.key) else { return nil }
        return try? JSONDecoder().decode(LastPlayedTrack.self, from: data)
    }

    func clear() {
        defaults.removeObject(forKey: Self.key)
    }
}
