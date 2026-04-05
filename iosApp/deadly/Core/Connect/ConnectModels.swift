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

// MARK: - Outgoing Session Update

struct SessionUpdateMessage: Encodable {
    let type = "session_update"
    let state: OutgoingPlaybackState
}

struct SessionPlayOnMessage: Encodable {
    let type = "session_play_on"
    let targetDeviceId: String
    let state: OutgoingPlaybackState
}

struct SessionTrack: Encodable {
    let title: String
    let duration: Double  // seconds
}

struct OutgoingPlaybackState: Encodable {
    let showId: String
    let recordingId: String
    let trackIndex: Int
    let positionMs: Int
    let durationMs: Int
    let trackTitle: String?
    let status: String  // "playing", "paused", "stopped"
    let date: String?
    let venue: String?
    let location: String?
    let tracks: [SessionTrack]?
}

// MARK: - Incoming Playback State (from remote device)

struct IncomingPlaybackState: Codable {
    let showId: String
    let recordingId: String
    let trackIndex: Int
    let positionMs: Int
    let durationMs: Int?
    let trackTitle: String?
    let status: String?
    let date: String?
    let venue: String?
    let location: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        showId = try c.decode(String.self, forKey: .showId)
        recordingId = try c.decode(String.self, forKey: .recordingId)
        trackIndex = try c.decodeIfPresent(Int.self, forKey: .trackIndex) ?? 0
        positionMs = try c.decodeIfPresent(Int.self, forKey: .positionMs) ?? 0
        durationMs = try c.decodeIfPresent(Int.self, forKey: .durationMs)
        trackTitle = try c.decodeIfPresent(String.self, forKey: .trackTitle)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        date = try c.decodeIfPresent(String.self, forKey: .date)
        venue = try c.decodeIfPresent(String.self, forKey: .venue)
        location = try c.decodeIfPresent(String.self, forKey: .location)
    }
}

// MARK: - Server Config

struct ConnectConfig {
    var positionUpdateIntervalMs: Int = 5000
    var seekDivergenceThresholdMs: Int = 2000
    var redirectMaxAgeSec: Int = 120
    var seekSettleDelayMs: Int = 500
}

enum ConnectPlaybackEvent {
    case playOn(IncomingPlaybackState, relayedAt: Int?)
    case stop
    /// Emitted once after connecting when the server sends the initial user_state.
    /// The client should sync its UI to this state without clobbering the active session.
    case syncState(UserPlaybackState)
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
