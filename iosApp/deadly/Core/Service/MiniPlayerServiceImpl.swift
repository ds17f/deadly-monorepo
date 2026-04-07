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

    // MARK: - Remote state helpers

    private var remote: ConnectState? {
        guard let cs = connectService, cs.isRemoteControlling else { return nil }
        return cs.connectState
    }

    // MARK: - Computed state

    var isVisible: Bool {
        streamPlayer.playbackState.isActive || hasError || remote != nil
    }

    var trackTitle: String? {
        if let r = remote {
            let idx = r.trackIndex
            return idx < r.tracks.count ? r.tracks[idx].title : nil
        }
        return streamPlayer.currentTrack?.title
    }

    var albumTitle: String? {
        if remote != nil { return nil }
        return streamPlayer.currentTrack?.albumTitle
    }

    /// Extract the archive.org recording ID from the current track's stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
    var artworkRecordingId: String? {
        if let r = remote { return r.recordingId }
        guard let url = streamPlayer.currentTrack?.url else { return nil }
        let parts = url.pathComponents
        guard parts.count >= 3, parts[1] == "download" else { return nil }
        return parts[2]
    }

    var artworkURL: String? {
        if remote != nil { return nil }
        return streamPlayer.currentTrack?.artworkURL?.absoluteString
    }

    var isPlaying: Bool {
        if let r = remote { return r.playing }
        return streamPlayer.playbackState.isPlaying
    }

    var hasNext: Bool {
        if let r = remote { return r.trackIndex < r.tracks.count - 1 }
        return streamPlayer.queueState.hasNext
    }

    var hasError: Bool {
        if remote != nil { return false }
        if case .error = streamPlayer.playbackState {
            return true
        }
        return false
    }

    var errorMessage: String? {
        if remote != nil { return nil }
        if case .error(let error) = streamPlayer.playbackState {
            return error.localizedDescription
        }
        return nil
    }

    var playbackProgress: Double {
        if let r = remote {
            return r.durationMs > 0 ? Double(r.positionMs) / Double(r.durationMs) : 0
        }
        return streamPlayer.progress.progress
    }

    var showDate: String? {
        if let r = remote { return r.date }
        let d = streamPlayer.currentTrack?.metadata["showDate"]
        return (d?.isEmpty == false) ? d : nil
    }

    var venue: String? {
        if let r = remote {
            if let v = r.venue, !v.isEmpty { return v }
            return r.location
        }
        let v = streamPlayer.currentTrack?.metadata["venue"]
        if v?.isEmpty == false { return v }
        let loc = streamPlayer.currentTrack?.metadata["location"]
        return (loc?.isEmpty == false) ? loc : nil
    }

    var displaySubtitle: String? {
        if let r = remote {
            var result = ""
            if let d = r.date { result += d }
            if let v = r.venue, !v.isEmpty {
                if !result.isEmpty { result += " - " }
                result += v
            }
            if let name = r.activeDeviceName {
                if !result.isEmpty { result += " · " }
                result += name
            }
            return result.isEmpty ? nil : result
        }
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
