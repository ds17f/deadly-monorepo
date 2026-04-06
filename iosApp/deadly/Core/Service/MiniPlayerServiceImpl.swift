import Foundation
import SwiftAudioStreamEx

/// Concrete implementation of `MiniPlayerService` backed by `StreamPlayer`.
///
/// When a remote Connect device is active this service forwards remote state
/// (track info, play/pause, interpolated progress) instead of local state.
@Observable
@MainActor
final class MiniPlayerServiceImpl: MiniPlayerService {

    private let streamPlayer: StreamPlayer
    private let connectService: ConnectService

    nonisolated init(streamPlayer: StreamPlayer, connectService: ConnectService) {
        self.streamPlayer = streamPlayer
        self.connectService = connectService
    }

    // MARK: - Remote state

    /// True when another device owns the active Connect session.
    private var isRemoteActive: Bool {
        guard let activeId = connectService.userState?.activeDeviceId else { return false }
        return activeId != connectService.deviceId
    }

    /// Interpolated playback position in the range 0.0–1.0, updated at ~30 fps when remote.
    private(set) var interpolatedRemoteProgress: Double = 0.0

    /// Call once after init to start the interpolation loop.
    func startInterpolationLoop() {
        Task { @MainActor [weak self] in
            while true {
                guard let self else { return }
                if self.isRemoteActive, let state = self.connectService.userState {
                    let nowMs = Int(Date().timeIntervalSince1970 * 1000)
                    let durMs = state.durationMs > 0 ? state.durationMs : 1
                    if state.isPlaying {
                        let rawMs = state.positionMs + (nowMs - state.updatedAt)
                        let clampedMs = min(rawMs, state.durationMs > 0 ? state.durationMs : rawMs)
                        self.interpolatedRemoteProgress = Double(clampedMs) / Double(durMs)
                    } else {
                        self.interpolatedRemoteProgress = Double(state.positionMs) / Double(durMs)
                    }
                }
                try? await Task.sleep(for: .milliseconds(33))
            }
        }
    }

    // MARK: - Computed state

    var isVisible: Bool {
        if isRemoteActive { return connectService.userState?.trackTitle != nil }
        return streamPlayer.playbackState.isActive || hasError
    }

    var trackTitle: String? {
        if isRemoteActive { return connectService.userState?.trackTitle }
        return streamPlayer.currentTrack?.title
    }

    var albumTitle: String? {
        if isRemoteActive { return nil }
        return streamPlayer.currentTrack?.albumTitle
    }

    /// Extract the archive.org recording ID from the current track's stream URL.
    var artworkRecordingId: String? {
        guard let url = streamPlayer.currentTrack?.url else { return nil }
        let parts = url.pathComponents
        guard parts.count >= 3, parts[1] == "download" else { return nil }
        return parts[2]
    }

    var artworkURL: String? {
        streamPlayer.currentTrack?.artworkURL?.absoluteString
    }

    var isPlaying: Bool {
        if isRemoteActive { return connectService.userState?.isPlaying ?? false }
        return streamPlayer.playbackState.isPlaying
    }

    var hasNext: Bool {
        streamPlayer.queueState.hasNext
    }

    var hasError: Bool {
        if case .error = streamPlayer.playbackState {
            return true
        }
        return false
    }

    var errorMessage: String? {
        if case .error(let error) = streamPlayer.playbackState {
            return error.localizedDescription
        }
        return nil
    }

    var playbackProgress: Double {
        if isRemoteActive { return interpolatedRemoteProgress }
        return streamPlayer.progress.progress
    }

    var showDate: String? {
        if isRemoteActive { return connectService.userState?.date }
        let d = streamPlayer.currentTrack?.metadata["showDate"]
        return (d?.isEmpty == false) ? d : nil
    }

    var venue: String? {
        if isRemoteActive { return connectService.userState?.venue }
        let v = streamPlayer.currentTrack?.metadata["venue"]
        if v?.isEmpty == false { return v }
        let loc = streamPlayer.currentTrack?.metadata["location"]
        return (loc?.isEmpty == false) ? loc : nil
    }

    var displaySubtitle: String? {
        var result = ""
        if let date = showDate {
            result += date
        }
        if let v = venue {
            if !result.isEmpty { result += " - " }
            result += v
        }
        return result.isEmpty ? nil : result
    }

    // MARK: - Actions

    func togglePlayPause() {
        if isRemoteActive {
            let playing = connectService.userState?.isPlaying ?? false
            connectService.sendCommand(action: playing ? "pause" : "play")
        } else {
            streamPlayer.togglePlayPause()
        }
    }

    func skipNext() {
        if isRemoteActive {
            connectService.sendCommand(action: "next")
        } else {
            streamPlayer.next()
        }
    }
}
