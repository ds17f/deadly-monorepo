import SwiftUI
import SwiftAudioStreamEx

/// Sheet shown from the player that lists connected devices and allows
/// transferring playback. Playback controls (play/pause/next/prev) are
/// intentionally omitted — they're already in the player itself.
struct PlayerConnectSheet: View {
    var connectService: ConnectService
    var playlistService: PlaylistServiceImpl
    var streamPlayer: StreamPlayer
    @Environment(\.dismiss) private var dismiss

    private var isLocalDevice: (ConnectDevice) -> Bool {
        { $0.deviceId == connectService.deviceId }
    }

    private func playOnDevice(_ device: ConnectDevice) {
        let isActiveDevice = connectService.userState?.activeDeviceId == connectService.deviceId

        if isActiveDevice, let show = playlistService.currentShow {
            let posMs = Int(streamPlayer.progress.currentTime * 1000)
            let durMs = Int(streamPlayer.progress.duration * 1000)
            let tracks = playlistService.tracks.map { t in
                SessionTrack(title: t.title, duration: t.durationInterval ?? 0)
            }
            connectService.sendSessionPlayOn(
                targetDeviceId: device.deviceId,
                state: OutgoingPlaybackState(
                    showId: show.id,
                    recordingId: playlistService.currentRecording?.identifier ?? "",
                    trackIndex: streamPlayer.queueState.currentIndex,
                    positionMs: posMs,
                    durationMs: durMs,
                    trackTitle: streamPlayer.currentTrack?.title,
                    status: "playing",
                    date: show.date,
                    venue: show.venue.name,
                    location: show.location.displayText,
                    tracks: tracks
                )
            )
        } else if let us = connectService.userState, let showId = us.showId, let recordingId = us.recordingId {
            connectService.sendSessionPlayOn(
                targetDeviceId: device.deviceId,
                state: OutgoingPlaybackState(
                    showId: showId,
                    recordingId: recordingId,
                    trackIndex: us.trackIndex,
                    positionMs: us.positionMs,
                    durationMs: us.durationMs,
                    trackTitle: us.trackTitle,
                    status: "playing",
                    date: us.date,
                    venue: us.venue,
                    location: us.location,
                    tracks: nil
                )
            )
        }
        dismiss()
    }

    var body: some View {
        NavigationStack {
            List {
                // Connection status
                Section {
                    HStack {
                        Circle()
                            .fill(connectService.connectionState == .connected
                                  ? Color.green : Color.orange)
                            .frame(width: 8, height: 8)
                        Text(statusText)
                            .foregroundStyle(.secondary)
                    }
                }

                // Now Playing — where the stream currently is
                if let us = connectService.userState, let trackTitle = us.trackTitle {
                    Section("Now Playing") {
                        HStack(spacing: 12) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .foregroundStyle(DeadlyColors.primary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(trackTitle)
                                    .fontWeight(.medium)
                                HStack(spacing: 4) {
                                    if let deviceName = us.activeDeviceName {
                                        Text(deviceName)
                                            .font(.callout)
                                            .foregroundStyle(.secondary)
                                    }
                                    if let date = us.date {
                                        Text("·")
                                            .font(.callout)
                                            .foregroundStyle(.tertiary)
                                        Text(date)
                                            .font(.callout)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Device list
                Section("Devices") {
                    if connectService.devices.isEmpty {
                        Text("No devices connected")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(connectService.devices) { device in
                            let isActive = connectService.userState?.activeDeviceId == device.deviceId
                            let isLocal = isLocalDevice(device)
                            let hasPlaybackState = playlistService.currentShow != nil
                                || connectService.userState?.showId != nil
                            let canTransfer = !isActive && hasPlaybackState

                            Button {
                                if canTransfer { playOnDevice(device) }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: deviceIcon(for: device.type))
                                        .foregroundStyle(isActive ? DeadlyColors.primary : .secondary)
                                        .frame(width: 20)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(device.name)
                                            .foregroundStyle(isActive ? DeadlyColors.primary : .primary)
                                        Text(isLocal
                                             ? "\(device.type.capitalized) · This device"
                                             : device.type.capitalized)
                                            .font(.callout)
                                            .foregroundStyle(.secondary)
                                    }
                                    if canTransfer {
                                        Spacer()
                                        Image(systemName: "play.circle")
                                            .foregroundStyle(DeadlyColors.primary)
                                    }
                                }
                            }
                            .disabled(!canTransfer)
                        }
                    }
                }
            }
            .navigationTitle("Connect")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var statusText: String {
        switch connectService.connectionState {
        case .connected: return "Connected"
        case .connecting: return "Connecting…"
        case .reconnecting: return "Reconnecting…"
        case .disconnected: return "Disconnected"
        }
    }

    private func deviceIcon(for type: String) -> String {
        switch type {
        case "ios": return "iphone"
        case "android": return "phone"
        case "web": return "laptopcomputer"
        default: return "antenna.radiowaves.left.and.right"
        }
    }
}
