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

// MARK: - ConnectState

struct ConnectState: Codable {
    let version: Int
    let showId: String?
    let recordingId: String?
    let tracks: [SessionTrack]
    let trackIndex: Int
    let positionMs: Int
    let positionTs: Double
    let durationMs: Int
    let playing: Bool
    let activeDeviceId: String?
    let activeDeviceName: String?
    let activeDeviceType: DeviceType?
    let date: String?
    let venue: String?
    let location: String?
}
