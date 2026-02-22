import Foundation

/// Core playback service protocol. Feature modules depend on this, never on implementations.
/// Implementation: PlayerServiceImpl (DEAD-19)
protocol PlayerService {
    var playbackState: PlaybackState { get }
    var currentTrackTitle: String? { get }
    var progress: Double { get }  // 0.0–1.0
    var duration: TimeInterval { get }
    var currentTime: TimeInterval { get }

    func play()
    func pause()
    func togglePlayPause()
    func stop()
    func seek(to time: TimeInterval)
    func next()
    func previous()
}
