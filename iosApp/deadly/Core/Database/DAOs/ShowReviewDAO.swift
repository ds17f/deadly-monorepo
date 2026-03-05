import Foundation
import GRDB

struct ShowReviewDAO: Sendable {
    let database: AppDatabase

    // MARK: - Upsert

    func upsert(_ record: ShowReviewRecord) throws {
        try database.write { db in
            var r = record
            try r.save(db, onConflict: .replace)
        }
    }

    // MARK: - Fetch

    func fetchByShowId(_ showId: String) throws -> ShowReviewRecord? {
        try database.read { db in
            try ShowReviewRecord.fetchOne(db, key: showId)
        }
    }

    func fetchByShowIds(_ showIds: [String]) throws -> [ShowReviewRecord] {
        guard !showIds.isEmpty else { return [] }
        return try database.read { db in
            try ShowReviewRecord
                .filter(showIds.contains(Column("showId")))
                .fetchAll(db)
        }
    }

    func fetchAll() throws -> [ShowReviewRecord] {
        try database.read { db in
            try ShowReviewRecord.fetchAll(db)
        }
    }

    // MARK: - Field updates

    func updateNotes(_ showId: String, notes: String?) throws {
        try database.write { db in
            try ShowReviewRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("notes").set(to: notes),
                    Column("updatedAt").set(to: Int64(Date().timeIntervalSince1970 * 1000))
                )
        }
    }

    func updateCustomRating(_ showId: String, rating: Double?) throws {
        try database.write { db in
            try ShowReviewRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("customRating").set(to: rating),
                    Column("updatedAt").set(to: Int64(Date().timeIntervalSince1970 * 1000))
                )
        }
    }

    func updateRecordingQuality(_ showId: String, quality: Int?, recordingId: String? = nil) throws {
        try database.write { db in
            try ShowReviewRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("recordingQuality").set(to: quality),
                    Column("reviewedRecordingId").set(to: recordingId),
                    Column("updatedAt").set(to: Int64(Date().timeIntervalSince1970 * 1000))
                )
        }
    }

    func updatePlayingQuality(_ showId: String, quality: Int?) throws {
        try database.write { db in
            try ShowReviewRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("playingQuality").set(to: quality),
                    Column("updatedAt").set(to: Int64(Date().timeIntervalSince1970 * 1000))
                )
        }
    }

    // MARK: - Delete

    func deleteByShowId(_ showId: String) throws {
        try database.write { db in
            try ShowReviewRecord.deleteOne(db, key: showId)
        }
    }

    func deleteAll() throws {
        try database.write { db in
            try ShowReviewRecord.deleteAll(db)
        }
    }
}
