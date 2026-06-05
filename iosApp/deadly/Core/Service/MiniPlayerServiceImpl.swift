import Foundation
import SwiftAudioStreamEx
import os.log

private let logger = Logger(subsystem: "com.grateful.deadly", category: "MiniPlayerService")

/// Concrete implementation of `MiniPlayerService` backed by `StreamPlayer`.
///
/// All properties are computed — reads through to `streamPlayer` so that
/// observation propagates automatically via `@Observable`.
///
/// A `restoredTrack` skeleton can be set at launch from `LastPlayedTrackStore`
/// so the mini player renders immediately, before `PlaybackRestorationService`
/// has finished loading the show / queue. Once the streamPlayer's `currentTrack`
/// is populated, the skeleton is shadowed automatically.
///
/// When Connect is remote-controlling another device, the mini player mirrors
/// the shared `ConnectState` instead of the (inactive) local `streamPlayer`.
@Observable
@MainActor
final class MiniPlayerServiceImpl: MiniPlayerService {

    private let streamPlayer: StreamPlayer
    var connectService: ConnectService?

    /// Pre-queue placeholder from `LastPlayedTrackStore`. Set at app launch
    /// via `seedRestoredTrack(_:)`; cleared automatically once the streamPlayer
    /// has a real `currentTrack`.
    private(set) var restoredTrack: LastPlayedTrack?

    /// Ticks every ~250ms to drive interpolation of remote playback position.
    /// Reading this in computed properties causes SwiftUI views to re-render.
    private var interpolationTick: Date = Date()
    @ObservationIgnored private var interpolationTimer: Timer?

