import Foundation

// MARK: - DeviceType

enum DeviceType: String, Codable {
    case ios
    case android
    case web

    var systemImage: String {
        switch self {
        case .ios: return "airplayaudio"
        case .android: return "iphone.and.arrow.forward" // closest available
        case .web: return "globe"
        }
    }

    var label: String { rawValue.capitalized }
}

// MARK: - ConnectDevice

struct ConnectDevice: Codable, Identifiable {
    let deviceId: String
    let deviceType: DeviceType
    let deviceName: String

    var id: String { deviceId }
}

// MARK: - SessionTrack

struct SessionTrack: Codable {
    let title: String
    let durationMs: Int
}

// MARK: - PendingAdvance

// ADR-0010 §7: shared end-of-show countdown. deadline is an absolute server
// timestamp (ms), Double like positionTs (the server may send a fractional value).
struct PendingAdvance: Codable, Equatable {
    let showId: String
    let deadline: Double
}

// MARK: - ConnectState

struct ConnectState: Codable {
    let version: Int
    // Server boot id — a change means the server restarted (see api ConnectState.epoch).
    let epoch: Int
    let showId: String?
    let recordingId: String?
    let tracks: [SessionTrack]
    let trackIndex: Int
    let positionMs: Int
    let positionTs: Double
    let durationMs: Int
    // Bumped only by an explicit server-side seek (never by routine position
    // reports). The active device seeks when this advances, not on positionMs
    // deltas — so our own position echoes can't cause skips and small remote seeks
    // still land. Optional/additive (decodeIfPresent → nil against a server that
    // doesn't send it; treated as 0). See ADR-0017.
    let seekNonce: Int?
    let playing: Bool
    let activeDeviceId: String?
    let activeDeviceName: String?
    let activeDeviceType: DeviceType?
    let date: String?
    let venue: String?
    let location: String?
    // ADR-0010 §7: shared end-of-show countdown. Optional/additive — a missing
    // key decodes to nil (synthesized Decodable uses decodeIfPresent for optionals).
    let pendingAdvance: PendingAdvance?
}
