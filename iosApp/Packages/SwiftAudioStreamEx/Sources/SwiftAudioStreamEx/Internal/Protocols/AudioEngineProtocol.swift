import AVFoundation
import Foundation

/// Internal protocol for the audio engine, enabling testability and engine swappability.
protocol AudioEngineProtocol: AnyObject {
    func load(url: URL)
    func queue(url: URL)
    func play()
    func pause()
    func seek(to time: TimeInterval)
    func stop()

    func attachAudioNode(_ node: AVAudioNode)
    func detachAudioNode(_ node: AVAudioNode)

    var onStateChange: ((PlaybackState) -> Void)? { get set }
    var onTrackComplete: (() -> Void)? { get set }
    var onProgressUpdate: ((PlaybackProgress) -> Void)? { get set }
    var onError: ((StreamPlayerError) -> Void)? { get set }
}
