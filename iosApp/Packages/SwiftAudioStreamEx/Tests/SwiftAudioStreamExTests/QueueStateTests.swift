import Testing
@testable import SwiftAudioStreamEx

@Suite("QueueState")
struct QueueStateTests {

    @Test("empty queue state")
    func emptyState() {
        let state = QueueState.empty
        #expect(state.isEmpty == true)
        #expect(state.hasNext == false)
        #expect(state.hasPrevious == false)
        #expect(state.isFirstTrack == true)
        #expect(state.isLastTrack == false)
        #expect(state.currentIndex == 0)
        #expect(state.totalTracks == 0)
    }

    @Test("single track queue")
    func singleTrack() {
        let state = QueueState(currentIndex: 0, totalTracks: 1)
        #expect(state.isEmpty == false)
        #expect(state.hasNext == false)
        #expect(state.hasPrevious == false)
        #expect(state.isFirstTrack == true)
        #expect(state.isLastTrack == true)
    }

    @Test("middle of multi-track queue")
    func middleTrack() {
        let state = QueueState(currentIndex: 2, totalTracks: 5)
        #expect(state.hasNext == true)
        #expect(state.hasPrevious == true)
        #expect(state.isFirstTrack == false)
        #expect(state.isLastTrack == false)
    }

    @Test("first track of multi-track queue")
    func firstTrack() {
        let state = QueueState(currentIndex: 0, totalTracks: 5)
        #expect(state.hasNext == true)
        #expect(state.hasPrevious == false)
        #expect(state.isFirstTrack == true)
        #expect(state.isLastTrack == false)
    }

    @Test("last track of multi-track queue")
    func lastTrack() {
        let state = QueueState(currentIndex: 4, totalTracks: 5)
        #expect(state.hasNext == false)
        #expect(state.hasPrevious == true)
        #expect(state.isFirstTrack == false)
        #expect(state.isLastTrack == true)
    }
}
