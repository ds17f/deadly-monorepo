import AudioStreaming
import Testing
@testable import SwiftAudioStreamEx

@Suite("Playback network failure classifier")
struct PlaybackNetworkFailureClassifierTests {
    @Test("AudioStreaming serverError maps to CDN recovery")
    func serverError() {
        let error = AudioPlayerError.networkError(.serverError)
        #expect(PlaybackNetworkFailureClassifier.classify(
            errorDescription: String(describing: error)
        ) == .cdnServer)
    }

    @Test("Other AudioStreaming networkError maps to connectivity retry")
    func networkError() {
        let error = AudioPlayerError.networkError(.missingData)
        #expect(PlaybackNetworkFailureClassifier.classify(
            errorDescription: String(describing: error)
        ) == .connectivity)
    }

    @Test("Unrelated player errors are not network failures")
    func unrelatedError() {
        #expect(PlaybackNetworkFailureClassifier.classify(
            errorDescription: "playerError(AudioStreaming.DecoderError.invalidFormat)"
        ) == nil)
    }
}
