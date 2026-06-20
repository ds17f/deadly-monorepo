import SwiftUI

struct ConnectSheet: View {
    @Environment(\.appContainer) private var container
    @Environment(\.dismiss) private var dismiss
    @State private var showingStopConfirm = false
    @State private var localVolume: Double = 100
    @State private var isDragging = false
    @State private var debounceTask: Task<Void, Never>?

    private var service: ConnectService { container.connectService }

    var body: some View {
        NavigationStack {
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                }
        }
        .presentationDetents([.medium, .large])
        // Re-check the global kill switch every time the sheet opens, so the
        // correct mode (unavailable / promo / devices) is shown at this critical
        // moment even if the cached value is stale (ADR-0018).
        .onAppear { service.refreshServerConnectEnabled() }
    }

    // Three modes (ADR-0018): server off → unavailable; server on + opted out →
    // beta promo; both on → the full device picker.
    @ViewBuilder
    private var content: some View {
        if !service.serverConnectEnabled {
            ConnectUnavailableView()
                .navigationTitle("Connect")
        } else if !container.appPreferences.connectEnabled {
            ConnectEnablePromoView(
                onEnable: { service.setEnabled(true) },
                onNotNow: { dismiss() }
            )
            .navigationTitle("Connect")
        } else {
            fullDeviceList
                .navigationTitle("Devices")
        }
    }

    private var fullDeviceList: some View {
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
                    let activeDeviceId = service.connectState?.activeDeviceId
                    let activeDevice = service.devices.first { $0.deviceId == activeDeviceId }
                    let localDevice = service.devices.first { $0.deviceId == myId }
                    let otherDevices = service.devices.filter { $0.deviceId != myId }
                    let allDevices = (([localDevice].compactMap { $0 }) + otherDevices)
                        .filter { $0.deviceId != activeDeviceId }

                    // Playback Device section — always the active device
                    Section("Playback Device") {
                        if let device = activeDevice {
                            let isPending = service.pendingTransfer == device.deviceId
                            ConnectDeviceRow(
                                device: device,
                                label: device.deviceId == myId ? "This Device" : device.deviceType.label,
                                isDeviceActive: true,
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

                        // Volume slider — shown when there's an active device
                        if hasSession, activeDeviceId != nil {
                            HStack(spacing: 10) {
                                Image(systemName: "speaker.fill")
                                    .foregroundStyle(.secondary)
                                    .frame(width: 20)
                                Slider(
                                    value: $localVolume,
                                    in: 0...100,
                                    onEditingChanged: { editing in
                                        isDragging = editing
                                        if !editing {
                                            debounceTask?.cancel()
                                            service.sendVolume(volume: Int(localVolume))
                                        }
                                    }
                                )
                                .onChange(of: localVolume) { _, newValue in
                                    guard isDragging else { return }
                                    debounceTask?.cancel()
                                    debounceTask = Task {
                                        try? await Task.sleep(for: .milliseconds(150))
                                        guard !Task.isCancelled else { return }
                                        service.sendVolume(volume: Int(newValue))
                                    }
                                }
                                Image(systemName: "speaker.wave.3.fill")
                                    .foregroundStyle(.secondary)
                                    .frame(width: 20)
                            }
                            .padding(.vertical, 2)
                        }
                    }
                    .onChange(of: service.activeDeviceVolume) { _, newValue in
                        guard !isDragging else { return }
                        localVolume = Double(newValue)
                    }

                    // All devices — local first, then others
                    if !allDevices.isEmpty {
                        Section("Devices") {
                            ForEach(allDevices) { device in
                                let isDeviceActive = device.deviceId == activeDeviceId
                                let isPending = service.pendingTransfer == device.deviceId
                                let isLocal = device.deviceId == myId
                                ConnectDeviceRow(
                                    device: device,
                                    label: isLocal ? "\(device.deviceType.label) (This Device)" : device.deviceType.label,
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

                    // Per-device beta opt-out (ADR-0018). Affects this device only.
                    Section {
                        Button("Turn off Connect", role: .destructive) {
                            service.setEnabled(false)
                            dismiss()
                        }
                    } footer: {
                        Text("Stops Connect on this device only. Your other devices stay connected — turn it off there too to fully stop syncing.")
                    }
                }
            }
            .onAppear {
                localVolume = Double(service.activeDeviceVolume)
            }
            .confirmationDialog("Stop Session?", isPresented: $showingStopConfirm, titleVisibility: .visible) {
                Button("Stop Session", role: .destructive) {
                    service.sendStop()
                }
                Button("Cancel", role: .cancel) {}
            }
    }
}

// MARK: - Unavailable / Promo modes

/// Server kill switch is OFF — Connect can't be used right now (ADR-0018).
private struct ConnectUnavailableView: View {
    var body: some View {
        ContentUnavailableView(
            "Connect is unavailable",
            systemImage: "airplayaudio",
            description: Text("Connect (cross-device playback) is temporarily turned off. Check back later.")
        )
    }
}

/// Server is ON but this device hasn't opted in — the "Enable Connect (Beta)"
/// promo with the confirmation gate (ADR-0018).
private struct ConnectEnablePromoView: View {
    let onEnable: () -> Void
    let onNotNow: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Image(systemName: "airplayaudio")
                    .font(.title)
                    .foregroundStyle(DeadlyColors.primary)
                Text("Enable Connect (Beta)")
                    .font(.title2.bold())
            }

            Text("Connect keeps playback in sync across your devices — start a show on your phone, pick it up on another device.")
                .foregroundStyle(.secondary)

            Text("It's a beta feature and may occasionally skip, pause, or fall out of sync. You'll need to enable Connect on your other devices too for them to appear here.")
                .foregroundStyle(.secondary)

            Spacer()

            Button(action: onEnable) {
                Text("Enable Connect")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            Button(action: onNotNow) {
                Text("Not now")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
        }
        .padding(24)
    }
}

// MARK: - ConnectDeviceRow

struct ConnectDeviceRow: View {
    let device: ConnectDevice
    let label: String
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
                    Text(label)
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
