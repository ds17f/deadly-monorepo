import SwiftUI

struct ConnectScreen: View {
    @Environment(\.appContainer) private var container

    private var service: ConnectService { container.connectService }

    var body: some View {
        List {
            if !container.authService.isSignedIn {
                ContentUnavailableView(
                    "Sign In Required",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: Text("Sign in to see and manage connected devices.")
                )
            } else {
                Section {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(service.isConnected ? Color.green : Color.secondary)
                            .frame(width: 8, height: 8)
                        Text(service.isConnected ? "Connected" : "Disconnected")
                            .foregroundStyle(service.isConnected ? .primary : .secondary)
                    }
                }

                if service.devices.isEmpty {
                    Section("Devices") {
                        Text("No devices connected")
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Section("Devices") {
                        ForEach(service.devices) { device in
                            DeviceRow(
                                device: device,
                                isMe: device.deviceId == container.appPreferences.installId
                            )
                        }
                    }
                }
            }
        }
        .navigationTitle("Connected Devices")
    }
}

// MARK: - DeviceRow

private struct DeviceRow: View {
    let device: ConnectDevice
    let isMe: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.deviceType.systemImage)
                .foregroundStyle(DeadlyColors.primary)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                Text(device.deviceName)
                    .font(.body)
                Text(device.deviceType.label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if isMe {
                Spacer()
                Text("This Device")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
