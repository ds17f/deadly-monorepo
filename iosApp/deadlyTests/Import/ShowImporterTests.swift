import Foundation
import Testing
@testable import deadly

@Suite("ShowImporter Tests")
struct ShowImporterTests {

    // MARK: - Fixtures

    private func makeShowData(
        showId: String = "gd1977-05-08",
        date: String = "1977-05-08",
        venue: String = "Barton Hall",
        city: String? = "Ithaca",
        state: String? = "NY",
        country: String? = "USA",
        locationRaw: String? = "Ithaca, NY",
        avgRating: Double = 4.8,
        totalHighRatings: Int = 100,
        totalLowRatings: Int = 5,
        recordingCount: Int = 3,
        recordings: [String] = ["gd77-05-08.sbd.hicks.4982.sbeok.shnf"],
        bestRecording: String? = "gd77-05-08.sbd.hicks.4982.sbeok.shnf",
        sourceTypes: [String: Int] = ["SBD": 2, "AUD": 1],
        setlistJson: String? = nil,
        lineupJson: String? = nil
    ) -> ShowImportData {
        // Build JSON and decode, since ShowImportData.init(from:) is the only initializer
        var dict: [String: Any] = [
            "show_id": showId,
            "band": "Grateful Dead",
            "venue": venue,
            "date": date,
            "avg_rating": avgRating,
            "total_high_ratings": totalHighRatings,
            "total_low_ratings": totalLowRatings,
            "recording_count": recordingCount,
            "recordings": recordings,
            "source_types": sourceTypes,
        ]
        if let city { dict["city"] = city }
        if let state { dict["state"] = state }
        if let country { dict["country"] = country }
        if let lr = locationRaw { dict["location_raw"] = lr }
        if let br = bestRecording { dict["best_recording"] = br }
        if let s = setlistJson, let data = s.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data) {
            dict["setlist"] = obj
        }
        if let l = lineupJson, let data = l.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data) {
            dict["lineup"] = obj
        }
        let data = try! JSONSerialization.data(withJSONObject: dict)
        return try! JSONDecoder().decode(ShowImportData.self, from: data)
    }

    // MARK: - makeRecord tests

    @Test("makeRecord produces correct ShowRecord fields")
    func testMakeRecordBasicFields() {
        let show = makeShowData()
        let record = ShowImporter.makeRecord(from: show, now: 1000)

        #expect(record.showId == "gd1977-05-08")
        #expect(record.date == "1977-05-08")
        #expect(record.year == 1977)
        #expect(record.month == 5)
        #expect(record.yearMonth == "1977-05")
        #expect(record.band == "Grateful Dead")
        #expect(record.venueName == "Barton Hall")
        #expect(record.city == "Ithaca")
        #expect(record.state == "NY")
        #expect(record.country == "USA")
        #expect(record.locationRaw == "Ithaca, NY")
        #expect(record.averageRating == 4.8)
        #expect(record.totalReviews == 105)
        #expect(record.recordingCount == 3)
        #expect(record.bestRecordingId == "gd77-05-08.sbd.hicks.4982.sbeok.shnf")
        #expect(record.isInLibrary == false)
        #expect(record.createdAt == 1000)
        #expect(record.updatedAt == 1000)
    }

    @Test("makeRecord encodes recordings array as JSON")
    func testMakeRecordRecordingsRaw() {
        let show = makeShowData(recordings: ["rec1", "rec2"])
        let record = ShowImporter.makeRecord(from: show)

        let raw = record.recordingsRaw
        #expect(raw != nil)
        let decoded = try? JSONSerialization.jsonObject(with: raw!.data(using: .utf8)!) as? [String]
        #expect(decoded == ["rec1", "rec2"])
    }

    @Test("makeRecord treats avgRating 0 as nil")
    func testMakeRecordZeroRating() {
        let show = makeShowData(avgRating: 0.0)
        let record = ShowImporter.makeRecord(from: show)
        #expect(record.averageRating == nil)
    }

    @Test("makeRecord extracts song list from setlist JSON")
    func testMakeRecordSongList() {
        let setlistJson = #"[{"set": 1, "songs": [{"name": "Scarlet Begonias"}, {"name": "Fire on the Mountain"}]}]"#
        let show = makeShowData(setlistJson: setlistJson)
        let record = ShowImporter.makeRecord(from: show)

        #expect(record.setlistRaw != nil)
        #expect(record.songList?.contains("Scarlet Begonias") == true)
        #expect(record.songList?.contains("Fire on the Mountain") == true)
    }

    @Test("makeRecord encodes lineup as JSON and extracts member list")
    func testMakeRecordLineup() {
        let lineupJson = #"[{"name": "Jerry Garcia", "instruments": "Guitar"}, {"name": "Bob Weir", "instruments": "Guitar"}]"#
        let show = makeShowData(lineupJson: lineupJson)
        let record = ShowImporter.makeRecord(from: show)

        #expect(record.lineupRaw != nil)
        #expect(record.memberList?.contains("Jerry Garcia") == true)
        #expect(record.memberList?.contains("Bob Weir") == true)
    }

    @Test("makeRecord resolves cover image from ticket front")
    func testMakeRecordCoverImage() {
        var dict: [String: Any] = [
            "show_id": "test", "band": "GD", "venue": "V", "date": "1970-01-01",
            "ticket_images": [
                ["url": "http://example.com/back.jpg", "side": "back"],
                ["url": "http://example.com/front.jpg", "side": "front"],
            ],
            "photos": [["url": "http://example.com/photo.jpg"]],
        ]
        let data = try! JSONSerialization.data(withJSONObject: dict)
        let show = try! JSONDecoder().decode(ShowImportData.self, from: data)
        let record = ShowImporter.makeRecord(from: show)
        #expect(record.coverImageUrl == "http://example.com/front.jpg")
    }

    // MARK: - makeSearchRecord / buildSearchText tests

    @Test("buildSearchText contains primary date")
    func testSearchTextContainsDate() {
        let show = makeShowData(date: "1977-05-08")
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("1977-05-08"))
    }

    @Test("buildSearchText contains short date variations")
    func testSearchTextDateVariations() {
        let show = makeShowData(date: "1977-05-08")
        let text = ShowImporter.buildSearchText(from: show)

        #expect(text.contains("77"))       // year short
        #expect(text.contains("1977"))     // year full
        #expect(text.contains("197"))      // decade prefix
        #expect(text.contains("5-8-77"))   // M-D-YY
        #expect(text.contains("5/8/77"))   // M/D/YY
        #expect(text.contains("5.8.77"))   // M.D.YY
    }

    @Test("buildSearchText contains venue and location")
    func testSearchTextLocationFields() {
        let show = makeShowData(venue: "Barton Hall", locationRaw: "Ithaca, NY")
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("Barton Hall"))
        #expect(text.contains("Ithaca, NY"))
    }

    @Test("buildSearchText contains member names from lineup")
    func testSearchTextMembers() {
        let lineupJson = #"[{"name": "Jerry Garcia", "instruments": "Guitar"}]"#
        let show = makeShowData(lineupJson: lineupJson)
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("Jerry Garcia"))
    }

    @Test("buildSearchText contains songs from setlist")
    func testSearchTextSongs() {
        let setlistJson = #"[{"set": 1, "songs": [{"name": "Dark Star"}]}]"#
        let show = makeShowData(setlistJson: setlistJson)
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("Dark Star"))
    }

    @Test("buildSearchText includes SBD source tag")
    func testSearchTextSourceTagSBD() {
        let show = makeShowData(sourceTypes: ["SBD": 1])
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("soundboard"))
        #expect(text.contains("sbd"))
    }

    @Test("buildSearchText includes AUD source tag")
    func testSearchTextSourceTagAUD() {
        let show = makeShowData(sourceTypes: ["AUD": 1])
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("audience"))
        #expect(text.contains("aud"))
    }

    @Test("buildSearchText includes top-rated tag when criteria met")
    func testSearchTextTopRated() {
        let show = makeShowData(avgRating: 4.5, totalHighRatings: 10, totalLowRatings: 2)
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("top-rated"))
    }

    @Test("buildSearchText does not include top-rated tag when below threshold")
    func testSearchTextNotTopRated() {
        let show = makeShowData(avgRating: 3.9, totalHighRatings: 2, totalLowRatings: 1)
        let text = ShowImporter.buildSearchText(from: show)
        #expect(!text.contains("top-rated"))
    }

    @Test("buildSearchText includes popular tag when review count >= 50")
    func testSearchTextPopular() {
        let show = makeShowData(totalHighRatings: 45, totalLowRatings: 10)
        let text = ShowImporter.buildSearchText(from: show)
        #expect(text.contains("popular"))
    }

    @Test("makeSearchRecord produces correct showId")
    func testMakeSearchRecord() {
        let show = makeShowData(showId: "gd1977-05-08")
        let record = ShowImporter.makeSearchRecord(from: show)
        #expect(record.showId == "gd1977-05-08")
        #expect(!record.searchText.isEmpty)
    }
}
