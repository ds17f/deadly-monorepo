import Foundation

// MARK: - Connection State

enum ConnectConnectionState {
    case disconnected
    case connecting
    case connected
    case reconnecting
}

// MARK: - Device

struct ConnectDevice: Codable, Identifiable {
    let deviceId: String
    let type: String
    let name: String
    let capabilities: [String]

    var id: String { deviceId }
}

// MARK: - Outgoing Messages

struct RegisterMessage: Encodable {
    let type = "register"
    let device: RegisterDevice
}

struct RegisterDevice: Encodable {
    let deviceId: String
    let type: String
    let name: String
    let capabilities: [String]
}

// MARK: - User Playback State

struct UserPlaybackState: Codable {
    let showId: String?
    let recordingId: String?
    let trackIndex: Int
    let positionMs: Int
    let durationMs: Int
    let trackTitle: String?
    let date: String?
    let venue: String?
    let location: String?
    let activeDeviceId: String?
    let activeDeviceName: String?
    let activeDeviceType: String?
    let isPlaying: Bool
    let updatedAt: Int

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        showId = try c.decodeIfPresent(String.self, forKey: .showId)
        recordingId = try c.decodeIfPresent(String.self, forKey: .recordingId)
        trackIndex = try c.decodeIfPresent(Int.self, forKey: .trackIndex) ?? 0
        positionMs = try c.decodeIfPresent(Int.self, forKey: .positionMs) ?? 0
        durationMs = try c.decodeIfPresent(Int.self, forKey: .durationMs) ?? 0
        trackTitle = try c.decodeIfPresent(String.self, forKey: .trackTitle)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        venue = try c.decodeIfPresent(String.self, forKey: .venue)
        location = try c.decodeIfPresent(String.self, forKey: .location)
        activeDeviceId = try c.decodeIfPresent(String.self, forKey: .activeDeviceId)
        activeDeviceName = try c.decodeIfPresent(String.self, forKey: .activeDeviceName)
        activeDeviceType = try c.decodeIfPresent(String.self, forKey: .activeDeviceType)
        isPlaying = try c.decodeIfPresent(Bool.self, forKey: .isPlaying) ?? false
        updatedAt = try c.decodeIfPresent(Int.self, forKey: .updatedAt) ?? 0
    }
}
