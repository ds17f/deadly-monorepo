import Testing
@testable import SwiftAudioStreamEx

@Suite("PlaybackProgress")
struct PlaybackProgressTests {

    @Test("zero progress")
    func zeroProgress() {
        let p = PlaybackProgress.zero
        #expect(p.currentTime == 0)
        #expect(p.duration == 0)
        #expect(p.progress == 0)
        #expect(p.remaining == 0)
    }

    @Test("progress fraction calculation")
    func progressFraction() {
        let p = PlaybackProgress(currentTime: 30, duration: 120)
        #expect(p.progress == 0.25)
    }

    @Test("remaining time calculation")
    func remainingTime() {
        let p = PlaybackProgress(currentTime: 30, duration: 120)
        #expect(p.remaining == 90)
    }

    @Test("progress clamped to 1.0 when currentTime exceeds duration")
    func progressClamped() {
        let p = PlaybackProgress(currentTime: 150, duration: 120)
        #expect(p.progress == 1.0)
    }

    @Test("progress is 0 when duration is 0")
    func zeroDuration() {
        let p = PlaybackProgress(currentTime: 30, duration: 0)
        #expect(p.progress == 0)
    }

    @Test("remaining is 0 when past duration")
    func remainingPastDuration() {
        let p = PlaybackProgress(currentTime: 150, duration: 120)
        #expect(p.remaining == 0)
    }
}
