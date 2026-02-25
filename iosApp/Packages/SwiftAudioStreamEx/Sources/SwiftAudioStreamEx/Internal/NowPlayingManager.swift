import Foundation
import MediaPlayer
import os
#if canImport(UIKit)
import UIKit
#endif

/// Archive.org auto-generates waveform spectrograms (180x45, 4:1 aspect) for audio
/// items that lack real artwork. Detect by pixel dimensions to use fallback instead.
#if canImport(UIKit)
private func isWaveform(_ image: UIImage) -> Bool {
    let w = image.size.width * image.scale
    let h = image.size.height * image.scale
    guard h > 0 else { return false }
    return h <= 50 || w / h > 3
}
#endif

/// Updates MPNowPlayingInfoCenter with current track metadata and progress.
@MainActor
final class NowPlayingManager {
    private let logger = Logger(subsystem: "SwiftAudioStreamEx", category: "NowPlaying")
    private var artworkTask: Task<Void, Never>?
    private var currentArtwork: MPMediaItemArtwork?
    private var fallbackArtwork: MPMediaItemArtwork?

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

    /// Set a fallback image (e.g., app logo) to use when artwork is unavailable or invalid.
    func setFallbackImage(_ image: Any?) {
        #if canImport(UIKit)
        guard let uiImage = image as? UIImage else {
            fallbackArtwork = nil
            return
        }
        fallbackArtwork = MPMediaItemArtwork(boundsSize: uiImage.size) { @Sendable _ in uiImage }
        #endif
    }

    func loadArtwork(from url: URL?) {
        artworkTask?.cancel()
        currentArtwork = nil

        // If no URL, use fallback immediately
        guard let url else {
            useFallbackArtwork()
            return
        }

        artworkTask = Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                guard !Task.isCancelled else { return }

                #if canImport(UIKit)
                guard let image = UIImage(data: data) else {
                    useFallbackArtwork()
                    return
                }

                // Detect waveform spectrograms and use fallback instead
                if isWaveform(image) {
                    logger.debug("Detected waveform spectrogram, using fallback artwork")
                    useFallbackArtwork()
                    return
                }

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
                    useFallbackArtwork()
                }
            }
        }
    }

    private func useFallbackArtwork() {
        guard let fallback = fallbackArtwork else { return }
        currentArtwork = fallback
        if var info = MPNowPlayingInfoCenter.default().nowPlayingInfo {
            info[MPMediaItemPropertyArtwork] = fallback
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
        logger.debug("Using fallback artwork for Now Playing")
    }

    func clear() {
        artworkTask?.cancel()
        currentArtwork = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