    nonisolated init(streamPlayer: StreamPlayer) {
        self.streamPlayer = streamPlayer
        Task { @MainActor [weak self] in
            self?.startInterpolationTicker()
            // When the local (active) device auto-advances to the next track,
            // broadcast it so remote controllers/targets follow along.
            streamPlayer.onTrackComplete = { [weak self] in
                self?.connectService?.sendNext()
            }
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

    /// Seed the mini player with a saved track so it can render before the
    /// real queue is loaded. Pass `nil` to clear.
    func seedRestoredTrack(_ track: LastPlayedTrack?) {
        restoredTrack = track
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

    /// Interpolate position for a remote (non-active) playing device based on
    /// positionMs + elapsed wall-clock time since positionTs. Reads
    /// `interpolationTick` so SwiftUI observes it and re-renders on each tick.
    ///
    /// `positionTs` is the server's wall-clock, so we translate our local tick
    /// into server space via `connectService.serverTimeOffsetMs` before
    /// subtracting — without it, clock-skewed devices scroll the progress bar
    /// at the wrong position.
    private func interpolatedRemotePositionMs(_ r: ConnectState) -> Int {
        let localMs = interpolationTick.timeIntervalSince1970 * 1000
        let offset = connectService?.serverTimeOffsetMs ?? 0
        let serverNowMs = localMs + offset
        if r.playing {
            let elapsed = max(0, serverNowMs - r.positionTs)
            return r.positionMs + Int(elapsed)
        } else {
            return r.positionMs
        }
    }

    // MARK: - Computed state

    /// True when the streamPlayer has no real track loaded yet but a saved
    /// track is being shown. Controls are disabled in this state.
    var isSkeleton: Bool {
        streamPlayer.currentTrack == nil && restoredTrack != nil
    }

    var isPreparing: Bool {
        streamPlayer.isPreparing
    }

    var isBuffering: Bool {
        switch streamPlayer.playbackState {
        case .loading, .buffering: return true
        default: return false
        }
    }

    var isRetrying: Bool {
        streamPlayer.isRetrying
    }

    var isVisible: Bool {
        streamPlayer.playbackState.isActive
            || streamPlayer.currentTrack != nil
            || restoredTrack != nil
            || hasError
            || remote != nil
    }

    var trackTitle: String? {
        if let r = remote {
            let idx = r.trackIndex
            return idx >= 0 && idx < r.tracks.count ? r.tracks[idx].title : nil
        }
        return streamPlayer.currentTrack?.title ?? restoredTrack?.trackTitle
    }

    var albumTitle: String? {
        if remote != nil { return nil }
        if let real = streamPlayer.currentTrack?.albumTitle { return real }
        // Build the same "venue — date" format as PlaylistService uses.
        guard let saved = restoredTrack, let venue = saved.venue else { return nil }
        return "\(venue) — \(saved.showDate)"
    }

    /// Extract the archive.org recording ID from the current track's stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
    var artworkRecordingId: String? {
        if let r = remote { return r.recordingId }
        if let url = streamPlayer.currentTrack?.url {
            let parts = url.pathComponents
            if parts.count >= 3, parts[1] == "download" {
                return parts[2]
            }
        }
        return restoredTrack?.recordingId
    }

    var artworkURL: String? {
        // Prefer local ticket art when present (kept even when mirroring remote).
        if let real = streamPlayer.currentTrack?.artworkURL?.absoluteString { return real }
        if let r = remote, let rec = r.recordingId {
            return "https://archive.org/services/img/\(rec)"
        }
        guard let recId = restoredTrack?.recordingId else { return nil }
        return "https://archive.org/services/img/\(recId)"
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

    var showDate: String? {
        if let r = remote { return r.date }
        if let d = streamPlayer.currentTrack?.metadata["showDate"], !d.isEmpty {
            return d
        }
        return restoredTrack?.showDate
    }

    var venue: String? {
        if let r = remote {
            if let v = r.venue, !v.isEmpty { return v }
            return r.location
        }
        if let v = streamPlayer.currentTrack?.metadata["venue"], !v.isEmpty {
            return v
        }
        if let loc = streamPlayer.currentTrack?.metadata["location"], !loc.isEmpty {
            return loc
        }
        return restoredTrack?.venue ?? restoredTrack?.location
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

    var isPendingCommand: Bool {
        // Only surface a spinner while remote-controlling another device, where a
        // command genuinely awaits a server round-trip. When we're the active/sole
        // device, local playback is authoritative and responds instantly — never
        // gate the UI on a confirmation that may never arrive (e.g. WS offline).
        guard let connect = connectService, connect.isRemoteControlling else { return false }
        return connect.pendingCommand != nil
    }

    // MARK: - Actions

    func togglePlayPause() {
        guard let connect = connectService else {
            guard !isSkeleton else { return }
            streamPlayer.togglePlayPause()
            return
        }

        if connect.isRemoteControlling {
            // Remote control: send command only, wait for server to confirm.
            let serverPlaying = connect.connectState?.playing ?? false
            logger.info("togglePlayPause: remote -> \(serverPlaying ? "sendPause" : "sendPlay", privacy: .public)")
            if serverPlaying { connect.sendPause() } else { connect.sendPlay() }
            return
        }

        // Active device (or no active device): drive local audio optimistically + send command.
        guard !isSkeleton else { return }
        let wasPlaying = streamPlayer.playbackState.isPlaying
        streamPlayer.togglePlayPause()
        logger.info("togglePlayPause: local toggle (wasPlaying=\(wasPlaying, privacy: .public))")
        if wasPlaying { connect.sendPause() } else { connect.sendPlay() }
    }

    func skipNext() {
        guard let connect = connectService else {
            guard !isSkeleton else { return }
            streamPlayer.next()
            return
        }

        if connect.isRemoteControlling {
            logger.info("skipNext: remote -> sendNext")
            connect.sendNext()
        } else {
            guard !isSkeleton else { return }
            logger.info("skipNext: local -> streamPlayer.next() + sendNext")
            streamPlayer.next()
            connect.sendNext()
        }
    }

    func skipPrev() {
        guard let connect = connectService else {
            guard !isSkeleton else { return }
            streamPlayer.previous()
            return
        }

        if connect.isRemoteControlling {
            logger.info("skipPrev: remote -> sendPrev")
            connect.sendPrev()
        } else {
            guard !isSkeleton else { return }
            logger.info("skipPrev: local -> streamPlayer.previous() + sendPrev")
            streamPlayer.previous()
            connect.sendPrev()
        }
    }

    func seek(fraction: Double) {
        guard let connect = connectService else {
            guard !isSkeleton else { return }
            streamPlayer.seek(to: fraction * streamPlayer.progress.duration)
            return
        }

        if connect.isRemoteControlling {
            guard let state = connect.connectState else { return }
            let posMs = Int(fraction * Double(state.durationMs))
            logger.info("seek: remote -> sendSeek(pos=\(posMs, privacy: .public))")
            connect.sendSeek(trackIndex: state.trackIndex, positionMs: posMs, durationMs: state.durationMs)
        } else {
            guard !isSkeleton else { return }
            let target = fraction * streamPlayer.progress.duration
            let posMs = Int(target * 1000)
            let durMs = Int(streamPlayer.progress.duration * 1000)
            logger.info("seek: local -> streamPlayer.seek(\(target, privacy: .public)) + sendSeek")
            streamPlayer.seek(to: target)
            connect.sendSeek(trackIndex: streamPlayer.queueState.currentIndex, positionMs: posMs, durationMs: durMs)
        }
    }
}
