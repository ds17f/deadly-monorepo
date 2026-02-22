import Foundation

@Observable
@MainActor
final class PanelContentService {
    private(set) var lyrics: String?
    private(set) var venueInfo: String?
    private(set) var credits: [LineupMember]?
    private(set) var isLoading: Bool = false

    private var lastLoadedShowId: String?
    private var lastLoadedSongTitle: String?

    private let geniusService: any GeniusService
    private let wikipediaService: any WikipediaService

    init(geniusService: some GeniusService, wikipediaService: some WikipediaService) {
        self.geniusService = geniusService
        self.wikipediaService = wikipediaService
    }

    func loadContent(show: Show?, songTitle: String?) async {
        let showId = show?.id
        let scrubbedSong = songTitle.map { SongTitleScrubber.scrub($0) } ?? ""

        guard showId != lastLoadedShowId || scrubbedSong != lastLoadedSongTitle else { return }
        lastLoadedShowId = showId
        lastLoadedSongTitle = scrubbedSong

        // Credits come straight from the model — no network needed.
        credits = show?.lineup?.members

        guard let show else {
            lyrics = nil
            venueInfo = nil
            return
        }

        isLoading = true

        let genius = geniusService
        let wikipedia = wikipediaService
        let songToLoad = scrubbedSong
        let venueName = show.venue.name
        let venueCity = show.venue.city

        // Both services guard against empty input and return nil — safe to always call.
        async let lyricsResult: String? = genius.getLyrics(songTitle: songToLoad, artist: "Grateful Dead")
        async let venueResult: String? = wikipedia.getVenueSummary(venueName: venueName, city: venueCity)

        let (l, v) = await (lyricsResult, venueResult)
        lyrics = l
        venueInfo = v
        isLoading = false
    }
}
