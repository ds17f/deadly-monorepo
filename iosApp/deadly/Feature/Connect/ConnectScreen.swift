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
                            ConnectDeviceRow(
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
