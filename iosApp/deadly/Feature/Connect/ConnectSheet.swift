import SwiftUI

struct ConnectSheet: View {
    @Environment(\.appContainer) private var container
    @Environment(\.dismiss) private var dismiss
    @State private var showingStopConfirm = false

    private var service: ConnectService { container.connectService }

    var body: some View {
        NavigationStack {
            List {
                if !container.authService.isSignedIn {
                    ContentUnavailableView(
                        "Sign In Required",
                        systemImage: "person.crop.circle.badge.exclamationmark",
                        description: Text("Sign in to see and manage connected devices.")
                    )
                } else {
                    let hasSession = service.connectState?.showId != nil
                    let myId = container.appPreferences.installId
                    let localDevice = service.devices.first { $0.deviceId == myId }
                    let otherDevices = service.devices.filter { $0.deviceId != myId }

                    // This Device section
                    Section("This Device") {
                        if let device = localDevice {
                            let isDeviceActive = device.deviceId == service.connectState?.activeDeviceId
                            let isPending = service.pendingTransfer == device.deviceId
                            ConnectDeviceRow(
                                device: device,
                                isMe: true,
                                isDeviceActive: isDeviceActive,
                                hasSession: hasSession,
                                isPending: isPending,
                                transferDisabled: service.pendingTransfer != nil,
                                isRemoteControlling: service.isRemoteControlling,
                                onTransfer: { service.sendTransfer(targetDeviceId: device.deviceId) }
                            )
                        } else {
                            HStack(spacing: 8) {
                                Circle()
                                    .fill(service.isConnected ? Color.green : Color.secondary)
                                    .frame(width: 8, height: 8)
                                Text(service.isConnected ? "Connected" : "Disconnected")
                                    .foregroundStyle(service.isConnected ? .primary : .secondary)
                            }
                        }
                    }

                    // Other devices section
                    if otherDevices.isEmpty {
                        Section("Other Devices") {
                            Text("No other devices connected")
                                .foregroundStyle(.secondary)
                        }
                    } else {
                        Section("Other Devices") {
                            ForEach(otherDevices) { device in
                                let isDeviceActive = device.deviceId == service.connectState?.activeDeviceId
                                let isPending = service.pendingTransfer == device.deviceId
                                ConnectDeviceRow(
                                    device: device,
                                    isMe: false,
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

                    if service.connectState?.showId != nil {
                        Section {
                            Button("Stop Session", role: .destructive) {
                                showingStopConfirm = true
                            }
                        }
                    }
                }
            }
            .navigationTitle("Devices")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .confirmationDialog("Stop Session?", isPresented: $showingStopConfirm, titleVisibility: .visible) {
                Button("Stop Session", role: .destructive) {
                    service.sendStop()
                }
                Button("Cancel", role: .cancel) {}
            }
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - ConnectDeviceRow

struct ConnectDeviceRow: View {
    let device: ConnectDevice
    let isMe: Bool
    let isDeviceActive: Bool
    let hasSession: Bool
    let isPending: Bool
    let transferDisabled: Bool
    let isRemoteControlling: Bool
    let onTransfer: () -> Void

    private var isTappable: Bool {
        hasSession && !isPending && !isDeviceActive && !transferDisabled
    }

    var body: some View {
        Button(action: onTransfer) {
            HStack(spacing: 12) {
                Image(systemName: device.deviceType.systemImage)
                    .foregroundStyle(isDeviceActive ? DeadlyColors.primary : .primary)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(device.deviceName)
                        .font(.body)
                        .foregroundStyle(.primary)
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
                        Image(systemName: "speaker.wave.2.fill")
                            .foregroundStyle(DeadlyColors.primary)
                            .font(.caption)
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isTappable)
    }
}
