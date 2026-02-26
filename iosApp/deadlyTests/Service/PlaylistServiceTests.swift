import Foundation
import Testing
import GRDB
import SwiftAudioStreamEx
@testable import deadly

// MARK: - Stubs

final class StubArchiveMetadataClient: ArchiveMetadataClient, @unchecked Sendable {
    var stubbedTracks: [ArchiveTrack]
    var stubbedReviews: [Review] = []
    var callCount = 0
    var reviewCallCount = 0

    init(tracks: [ArchiveTrack] = []) {
        self.stubbedTracks = tracks
    }

    func fetchTracks(recordingId: String) async throws -> [ArchiveTrack] {
        callCount += 1
        return stubbedTracks
    }

    func fetchReviews(recordingId: String) async throws -> [Review] {
        reviewCallCount += 1
        return stubbedReviews
    }

    func clearCache(recordingId: String) {}
}

@MainActor
final class StubRecentShowsService: RecentShowsService {
    var recentShows: [Show] = []
    var recordedShowIds: [String] = []

    var recentShowsStream: AsyncStream<[Show]> {
        AsyncStream { continuation in
            continuation.yield(self.recentShows)
        }
    }

    func recordShowPlay(showId: String) {
        recordedShowIds.append(showId)
    }

    func getRecentShows(limit: Int) async -> [Show] {
        return Array(recentShows.prefix(limit))
    }

    func isShowInRecent(showId: String) async -> Bool {
        return recentShows.contains { $0.id == showId }
    }

    func removeShow(showId: String) async {
        recentShows.removeAll { $0.id == showId }
    }

    func clearRecentShows() async {
        recentShows.removeAll()
    }

    func startObservingPlayback() {}
    func stopObservingPlayback() {}
}

// MARK: - Tests

@MainActor
@Suite("PlaylistService Tests")
struct PlaylistServiceTests {

    let db: AppDatabase
    let showRepo: GRDBShowRepository
    let stubRecentShowsService: StubRecentShowsService
    let stubClient: StubArchiveMetadataClient
    let streamPlayer: StreamPlayer
    let service: PlaylistServiceImpl

    init() throws {
        db = try AppDatabase.makeEmpty()
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        showRepo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: AppPreferences())
        stubRecentShowsService = StubRecentShowsService()

        stubClient = StubArchiveMetadataClient(tracks: [
            ArchiveTrack(name: "d1t01.mp3", title: "Song One",   trackNumber: 1, duration: "300", format: "VBR MP3", size: nil),
            ArchiveTrack(name: "d1t02.mp3", title: "Song Two",   trackNumber: 2, duration: "240", format: "VBR MP3", size: nil),
            ArchiveTrack(name: "d1t03.mp3", title: "Song Three", trackNumber: 3, duration: "360", format: "VBR MP3", size: nil),
        ])

        streamPlayer = StreamPlayer()

