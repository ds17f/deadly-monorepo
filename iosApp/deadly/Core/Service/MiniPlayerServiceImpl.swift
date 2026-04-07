import Foundation
import SwiftAudioStreamEx

/// Concrete implementation of `MiniPlayerService` backed by `StreamPlayer`.
///
/// All properties are computed — reads through to `streamPlayer` so that
/// observation propagates automatically via `@Observable`.
@Observable
@MainActor
final class MiniPlayerServiceImpl: MiniPlayerService {

    private let streamPlayer: StreamPlayer
    var connectService: ConnectService?

    nonisolated init(streamPlayer: StreamPlayer) {
        self.streamPlayer = streamPlayer
    }

    // MARK: - Computed state

    var isVisible: Bool {
        streamPlayer.playbackState.isActive || hasError
    }

    var trackTitle: String? {
        streamPlayer.currentTrack?.title
    }

    var albumTitle: String? {
        streamPlayer.currentTrack?.albumTitle
    }

    /// Extract the archive.org recording ID from the current track's stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
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
        streamPlayer.playbackState.isPlaying
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
        streamPlayer.progress.progress
    }

    var showDate: String? {
        let d = streamPlayer.currentTrack?.metadata["showDate"]
        return (d?.isEmpty == false) ? d : nil
    }

    var venue: String? {
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

    // MARK: - Connect state

    var isPendingCommand: Bool {
        connectService?.pendingCommand != nil
    }

    // MARK: - Actions

    func togglePlayPause() {
        guard let connect = connectService else {
            streamPlayer.togglePlayPause()
            return
        }

        if connect.isRemoteControlling {
            // Remote control: send command only, wait for server to confirm
            if connect.connectState?.playing == true {
                connect.sendPause()
            } else {
                connect.sendPlay()
            }
        } else {
            // Active device (or no active device): drive local audio optimistically + send command
            let wasPlaying = streamPlayer.playbackState.isPlaying
            streamPlayer.togglePlayPause()
            if wasPlaying {
                connect.sendPause()
            } else {
                connect.sendPlay()
            }
        }
    }

    func skipNext() {
        streamPlayer.next()
    }
}
