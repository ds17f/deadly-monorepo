import Foundation
@testable import SwiftAudioStreamEx

final class MockAudioEngine: AudioEngineProtocol {
    var loadedURL: URL?
    var queuedURL: URL?
    var isPlaying = false
    var isPaused = false
    var isStopped = false
    var seekTime: TimeInterval?

    var onStateChange: ((PlaybackState) -> Void)?
    var onTrackComplete: (() -> Void)?
    var onProgressUpdate: ((PlaybackProgress) -> Void)?
    var onError: ((StreamPlayerError) -> Void)?

    func load(url: URL) {
        loadedURL = url
        isPlaying = true
        isPaused = false
        isStopped = false
        onStateChange?(.playing)
    }

    func queue(url: URL) {
        queuedURL = url
    }

    func play() {
        isPlaying = true
        isPaused = false
        onStateChange?(.playing)
    }

    func pause() {
        isPlaying = false
        isPaused = true
        onStateChange?(.paused)
    }

    func seek(to time: TimeInterval) {
        seekTime = time
    }

    func stop() {
        isPlaying = false
        isPaused = false
        isStopped = true
        onStateChange?(.idle)
    }

    // Simulate track completion for testing
    func simulateTrackComplete() {
        onTrackComplete?()
    }

    func simulateProgress(_ progress: PlaybackProgress) {
        onProgressUpdate?(progress)
    }

    func simulateError(_ error: StreamPlayerError) {
        onError?(error)
    }
}
