import GRDB

struct RecordingPreferenceDAO: Sendable {
    let database: AppDatabase

    // MARK: - Upsert

    func upsert(_ record: RecordingPreferenceRecord) throws {
        try database.write { db in
            var r = record
            try r.save(db, onConflict: .replace)
        }
    }

    // MARK: - Fetch

    func fetch(_ showId: String) throws -> RecordingPreferenceRecord? {
        try database.read { db in
            try RecordingPreferenceRecord.fetchOne(db, key: showId)
        }
    }

    func fetchRecordingId(_ showId: String) throws -> String? {
        try database.read { db in
            try RecordingPreferenceRecord.fetchOne(db, key: showId)?.recordingId
        }
    }

    func fetchAll() throws -> [RecordingPreferenceRecord] {
        try database.read { db in
            try RecordingPreferenceRecord.fetchAll(db)
        }
    }

    // MARK: - Delete

    func delete(_ showId: String) throws {
        try database.write { db in
            try RecordingPreferenceRecord.deleteOne(db, key: showId)
        }
    }

    func deleteAll() throws {
        try database.write { db in
            try RecordingPreferenceRecord.deleteAll(db)
        }
    }
}
