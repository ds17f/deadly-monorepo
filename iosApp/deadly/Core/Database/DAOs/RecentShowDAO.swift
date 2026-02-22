import GRDB

struct RecentShowDAO: Sendable {
    let database: AppDatabase

    // MARK: - UPSERT

    /// Insert on first play; increment totalPlayCount and update lastPlayedTimestamp on subsequent plays.
    func upsert(showId: String, timestamp: Int64) throws {
        try database.write { db in
            try db.execute(
                sql: """
                    INSERT INTO recent_shows (showId, lastPlayedTimestamp, firstPlayedTimestamp, totalPlayCount)
                    VALUES (?, ?, ?, 1)
                    ON CONFLICT(showId) DO UPDATE SET
                        lastPlayedTimestamp = excluded.lastPlayedTimestamp,
                        totalPlayCount = totalPlayCount + 1
                    """,
                arguments: [showId, timestamp, timestamp]
            )
        }
    }

    // MARK: - Fetch

    func fetchRecent(limit: Int) throws -> [RecentShowRecord] {
        try database.read { db in
            try RecentShowRecord
                .order(Column("lastPlayedTimestamp").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }

    func fetchById(_ showId: String) throws -> RecentShowRecord? {
        try database.read { db in
            try RecentShowRecord.fetchOne(db, key: showId)
        }
    }

    func fetchMostPlayed(limit: Int) throws -> [RecentShowRecord] {
        try database.read { db in
            try RecentShowRecord
                .order(Column("totalPlayCount").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try RecentShowRecord.fetchCount(db)
        }
    }

    // MARK: - Cleanup

    /// Deletes records with lastPlayedTimestamp older than the cutoff. Returns the number of rows deleted.
    @discardableResult
    func deleteOlderThan(_ cutoffTimestamp: Int64) throws -> Int {
        try database.write { db in
            try RecentShowRecord
                .filter(Column("lastPlayedTimestamp") < cutoffTimestamp)
                .deleteAll(db)
        }
    }

    func clearAll() throws {
        try database.write { db in
            try RecentShowRecord.deleteAll(db)
        }
    }

    // MARK: - Observation

    func observeRecent(limit: Int) -> ValueObservation<ValueReducers.Fetch<[RecentShowRecord]>> {
        ValueObservation.tracking { db in
            try RecentShowRecord
                .order(Column("lastPlayedTimestamp").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }
}
