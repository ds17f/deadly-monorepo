import SwiftUI

struct ConnectScreen: View {
    @Environment(\.appContainer) private var container
    @State private var showingStopConfirm = false

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
                        let hasSession = service.connectState?.showId != nil
                        ForEach(service.devices) { device in
                            let isMe = device.deviceId == container.appPreferences.installId
                            let isDeviceActive = device.deviceId == service.connectState?.activeDeviceId
                            let isPending = service.pendingTransfer == device.deviceId
                            DeviceRow(
                                device: device,
                                isMe: isMe,
                                isDeviceActive: isDeviceActive,
                                hasSession: hasSession,
                                isPending: isPending,
                                transferDisabled: service.pendingTransfer != nil,
                                isRemoteControlling: service.isRemoteControlling,
                                onTransfer: { service.sendTransfer(targetDeviceId: device.deviceId) }
                            )
                        }
                    }
                }
            }
        }
        .navigationTitle("Connected Devices")
        .toolbar {
            if service.connectState?.showId != nil {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Stop", role: .destructive) {
                        showingStopConfirm = true
                    }
                }
            }
        }
        .confirmationDialog("Stop Session?", isPresented: $showingStopConfirm, titleVisibility: .visible) {
            Button("Stop Session", role: .destructive) {
                service.sendStop()
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

// MARK: - DeviceRow

private struct DeviceRow: View {
    let device: ConnectDevice
    let isMe: Bool
    let isDeviceActive: Bool
    let hasSession: Bool
    let isPending: Bool
    let transferDisabled: Bool
    let isRemoteControlling: Bool
    let onTransfer: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.deviceType.systemImage)
                .foregroundStyle(DeadlyColors.primary)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                Text(device.deviceName)
                    .font(.body)
                Text(isMe ? "This Device" : device.deviceType.label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if hasSession {
                if isPending {
                    ProgressView()
                        .controlSize(.small)
                } else if isDeviceActive {
                    Text("Playing")
                        .font(.caption)
                        .foregroundStyle(DeadlyColors.primary)
                } else if isMe && isRemoteControlling {
                    Button("Play here", action: onTransfer)
                        .font(.caption)
                        .disabled(transferDisabled)
                } else if !isMe {
                    Button("Play", action: onTransfer)
                        .font(.caption)
                        .disabled(transferDisabled)
                }
            }
        }
    }
}
