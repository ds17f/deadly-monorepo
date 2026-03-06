import GRDB

struct TrackReviewDAO: Sendable {
    let database: AppDatabase

    // MARK: - Upsert

    func upsert(_ record: TrackReviewRecord) throws {
        try database.write { db in
            var r = record
            try r.save(db, onConflict: .replace)
        }
    }

    // MARK: - Fetch

    func fetchForShow(_ showId: String) throws -> [TrackReviewRecord] {
        try database.read { db in
            try TrackReviewRecord
                .filter(Column("showId") == showId)
                .order(Column("trackNumber").asc, Column("trackTitle").asc)
                .fetchAll(db)
        }
    }

    func fetch(showId: String, trackTitle: String, recordingId: String?) throws -> TrackReviewRecord? {
        try database.read { db in
            if let rid = recordingId {
                return try TrackReviewRecord
                    .filter(Column("showId") == showId && Column("trackTitle") == trackTitle && Column("recordingId") == rid)
                    .fetchOne(db)
            } else {
                return try TrackReviewRecord
                    .filter(Column("showId") == showId && Column("trackTitle") == trackTitle && Column("recordingId") == nil)
                    .fetchOne(db)
            }
        }
    }

    func fetchCountForShow(_ showId: String) throws -> Int {
        try database.read { db in
            try TrackReviewRecord.filter(Column("showId") == showId).fetchCount(db)
        }
    }

    func fetchAll() throws -> [TrackReviewRecord] {
        try database.read { db in
            try TrackReviewRecord.fetchAll(db)
        }
    }

    func fetchThumbsUp() throws -> [TrackReviewRecord] {
        try database.read { db in
            try TrackReviewRecord
                .filter(Column("thumbs") == 1)
                .order(Column("updatedAt").desc)
                .fetchAll(db)
        }
    }

    // MARK: - Observation

    func observeThumbsUpTitles(showId: String) -> ValueObservation<ValueReducers.Fetch<Set<String>>> {
        ValueObservation.tracking { db in
            let titles = try TrackReviewRecord
                .filter(Column("showId") == showId && Column("thumbs") == 1)
                .fetchAll(db)
                .map(\.trackTitle)
            return Set(titles)
        }
    }

    // MARK: - Delete

    func delete(showId: String, trackTitle: String, recordingId: String?) throws {
        try database.write { db in
            if let rid = recordingId {
                try db.execute(
                    sql: "DELETE FROM track_reviews WHERE showId = ? AND trackTitle = ? AND recordingId = ?",
                    arguments: [showId, trackTitle, rid]
                )
            } else {
                try db.execute(
                    sql: "DELETE FROM track_reviews WHERE showId = ? AND trackTitle = ? AND recordingId IS NULL",
                    arguments: [showId, trackTitle]
                )
            }
        }
    }

    func deleteForShow(_ showId: String) throws {
        try database.write { db in
            try TrackReviewRecord.filter(Column("showId") == showId).deleteAll(db)
        }
    }
}
