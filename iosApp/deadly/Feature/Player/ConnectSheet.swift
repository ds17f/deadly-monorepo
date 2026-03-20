import SwiftUI

struct ConnectSheet: View {
    let connectService: ConnectService

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                switch connectService.connectionState {
                case .disconnected:
                    ContentUnavailableView(
                        "Not Connected",
                        systemImage: "wifi.slash",
                        description: Text("Sign in to connect devices")
                    )

                case .connecting, .reconnecting:
                    ProgressView("Connecting...")

                case .connected:
                    if connectService.devices.isEmpty {
                        ContentUnavailableView(
                            "No Devices",
                            systemImage: "desktopcomputer",
                            description: Text("Open the app on another device to see it here")
                        )
                    } else {
                        deviceList
                    }
                }
            }
            .navigationTitle("Devices")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }

    @ViewBuilder
    private var deviceList: some View {
        List {
            ForEach(connectService.devices) { device in
                let isActive = device.deviceId == connectService.userState?.activeDeviceId

                Button {
                    if !isActive { transferTo(device) }
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: iconName(for: device.type))
                            .font(.title2)
                            .foregroundStyle(isActive ? Color.accentColor : .secondary)
                            .frame(width: 32)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(device.name)
                                .font(.body)
                                .fontWeight(isActive ? .semibold : .regular)
                                .foregroundStyle(isActive ? Color.accentColor : .primary)

                            Text(device.type.capitalized)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        if isActive {
                            Circle()
                                .fill(Color.accentColor)
                                .frame(width: 8, height: 8)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

            // Resume option when state is parked
            if let state = connectService.userState, state.activeDeviceId == nil {
                Section {
                    Button {
                        connectService.claimSession()
                        dismiss()
                    } label: {
                        Label("Resume playback here", systemImage: "play.fill")
                    }
                }
            }
        }
    }

    private func transferTo(_ device: ConnectDevice) {
        guard let state = connectService.userState else { return }
        connectService.playOnDevice(
            targetDeviceId: device.deviceId,
            state: ConnectPlaybackState(
                showId: state.showId,
                recordingId: state.recordingId,
                trackIndex: state.trackIndex,
                positionMs: state.positionMs,
                durationMs: state.durationMs,
                trackTitle: state.trackTitle,
                status: state.isPlaying ? "playing" : "paused",
                date: state.date,
                venue: state.venue,
                location: state.location
            )
        )
        dismiss()
    }

    private func iconName(for type: String) -> String {
        switch type {
        case "ios": return "iphone"
        case "android": return "candybarphone"
        case "web": return "desktopcomputer"
        default: return "display"
        }
    }
}
