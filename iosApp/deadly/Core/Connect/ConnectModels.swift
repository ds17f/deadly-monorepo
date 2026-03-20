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
