import Foundation
import SwiftAudioStreamEx
import os.log

private let logger = Logger(subsystem: "com.grateful.deadly", category: "MiniPlayerService")

/// Concrete implementation of `MiniPlayerService` backed by `StreamPlayer`.
///
/// All properties are computed — reads through to `streamPlayer` so that
/// observation propagates automatically via `@Observable`.
@Observable
@MainActor
final class MiniPlayerServiceImpl: MiniPlayerService {

    private let streamPlayer: StreamPlayer
    var connectService: ConnectService?

    /// Ticks every ~250ms to drive interpolation of remote playback position.
    /// Reading this in computed properties causes SwiftUI views to re-render.
    private var interpolationTick: Date = Date()
    @ObservationIgnored private var interpolationTimer: Timer?

    nonisolated init(streamPlayer: StreamPlayer) {
        self.streamPlayer = streamPlayer
        Task { @MainActor [weak self] in
            self?.startInterpolationTicker()
        }
    }

    private func startInterpolationTicker() {
        interpolationTimer?.invalidate()
        interpolationTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.interpolationTick = Date()
            }
        }
    }

    // MARK: - Remote state helpers

    /// Returns Connect state when a shared show is loaded and this device isn't
    /// the active playback device (i.e. local StreamPlayer is not authoritative).
    /// Covers both "another device is active" and "paused with no active device".
    private var remote: ConnectState? {
        guard let cs = connectService,
              let state = cs.connectState,
              state.showId != nil,
              !cs.isActiveDevice else { return nil }
        return state
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
        // Always prefer local artwork (ticket art) even when showing remote state
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

    var hasPrevious: Bool {
        if let r = remote { return r.trackIndex > 0 }
        return streamPlayer.queueState.hasPrevious
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
            guard r.durationMs > 0 else { return 0 }
            let pos = Double(interpolatedRemotePositionMs(r))
            return min(1.0, max(0.0, pos / Double(r.durationMs)))
        }
        return streamPlayer.progress.progress
    }

    /// Interpolate position for a remote (non-active) playing device based on
    /// positionMs + elapsed wall-clock time since positionTs. Reads interpolationTick
    /// so SwiftUI observes it and re-renders on each tick.
    private func interpolatedRemotePositionMs(_ r: ConnectState) -> Int {
        let nowMs = interpolationTick.timeIntervalSince1970 * 1000
        if r.playing {
            let elapsed = max(0, nowMs - r.positionTs)
            return r.positionMs + Int(elapsed)
        } else {
            return r.positionMs
        }
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

    var positionMs: Int {
        if let r = remote { return interpolatedRemotePositionMs(r) }
        return Int(streamPlayer.progress.currentTime * 1000)
    }

    var durationMs: Int {
        if let r = remote { return r.durationMs }
        return Int(streamPlayer.progress.duration * 1000)
    }

    var trackIndex: Int {
        if let r = remote { return r.trackIndex }
        return streamPlayer.queueState.currentIndex
    }

    var trackCount: Int {
        if let r = remote { return r.tracks.count }
        return streamPlayer.queueState.totalTracks
    }

    // MARK: - Connect state

    var isPendingCommand: Bool {
        connectService?.pendingCommand != nil
    }

    // MARK: - Actions

    func togglePlayPause() {
        guard let connect = connectService else {
            logger.info("togglePlayPause: no connectService, direct streamPlayer toggle")
            streamPlayer.togglePlayPause()
            return
        }

        let isRemote = connect.isRemoteControlling
        let isActive = connect.isActiveDevice
        let serverPlaying = connect.connectState?.playing ?? false
        let localPlaying = streamPlayer.playbackState.isPlaying

        logger.info("togglePlayPause: isRemote=\(isRemote, privacy: .public) isActive=\(isActive, privacy: .public) serverPlaying=\(serverPlaying, privacy: .public) localPlaying=\(localPlaying, privacy: .public)")

        if isRemote {
            // Remote control: send command only, wait for server to confirm
            if serverPlaying {
                logger.info("togglePlayPause: remote -> sendPause")
                connect.sendPause()
            } else {
                logger.info("togglePlayPause: remote -> sendPlay")
                connect.sendPlay()
            }
        } else {
            // Active device (or no active device): drive local audio optimistically + send command
            let wasPlaying = streamPlayer.playbackState.isPlaying
            logger.info("togglePlayPause: local toggle (wasPlaying=\(wasPlaying, privacy: .public))")
            streamPlayer.togglePlayPause()
            if wasPlaying {
                logger.info("togglePlayPause: optimistic -> sendPause")
                connect.sendPause()
            } else {
                logger.info("togglePlayPause: optimistic -> sendPlay")
                connect.sendPlay()
            }
        }
    }

    func skipNext() {
        guard let connect = connectService else {
            logger.info("skipNext: no connectService, direct streamPlayer.next()")
            streamPlayer.next()
            return
        }

        let isRemote = connect.isRemoteControlling

        if isRemote {
            logger.info("skipNext: remote -> sendNext")
            connect.sendNext()
        } else {
            logger.info("skipNext: local -> streamPlayer.next() + sendNext")
            streamPlayer.next()
            connect.sendNext()
        }
    }

    func skipPrev() {
        guard let connect = connectService else {
            logger.info("skipPrev: no connectService, direct streamPlayer.previous()")
            streamPlayer.previous()
            return
        }

        let isRemote = connect.isRemoteControlling

        if isRemote {
            logger.info("skipPrev: remote -> sendPrev")
            connect.sendPrev()
        } else {
            logger.info("skipPrev: local -> streamPlayer.previous() + sendPrev")
            streamPlayer.previous()
            connect.sendPrev()
        }
    }

    func seek(fraction: Double) {
        guard let connect = connectService else {
            logger.info("seek: no connectService, direct streamPlayer.seek()")
            let target = fraction * streamPlayer.progress.duration
            streamPlayer.seek(to: target)
            return
        }

        let isRemote = connect.isRemoteControlling

        if isRemote {
            guard let state = connect.connectState else { return }
            let posMs = Int(fraction * Double(state.durationMs))
            logger.info("seek: remote -> sendSeek(pos=\(posMs, privacy: .public))")
            connect.sendSeek(trackIndex: state.trackIndex, positionMs: posMs, durationMs: state.durationMs)
        } else {
            let target = fraction * streamPlayer.progress.duration
            let posMs = Int(target * 1000)
            let durMs = Int(streamPlayer.progress.duration * 1000)
            logger.info("seek: local -> streamPlayer.seek(\(target, privacy: .public)) + sendSeek")
            streamPlayer.seek(to: target)
            connect.sendSeek(trackIndex: streamPlayer.queueState.currentIndex, positionMs: posMs, durationMs: durMs)
        }
    }
}
