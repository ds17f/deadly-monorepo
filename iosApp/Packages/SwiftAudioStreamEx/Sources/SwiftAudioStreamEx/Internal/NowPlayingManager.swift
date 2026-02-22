import Foundation
import MediaPlayer
import os
#if canImport(UIKit)
import UIKit
#endif

/// Updates MPNowPlayingInfoCenter with current track metadata and progress.
@MainActor
final class NowPlayingManager {
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "NowPlaying")
    private var artworkTask: Task<Void, Never>?
    private var currentArtwork: MPMediaItemArtwork?

    func update(
        track: TrackItem?,
        progress: PlaybackProgress,
        isPlaying: Bool,
        queueIndex: Int,
        queueCount: Int
    ) {
        guard let track else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: progress.currentTime,
            MPMediaItemPropertyPlaybackDuration: progress.duration,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: queueIndex,
            MPNowPlayingInfoPropertyPlaybackQueueCount: queueCount,
        ]

        if let albumTitle = track.albumTitle {
            info[MPMediaItemPropertyAlbumTitle] = albumTitle
        }

        if let duration = track.duration, progress.duration <= 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }

        if let artwork = currentArtwork {
            info[MPMediaItemPropertyArtwork] = artwork
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func updateElapsedTime(_ time: TimeInterval, isPlaying: Bool) {
        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else { return }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = time
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func loadArtwork(from url: URL?) {
        artworkTask?.cancel()
        currentArtwork = nil
        guard let url else { return }

        artworkTask = Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                guard !Task.isCancelled else { return }

                #if canImport(UIKit)
                guard let image = UIImage(data: data) else { return }
                let artworkImage = image
                let artwork = MPMediaItemArtwork(boundsSize: artworkImage.size) { @Sendable _ in artworkImage }
                currentArtwork = artwork
                // Re-push so artwork appears immediately in Control Center
                if var info = MPNowPlayingInfoCenter.default().nowPlayingInfo {
                    info[MPMediaItemPropertyArtwork] = artwork
                    MPNowPlayingInfoCenter.default().nowPlayingInfo = info
                }
                logger.debug("Artwork loaded for current track")
                #endif
            } catch {
                if !Task.isCancelled {
                    logger.warning("Failed to load artwork: \(error.localizedDescription)")
                }
            }
        }
    }

    func clear() {
        artworkTask?.cancel()
        currentArtwork = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
