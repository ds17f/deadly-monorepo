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
    let type: String       // "ios" | "android" | "web"
    let name: String
    let capabilities: [String]

    var id: String { deviceId }
}

// MARK: - Playback State (wire format)

struct ConnectPlaybackState: Codable {
    let showId: String
    let recordingId: String
    let trackIndex: Int
    let positionMs: Int
    var durationMs: Int?
    var trackTitle: String?
    let status: String     // "playing" | "paused" | "stopped"
    var date: String?
    var venue: String?
    var location: String?
}

// MARK: - User Playback State

struct UserPlaybackState: Codable {
    let showId: String
    let recordingId: String
    let trackIndex: Int
    let positionMs: Int
    let durationMs: Int
    var trackTitle: String?
    var date: String?
    var venue: String?
    var location: String?
    let activeDeviceId: String?
    let activeDeviceName: String?
    let activeDeviceType: String?
    let isPlaying: Bool
    let updatedAt: Double
}

// MARK: - Playback Command

struct PlaybackCommand: Codable {
    let action: String     // "play" | "pause" | "stop" | "next" | "prev" | "seek"
    var seekMs: Int?
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

struct SessionUpdateMessage: Encodable {
    let type = "session_update"
    let state: ConnectPlaybackState
}

struct PositionUpdateMessage: Encodable {
    let type = "position_update"
    let state: ConnectPlaybackState
}

struct SessionClaimMessage: Encodable {
    let type = "session_claim"
}

struct SessionPlayOnMessage: Encodable {
    let type = "session_play_on"
    let targetDeviceId: String
    let state: ConnectPlaybackState
}

struct CommandSendMessage: Encodable {
    let type = "command"
    let targetDeviceId: String
    let command: PlaybackCommand
}

struct StateClearMessage: Encodable {
    let type = "state_clear"
}
