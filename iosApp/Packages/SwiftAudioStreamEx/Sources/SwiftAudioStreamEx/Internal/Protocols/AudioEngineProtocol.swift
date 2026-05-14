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
    /// The second parameter is the position (seconds) at the moment the
    /// engine surrendered, when available — so the StreamPlayer can land
    /// the user's manual retry at that point rather than from 0:00.
    var onError: ((StreamPlayerError, TimeInterval?) -> Void)? { get set }
}