        service = PlaylistServiceImpl(
            showRepository: showRepo,
            archiveClient: stubClient,
            recentShowsService: stubRecentShowsService,
            libraryDAO: LibraryDAO(database: db),
            streamPlayer: streamPlayer
        )
    }

    // MARK: - Fixture helpers

    private func insertShow(_ showId: String, bestRecordingId: String?) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let yearMonth = String(showId.prefix(7))
        try ShowDAO(database: db).insert(ShowRecord(
            showId: showId,
            date: showId,
            year: 1977,
            month: 5,
            yearMonth: yearMonth,
            band: "Grateful Dead",
            url: nil,
            venueName: "Barton Hall",
            city: "Ithaca",
            state: "NY",
            country: "USA",
            locationRaw: nil,
            setlistStatus: nil,
            setlistRaw: nil,
            songList: nil,
            lineupStatus: nil,
            lineupRaw: nil,
            memberList: nil,
            showSequence: 1,
            recordingsRaw: nil,
            recordingCount: bestRecordingId != nil ? 1 : 0,
            bestRecordingId: bestRecordingId,
            averageRating: nil,
            totalReviews: 0,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: nil,
            createdAt: now,
            updatedAt: now
        ))
    }

    private func insertRecording(identifier: String, showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try RecordingDAO(database: db).insert(RecordingRecord(
            identifier: identifier,
            showId: showId,
            sourceType: "SBD",
            rating: 4.5,
            rawRating: 4.5,
            reviewCount: 100,
            confidence: 0.9,
            highRatings: 90,
            lowRatings: 10,
            taper: "Hicks",
            source: nil,
            lineage: nil,
            sourceTypeString: nil,
            collectionTimestamp: now
        ))
    }

    // MARK: - Tests

    @Test("loadShow populates show, recording, and tracks")
    func loadShowPopulatesAll() async throws {
        let showId = "1977-05-08"
        let recordingId = "gd77-05-08.sbd.hicks.4982.sbeok.shnf"
        try insertShow(showId, bestRecordingId: recordingId)
        try insertRecording(identifier: recordingId, showId: showId)

        await service.loadShow(showId)

        #expect(service.currentShow?.id == showId)
        #expect(service.currentRecording?.identifier == recordingId)
        #expect(service.tracks.count == 3)
        #expect(service.tracks[0].title == "Song One")
        #expect(stubClient.callCount == 1)
    }

    @Test("loadShow with unknown id leaves show nil")
    func loadShowUnknownId() async throws {
        await service.loadShow("nonexistent-show-id")
        #expect(service.currentShow == nil)
        #expect(service.currentRecording == nil)
        #expect(service.tracks.isEmpty)
    }

    @Test("playTrack loads StreamPlayer queue at correct index")
    func playTrackLoadsQueue() async throws {
        let showId = "1977-05-08"
        let recordingId = "gd77-05-08.sbd.hicks.4982.sbeok.shnf"
        try insertShow(showId, bestRecordingId: recordingId)
        try insertRecording(identifier: recordingId, showId: showId)

        await service.loadShow(showId)
        service.playTrack(at: 1)

        #expect(streamPlayer.currentTrack?.title == "Song Two")
    }

    @Test("recordRecentPlay delegates to RecentShowsService")
    func recordRecentPlayDelegates() async throws {
        let showId = "1977-05-08"
        let recordingId = "gd77-05-08.sbd.hicks.4982.sbeok.shnf"
        try insertShow(showId, bestRecordingId: recordingId)
        try insertRecording(identifier: recordingId, showId: showId)

        await service.loadShow(showId)
        service.recordRecentPlay()

        #expect(stubRecentShowsService.recordedShowIds.contains(showId))
    }

    @Test("loadReviews populates reviews from stub")
    func loadReviewsPopulates() async throws {
        let showId = "1977-05-08"
        let recordingId = "gd77-05-08.sbd.hicks.4982.sbeok.shnf"
        try insertShow(showId, bestRecordingId: recordingId)
        try insertRecording(identifier: recordingId, showId: showId)

        stubClient.stubbedReviews = [
            Review(reviewer: "deadhead1", title: "Great show", body: "Amazing performance", rating: 5, reviewDate: "2023-05-15 12:00:00"),
            Review(reviewer: "deadhead2", title: nil, body: "Good stuff", rating: 4, reviewDate: nil),
        ]

        await service.loadShow(showId)
        await service.loadReviews()

        #expect(service.reviews.count == 2)
        #expect(service.reviews[0].reviewer == "deadhead1")
        #expect(service.reviews[1].rating == 4)
        #expect(stubClient.reviewCallCount == 1)
    }

    @Test("selectRecording clears stale reviews")
    func selectRecordingClearsReviews() async throws {
        let showId = "1977-05-08"
        let recordingId = "gd77-05-08.sbd.hicks.4982.sbeok.shnf"
        try insertShow(showId, bestRecordingId: recordingId)
        try insertRecording(identifier: recordingId, showId: showId)

        stubClient.stubbedReviews = [
            Review(reviewer: "user1", rating: 5),
        ]

        await service.loadShow(showId)
        await service.loadReviews()
        #expect(service.reviews.count == 1)

        // Selecting the same recording should clear reviews
        await service.selectRecording(service.currentRecording!)
        #expect(service.reviews.isEmpty)
    }
}

// MARK: - parseReviews Tests

@Suite("ArchiveMetadataClient.parseReviews Tests")
struct ParseReviewsTests {

    @Test("parseReviews extracts valid reviews from JSON")
    func parseValidReviews() throws {
        let json: [String: Any] = [
            "reviews": [
                [
                    "reviewer": "deadhead1",
                    "reviewtitle": "Great show",
                    "reviewbody": "Amazing performance",
                    "stars": "5",
                    "reviewdate": "2023-05-15 12:00:00"
                ],
                [
                    "reviewer": "deadhead2",
                    "reviewtitle": "Pretty good",
                    "reviewbody": "Solid second set",
                    "stars": 4,
                    "reviewdate": "2023-06-01 10:00:00"
                ]
            ]
        ]
        let data = try JSONSerialization.data(withJSONObject: json)
        let reviews = URLSessionArchiveMetadataClient.parseReviews(from: data)

        #expect(reviews.count == 2)
        #expect(reviews[0].reviewer == "deadhead1")
        #expect(reviews[0].title == "Great show")
        #expect(reviews[0].body == "Amazing performance")
        #expect(reviews[0].rating == 5)
        #expect(reviews[0].reviewDate == "2023-05-15 12:00:00")
        #expect(reviews[1].rating == 4)
    }

    @Test("parseReviews returns empty when reviews key is missing")
    func parseMissingReviewsKey() throws {
        let json: [String: Any] = ["files": []]
        let data = try JSONSerialization.data(withJSONObject: json)
        let reviews = URLSessionArchiveMetadataClient.parseReviews(from: data)

        #expect(reviews.isEmpty)
    }

    @Test("parseReviews handles partial data gracefully")
    func parsePartialData() throws {
        let json: [String: Any] = [
            "reviews": [
                ["reviewer": "user1"],
                ["stars": "3", "reviewbody": "Decent show"]
            ]
        ]
        let data = try JSONSerialization.data(withJSONObject: json)
        let reviews = URLSessionArchiveMetadataClient.parseReviews(from: data)

        #expect(reviews.count == 2)
        #expect(reviews[0].reviewer == "user1")
        #expect(reviews[0].rating == nil)
        #expect(reviews[1].reviewer == nil)
        #expect(reviews[1].rating == 3)
        #expect(reviews[1].body == "Decent show")
    }

    @Test("parseReviews returns empty for empty reviews array")
    func parseEmptyArray() throws {
        let json: [String: Any] = ["reviews": []]
        let data = try JSONSerialization.data(withJSONObject: json)
        let reviews = URLSessionArchiveMetadataClient.parseReviews(from: data)

        #expect(reviews.isEmpty)
    }
}
