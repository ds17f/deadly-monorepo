import Foundation
import SwiftAudioStreamEx

/// Concrete implementation of `MiniPlayerService` backed by `StreamPlayer`.
///
/// All properties are computed — reads through to `streamPlayer` so that
/// observation propagates automatically via `@Observable`.
///
/// A `restoredTrack` skeleton can be set at launch from `LastPlayedTrackStore`
/// so the mini player renders immediately, before `PlaybackRestorationService`
/// has finished loading the show / queue. Once the streamPlayer's `currentTrack`
/// is populated, the skeleton is shadowed automatically.
@Observable
@MainActor
final class MiniPlayerServiceImpl: MiniPlayerService {

    private let streamPlayer: StreamPlayer

    /// Pre-queue placeholder from `LastPlayedTrackStore`. Set at app launch
    /// via `seedRestoredTrack(_:)`; cleared automatically once the streamPlayer
    /// has a real `currentTrack`.
    private(set) var restoredTrack: LastPlayedTrack?

    nonisolated init(streamPlayer: StreamPlayer) {
        self.streamPlayer = streamPlayer
    }

    /// Seed the mini player with a saved track so it can render before the
    /// real queue is loaded. Pass `nil` to clear.
    func seedRestoredTrack(_ track: LastPlayedTrack?) {
        restoredTrack = track
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

    var isVisible: Bool {
        streamPlayer.playbackState.isActive
            || streamPlayer.currentTrack != nil
            || restoredTrack != nil
            || hasError
    }

    var trackTitle: String? {
        streamPlayer.currentTrack?.title ?? restoredTrack?.trackTitle
    }

    var albumTitle: String? {
        if let real = streamPlayer.currentTrack?.albumTitle { return real }
        // Build the same "venue — date" format as PlaylistService uses.
        guard let saved = restoredTrack, let venue = saved.venue else { return nil }
        return "\(venue) — \(saved.showDate)"
    }

    /// Extract the archive.org recording ID from the current track's stream URL.
    /// URL format: https://archive.org/download/{recordingId}/{filename}
    var artworkRecordingId: String? {
        if let url = streamPlayer.currentTrack?.url {
            let parts = url.pathComponents
            if parts.count >= 3, parts[1] == "download" {
                return parts[2]
            }
        }
        return restoredTrack?.recordingId
    }

    var artworkURL: String? {
        if let real = streamPlayer.currentTrack?.artworkURL?.absoluteString { return real }
        guard let recId = restoredTrack?.recordingId else { return nil }
        return "https://archive.org/services/img/\(recId)"
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
        if let d = streamPlayer.currentTrack?.metadata["showDate"], !d.isEmpty {
            return d
        }
        return restoredTrack?.showDate
    }

    var venue: String? {
        if let v = streamPlayer.currentTrack?.metadata["venue"], !v.isEmpty {
            return v
        }
        if let loc = streamPlayer.currentTrack?.metadata["location"], !loc.isEmpty {
            return loc
        }
        return restoredTrack?.venue ?? restoredTrack?.location
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
        // No engine queue yet — nothing to play/pause. Once the restore
        // finishes loading the queue, the user can tap again.
        guard !isSkeleton else { return }
        streamPlayer.togglePlayPause()
    }

    func skipNext() {
        guard !isSkeleton else { return }
        streamPlayer.next()
    }
}
