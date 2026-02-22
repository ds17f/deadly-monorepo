import GRDB

struct RecordingDAO: Sendable {
    let database: AppDatabase

    // MARK: - Insert

    func insert(_ recording: RecordingRecord) throws {
        try database.write { db in
            var record = recording
            try record.insert(db)
        }
    }

    func insertAll(_ recordings: [RecordingRecord]) throws {
        try database.write { db in
            for recording in recordings {
                var record = recording
                try record.insert(db, onConflict: .ignore)
            }
        }
    }

    // MARK: - Fetch

    func fetchById(_ identifier: String) throws -> RecordingRecord? {
        try database.read { db in
            try RecordingRecord.fetchOne(db, key: identifier)
        }
    }

    func fetchForShow(_ showId: String) throws -> [RecordingRecord] {
        try database.read { db in
            try RecordingRecord
                .filter(Column("show_id") == showId)
                .order(Column("rating").desc)
                .fetchAll(db)
        }
    }

    func fetchBestForShow(_ showId: String) throws -> RecordingRecord? {
        try database.read { db in
            try RecordingRecord
                .filter(Column("show_id") == showId)
                .order(Column("rating").desc)
                .fetchOne(db)
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try RecordingRecord.fetchCount(db)
        }
    }

    func fetchCountForShow(_ showId: String) throws -> Int {
        try database.read { db in
            try RecordingRecord
                .filter(Column("show_id") == showId)
                .fetchCount(db)
        }
    }

    func fetchTopRated(minRating: Double, minReviews: Int, limit: Int) throws -> [RecordingRecord] {
        try database.read { db in
            try RecordingRecord
                .filter(Column("rating") >= minRating && Column("review_count") >= minReviews)
                .order(Column("rating").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }

    // MARK: - Management

    func deleteForShow(_ showId: String) throws {
        try database.write { db in
            try RecordingRecord
                .filter(Column("show_id") == showId)
                .deleteAll(db)
        }
    }

    func deleteAll() throws {
        try database.write { db in
            try RecordingRecord.deleteAll(db)
        }
    }
}
