import Testing
import Foundation
@testable import SwiftAudioStreamEx

@Suite("StreamPlayer")
struct StreamPlayerTests {

    private func makeTestTracks(count: Int = 3) -> [TrackItem] {
        (0..<count).map { i in
            TrackItem(
                url: URL(string: "https://example.com/track\(i).mp3")!,
                title: "Track \(i)",
                artist: "Artist",
                albumTitle: "Album"
            )
        }
    }

    @Test("initial state is idle with no track")
    @MainActor
    func initialState() {
        let player = StreamPlayer()
        #expect(player.playbackState == .idle)
        #expect(player.currentTrack == nil)
        #expect(player.progress == .zero)
        #expect(player.queueState == .empty)
    }

    @Test("TrackItem has unique IDs")
    func trackItemIdentity() {
        let url = URL(string: "https://example.com/track.mp3")!
        let a = TrackItem(url: url, title: "T", artist: "A")
        let b = TrackItem(url: url, title: "T", artist: "A")
        #expect(a != b)  // Different UUIDs
        #expect(a.id != b.id)
    }

    @Test("TrackItem metadata escape hatch")
    func trackMetadata() {
        let track = TrackItem(
            url: URL(string: "https://example.com/track.mp3")!,
            title: "Dark Star",
            artist: "Grateful Dead",
            metadata: ["showId": "abc123", "venue": "Barton Hall"]
        )
        #expect(track.metadata["showId"] == "abc123")
        #expect(track.metadata["venue"] == "Barton Hall")
    }

    @Test("StreamPlayerError descriptions")
    func errorDescriptions() {
        let url = URL(string: "https://example.com/track.mp3")!

        let e1 = StreamPlayerError.trackLoadFailed(url: url, reason: "404")
        #expect(e1.localizedDescription.contains("track.mp3"))

        let e2 = StreamPlayerError.networkError("timeout")
        #expect(e2.localizedDescription.contains("timeout"))

        let e3 = StreamPlayerError.invalidQueueIndex(99)
        #expect(e3.localizedDescription.contains("99"))
    }
}
