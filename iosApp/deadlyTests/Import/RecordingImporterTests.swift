import Foundation
import Testing
@testable import deadly

@Suite("RecordingImporter Tests")
struct RecordingImporterTests {

    // MARK: - Fixtures

    private func makeRecordingData(
        rating: Double = 4.2,
        reviewCount: Int = 30,
        sourceType: String? = "SBD",
        confidence: Double = 0.9,
        rawRating: Double = 4.1,
        highRatings: Int = 28,
        lowRatings: Int = 2,
        taper: String? = "Hicks",
        source: String? = "Soundboard",
        lineage: String? = "Sony ECM > DAT"
    ) -> RecordingImportData {
        var dict: [String: Any] = [
            "rating": rating,
            "review_count": reviewCount,
            "confidence": confidence,
            "date": "1977-05-08",
            "venue": "Barton Hall",
            "location": "Ithaca, NY",
            "raw_rating": rawRating,
            "high_ratings": highRatings,
            "low_ratings": lowRatings,
        ]
        if let st = sourceType { dict["source_type"] = st }
        if let t = taper { dict["taper"] = t }
        if let s = source { dict["source"] = s }
        if let l = lineage { dict["lineage"] = l }
        let data = try! JSONSerialization.data(withJSONObject: dict)
        return try! JSONDecoder().decode(RecordingImportData.self, from: data)
    }

    // MARK: - Tests

    @Test("makeRecord maps all fields correctly")
    func testMakeRecordAllFields() {
        let recData = makeRecordingData()
        let record = RecordingImporter.makeRecord(
            recordingId: "gd77-05-08.sbd.hicks.4982.sbeok.shnf",
            from: recData,
            showId: "gd1977-05-08",
            now: 9999
        )

        #expect(record.identifier == "gd77-05-08.sbd.hicks.4982.sbeok.shnf")
        #expect(record.showId == "gd1977-05-08")
        #expect(record.sourceType == "SBD")
        #expect(record.rating == 4.2)
        #expect(record.rawRating == 4.1)
        #expect(record.reviewCount == 30)
        #expect(record.confidence == 0.9)
        #expect(record.highRatings == 28)
        #expect(record.lowRatings == 2)
        #expect(record.taper == "Hicks")
        #expect(record.source == "Soundboard")
        #expect(record.lineage == "Sony ECM > DAT")
        #expect(record.sourceTypeString == "SBD")
        #expect(record.collectionTimestamp == 9999)
    }

    @Test("makeRecord handles nil optional fields")
    func testMakeRecordNilOptionals() {
        let recData = makeRecordingData(sourceType: nil, taper: nil, source: nil, lineage: nil)
        let record = RecordingImporter.makeRecord(
            recordingId: "test-rec",
            from: recData,
            showId: "test-show"
        )
        #expect(record.sourceType == nil)
        #expect(record.taper == nil)
        #expect(record.source == nil)
        #expect(record.lineage == nil)
        #expect(record.sourceTypeString == nil)
    }

    @Test("makeRecord uses the provided showId")
    func testMakeRecordShowId() {
        let recData = makeRecordingData()
        let record = RecordingImporter.makeRecord(recordingId: "r1", from: recData, showId: "show-abc")
        #expect(record.showId == "show-abc")
    }

    @Test("makeRecord uses filename as identifier")
    func testMakeRecordIdentifier() {
        let recData = makeRecordingData()
        let record = RecordingImporter.makeRecord(recordingId: "gd77-05-08.sbd", from: recData, showId: "s1")
        #expect(record.identifier == "gd77-05-08.sbd")
    }

    @Test("RecordingImportData defaults to 0 for missing numeric fields")
    func testDefaultValues() {
        let minimalJson = #"{"date": "1970-01-01", "venue": "V", "location": "L"}"#
        let data = minimalJson.data(using: .utf8)!
        let recData = try! JSONDecoder().decode(RecordingImportData.self, from: data)

        #expect(recData.rating == 0.0)
        #expect(recData.reviewCount == 0)
        #expect(recData.confidence == 0.0)
        #expect(recData.rawRating == 0.0)
        #expect(recData.highRatings == 0)
        #expect(recData.lowRatings == 0)
        #expect(recData.tracks.isEmpty)
        #expect(recData.sourceType == nil)
    }
}
