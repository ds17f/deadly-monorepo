import Foundation
import Testing
import GRDB
import SwiftAudioStreamEx
@testable import deadly

// MARK: - Stub

final class StubArchiveMetadataClient: ArchiveMetadataClient, @unchecked Sendable {
    var stubbedTracks: [ArchiveTrack]
    var callCount = 0

    init(tracks: [ArchiveTrack] = []) {
        self.stubbedTracks = tracks
    }

    func fetchTracks(recordingId: String) async throws -> [ArchiveTrack] {
        callCount += 1
        return stubbedTracks
    }
}

// MARK: - Tests

@MainActor
@Suite("PlaylistService Tests")
struct PlaylistServiceTests {

    let db: AppDatabase
    let showRepo: GRDBShowRepository
    let recentShowDAO: RecentShowDAO
    let stubClient: StubArchiveMetadataClient
    let streamPlayer: StreamPlayer
    let service: PlaylistServiceImpl

    init() throws {
        db = try AppDatabase.makeEmpty()
        let showDAO = ShowDAO(database: db)
        let recordingDAO = RecordingDAO(database: db)
        showRepo = GRDBShowRepository(showDAO: showDAO, recordingDAO: recordingDAO, appPreferences: AppPreferences())
        recentShowDAO = RecentShowDAO(database: db)

        stubClient = StubArchiveMetadataClient(tracks: [
            ArchiveTrack(name: "d1t01.mp3", title: "Song One",   trackNumber: 1, duration: "300", format: "VBR MP3", size: nil),
            ArchiveTrack(name: "d1t02.mp3", title: "Song Two",   trackNumber: 2, duration: "240", format: "VBR MP3", size: nil),
            ArchiveTrack(name: "d1t03.mp3", title: "Song Three", trackNumber: 3, duration: "360", format: "VBR MP3", size: nil),
        ])

        streamPlayer = StreamPlayer()

        service = PlaylistServiceImpl(
            showRepository: showRepo,
            archiveClient: stubClient,
            recentShowDAO: recentShowDAO,
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

    @Test("recordRecentPlay upserts into recent_shows")
    func recordRecentPlayUpserts() async throws {
        let showId = "1977-05-08"
        let recordingId = "gd77-05-08.sbd.hicks.4982.sbeok.shnf"
        try insertShow(showId, bestRecordingId: recordingId)
        try insertRecording(identifier: recordingId, showId: showId)

        await service.loadShow(showId)
        service.recordRecentPlay()

        let recent = try recentShowDAO.fetchById(showId)
        #expect(recent != nil)
        #expect(recent?.totalPlayCount == 1)
    }
}
