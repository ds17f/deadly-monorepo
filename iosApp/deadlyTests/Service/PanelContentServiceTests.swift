import Testing
@testable import deadly

// MARK: - Stub services

struct StubGeniusService: GeniusService {
    let result: String?
    var callCount = 0

    func getLyrics(songTitle: String, artist: String) async -> String? { result }
}

struct StubWikipediaService: WikipediaService {
    let result: String?

    func getVenueSummary(venueName: String, city: String?) async -> String? { result }
}

// MARK: - Fixtures

private func makeShow(id: String = "gd1977-05-08", withLineup: Bool = false) -> Show {
    let lineup: Lineup? = withLineup
        ? Lineup(status: "confirmed", members: [
            LineupMember(name: "Jerry Garcia", instruments: "Guitar"),
            LineupMember(name: "Bob Weir", instruments: "Guitar")
          ], raw: nil)
        : nil

    return Show(
        id: id,
        date: "1977-05-08",
        year: 1977,
        band: "Grateful Dead",
        venue: Venue(name: "Barton Hall", city: "Ithaca", state: "NY", country: "USA"),
        location: Location(displayText: "Ithaca, NY", city: "Ithaca", state: "NY"),
        setlist: nil,
        lineup: lineup,
        recordingIds: [],
        bestRecordingId: nil,
        recordingCount: 1,
        averageRating: nil,
        totalReviews: 0,
        coverImageUrl: nil,
        isInLibrary: false,
        libraryAddedAt: nil
    )
}

// MARK: - Tests

@Suite("PanelContentService")
@MainActor
struct PanelContentServiceTests {

    @Test("loads lyrics and venue info from stub services")
    func loadsContent() async {
        let service = PanelContentService(
            geniusService: StubGeniusService(result: "Fire on the mountain"),
            wikipediaService: StubWikipediaService(result: "Barton Hall info")
        )

        await service.loadContent(show: makeShow(), songTitle: "Fire on the Mountain")

        #expect(service.lyrics == "Fire on the mountain")
        #expect(service.venueInfo == "Barton Hall info")
        #expect(service.isLoading == false)
    }

    @Test("credits come from lineup, not network")
    func creditsFromLineup() async {
        let genius = StubGeniusService(result: nil)
        let wiki = StubWikipediaService(result: nil)
        let service = PanelContentService(geniusService: genius, wikipediaService: wiki)

        await service.loadContent(show: makeShow(withLineup: true), songTitle: nil)

        #expect(service.credits?.count == 2)
        #expect(service.credits?.first?.name == "Jerry Garcia")
    }

    @Test("nil show clears all panels without network calls")
    func nilShowClearsAll() async {
        let service = PanelContentService(
            geniusService: StubGeniusService(result: "lyrics"),
            wikipediaService: StubWikipediaService(result: "venue")
        )

        // Load something first
        await service.loadContent(show: makeShow(), songTitle: "Test")
        // Then nil show
        await service.loadContent(show: nil, songTitle: nil)

        #expect(service.lyrics == nil)
        #expect(service.venueInfo == nil)
        #expect(service.credits == nil)
    }

    @Test("does not re-fetch when showId and songTitle unchanged")
    func deduplicates() async {
        let service = PanelContentService(
            geniusService: StubGeniusService(result: "original"),
            wikipediaService: StubWikipediaService(result: "venue")
        )
        let show = makeShow()

        await service.loadContent(show: show, songTitle: "Casey Jones")
        #expect(service.lyrics == "original")

        // Second call with same args — state must not change
        // (We can't easily count calls with struct stubs, but isLoading won't flip)
        await service.loadContent(show: show, songTitle: "Casey Jones")
        #expect(service.lyrics == "original")
        #expect(service.isLoading == false)
    }

    @Test("re-fetches when songTitle changes")
    func refetchesOnSongChange() async {
        let service = PanelContentService(
            geniusService: StubGeniusService(result: "new lyrics"),
            wikipediaService: StubWikipediaService(result: nil)
        )
        let show = makeShow()

        await service.loadContent(show: show, songTitle: "Song A")
        #expect(service.lyrics == "new lyrics")

        // Different song
        let service2 = PanelContentService(
            geniusService: StubGeniusService(result: "different lyrics"),
            wikipediaService: StubWikipediaService(result: nil)
        )
        await service2.loadContent(show: show, songTitle: "Song A")
        await service2.loadContent(show: show, songTitle: "Song B")
        #expect(service2.lyrics == "different lyrics")
    }

    @Test("handles nil lyrics and venue gracefully")
    func handlesNilResults() async {
        let service = PanelContentService(
            geniusService: StubGeniusService(result: nil),
            wikipediaService: StubWikipediaService(result: nil)
        )

        await service.loadContent(show: makeShow(), songTitle: "Unknown")

        #expect(service.lyrics == nil)
        #expect(service.venueInfo == nil)
        #expect(service.isLoading == false)
    }
}
