import GRDB

struct ShowPlayerTagDAO: Sendable {
    let database: AppDatabase

    // MARK: - Upsert

    func upsert(_ record: ShowPlayerTagRecord) throws {
        try database.write { db in
            var r = record
            try r.save(db, onConflict: .replace)
        }
    }

    // MARK: - Fetch

    func fetchForShow(_ showId: String) throws -> [ShowPlayerTagRecord] {
        try database.read { db in
            try ShowPlayerTagRecord
                .filter(Column("showId") == showId)
                .order(Column("playerName").asc)
                .fetchAll(db)
        }
    }

    func fetchStandoutShowsForPlayer(_ playerName: String) throws -> [ShowPlayerTagRecord] {
        try database.read { db in
            try ShowPlayerTagRecord
                .filter(Column("playerName") == playerName && Column("isStandout") == true)
                .order(Column("createdAt").desc)
                .fetchAll(db)
        }
    }

    func fetchCountForShow(_ showId: String) throws -> Int {
        try database.read { db in
            try ShowPlayerTagRecord.filter(Column("showId") == showId).fetchCount(db)
        }
    }

    func fetchAll() throws -> [ShowPlayerTagRecord] {
        try database.read { db in
            try ShowPlayerTagRecord.fetchAll(db)
        }
    }

    func fetchAllStandoutPlayerNames() throws -> [String] {
        try database.read { db in
            try String.fetchAll(db, sql: """
                SELECT DISTINCT playerName FROM show_player_tags WHERE isStandout = 1 ORDER BY playerName ASC
            """)
        }
    }

    // MARK: - Delete

    func remove(showId: String, playerName: String) throws {
        try database.write { db in
            try db.execute(
                sql: "DELETE FROM show_player_tags WHERE showId = ? AND playerName = ?",
                arguments: [showId, playerName]
            )
        }
    }

    func removeForShow(_ showId: String) throws {
        try database.write { db in
            try ShowPlayerTagRecord.filter(Column("showId") == showId).deleteAll(db)
        }
    }
}
