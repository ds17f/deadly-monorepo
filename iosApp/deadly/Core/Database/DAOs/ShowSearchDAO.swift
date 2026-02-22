import GRDB

struct ShowSearchDAO: Sendable {
    let database: AppDatabase

    // MARK: - Insert

    func insert(_ record: ShowSearchRecord) throws {
        try database.write { db in
            var mutable = record
            try mutable.insert(db)
        }
    }

    func insertAll(_ records: [ShowSearchRecord]) throws {
        try database.write { db in
            for record in records {
                var mutable = record
                try mutable.insert(db)
            }
        }
    }

    // MARK: - Search

    /// FTS4 MATCH query — returns showIds of matching rows.
    func search(_ query: String) throws -> [String] {
        try database.read { db in
            let rows = try Row.fetchAll(
                db,
                sql: "SELECT showId FROM show_search WHERE show_search MATCH ?",
                arguments: [query]
            )
            return rows.map { $0["showId"] as String }
        }
    }

    func indexedCount() throws -> Int {
        try database.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM show_search") ?? 0
        }
    }

    // MARK: - Management

    func clearAll() throws {
        try database.write { db in
            try db.execute(sql: "DELETE FROM show_search")
        }
    }
}
