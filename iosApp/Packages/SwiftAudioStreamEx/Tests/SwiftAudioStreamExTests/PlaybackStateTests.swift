import Testing
@testable import SwiftAudioStreamEx

@Suite("PlaybackState")
struct PlaybackStateTests {

    @Test("isPlaying returns true only for .playing")
    func isPlaying() {
        #expect(PlaybackState.playing.isPlaying == true)
        #expect(PlaybackState.paused.isPlaying == false)
        #expect(PlaybackState.idle.isPlaying == false)
        #expect(PlaybackState.loading.isPlaying == false)
        #expect(PlaybackState.buffering.isPlaying == false)
        #expect(PlaybackState.ended.isPlaying == false)
        #expect(PlaybackState.error(.unknown("test")).isPlaying == false)
    }

    @Test("isPaused returns true only for .paused")
    func isPaused() {
        #expect(PlaybackState.paused.isPaused == true)
        #expect(PlaybackState.playing.isPaused == false)
        #expect(PlaybackState.idle.isPaused == false)
    }

    @Test("isActive returns true for active states")
    func isActive() {
        #expect(PlaybackState.playing.isActive == true)
        #expect(PlaybackState.paused.isActive == true)
        #expect(PlaybackState.buffering.isActive == true)
        #expect(PlaybackState.loading.isActive == true)

        #expect(PlaybackState.idle.isActive == false)
        #expect(PlaybackState.ended.isActive == false)
        #expect(PlaybackState.error(.unknown("test")).isActive == false)
    }

    @Test("isIdle returns true only for .idle")
    func isIdle() {
        #expect(PlaybackState.idle.isIdle == true)
        #expect(PlaybackState.playing.isIdle == false)
    }

    @Test("equality works for error cases")
    func errorEquality() {
        let error1 = PlaybackState.error(.unknown("a"))
        let error2 = PlaybackState.error(.unknown("a"))
        let error3 = PlaybackState.error(.unknown("b"))

        #expect(error1 == error2)
        #expect(error1 != error3)
    }
}
