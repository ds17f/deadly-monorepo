import Foundation

/// Converts `RecordingImportData` (parsed from JSON) into `RecordingRecord`.
struct RecordingImporter {

    /// Build a `RecordingRecord` from parsed import data.
    /// - Parameters:
    ///   - recordingId: The archive.org identifier (filename without .json extension).
    ///   - data: Parsed recording metadata.
    ///   - showId: The show this recording belongs to.
    static func makeRecord(
        recordingId: String,
        from data: RecordingImportData,
        showId: String,
        now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> RecordingRecord {
        RecordingRecord(
            identifier: recordingId,
            showId: showId,
            sourceType: data.sourceType,
            rating: data.rating,
            rawRating: data.rawRating,
            reviewCount: data.reviewCount,
            confidence: data.confidence,
            highRatings: data.highRatings,
            lowRatings: data.lowRatings,
            taper: data.taper,
            source: data.source,
            lineage: data.lineage,
            sourceTypeString: data.sourceType,
            collectionTimestamp: now
        )
    }
}
