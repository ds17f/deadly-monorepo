import GRDB

struct DataVersionDAO: Sendable {
    let database: AppDatabase

    // MARK: - Singleton UPSERT

    /// Insert or replace the singleton data version row (id is always 1).
    func upsert(_ record: DataVersionRecord) throws {
        try database.write { db in
            var singleton = record
            singleton.id = 1
            try singleton.save(db)
        }
    }

    // MARK: - Fetch

    func fetch() throws -> DataVersionRecord? {
        try database.read { db in
            try DataVersionRecord.fetchOne(db, key: 1)
        }
    }

    func currentVersion() throws -> String? {
        try fetch()?.dataVersion
    }

    func hasData() throws -> Bool {
        try fetch() != nil
    }
}
