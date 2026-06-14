import GRDB

/// Backlog ("Up Next") data access. Ordering is by `position` ascending; the
/// head is the lowest-position live (non-tombstoned) row. Mirrors the Android
/// `BacklogRepository` op set. Slice 1: purely local — no UI, no sync, no advance.
struct BacklogDAO: Sendable {
    let database: AppDatabase

    // MARK: - Reads

    /// Live backlog in play order (head first), tombstones excluded.
    func fetchAll() throws -> [BacklogRecord] {
        try database.read { db in
            try BacklogRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("position").asc)
                .fetchAll(db)
        }
    }

    /// Head of the backlog (next to play), or nil when empty.
    func peekHead() throws -> BacklogRecord? {
        try database.read { db in
            try BacklogRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("position").asc)
                .fetchOne(db)
        }
    }

    func contains(_ showId: String) throws -> Bool {
        try database.read { db in
            try BacklogRecord
                .filter(Column("showId") == showId && Column("deletedAt") == nil)
                .fetchCount(db) > 0
        }
    }

    func count() throws -> Int {
        try database.read { db in
            try BacklogRecord.filter(Column("deletedAt") == nil).fetchCount(db)
        }
    }

    // MARK: - Mutations

    /// Append to the bottom (or move an existing/tombstoned show to the bottom),
    /// clearing any tombstone.
    func addToBottom(_ showId: String, now: Int64) throws {
        try database.write { db in
            let maxPos = try Int64.fetchOne(db, sql: "SELECT MAX(position) FROM backlog") ?? 0
            try db.execute(
                sql: """
                    INSERT INTO backlog (showId, position, addedAt, deletedAt)
                    VALUES (?, ?, ?, NULL)
                    ON CONFLICT(showId) DO UPDATE SET
                        position = excluded.position,
                        addedAt = excluded.addedAt,
                        deletedAt = NULL
                    """,
                arguments: [showId, maxPos + 1, now]
            )
        }
    }

    /// Consume and return the head id (advance *commit*); nil when empty.
    func popHead(now: Int64) throws -> String? {
        try database.write { db in
            guard let head = try BacklogRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("position").asc)
                .fetchOne(db) else { return nil }
            try db.execute(
                sql: "UPDATE backlog SET deletedAt = ? WHERE showId = ?",
                arguments: [now, head.showId]
            )
            return head.showId
        }
    }

    /// Tombstone a single show (sync-friendly remove).
    func remove(_ showId: String, now: Int64) throws {
        try database.write { db in
            try db.execute(
                sql: "UPDATE backlog SET deletedAt = ? WHERE showId = ?",
                arguments: [now, showId]
            )
        }
    }

    /// Rewrite the order to exactly `orderedShowIds` (drag-to-reorder).
    func reorder(_ orderedShowIds: [String]) throws {
        try database.write { db in
            for (index, showId) in orderedShowIds.enumerated() {
                try db.execute(
                    sql: "UPDATE backlog SET position = ? WHERE showId = ?",
                    arguments: [Int64(index), showId]
                )
            }
        }
    }

    /// Tombstone every live row (Clear).
    func clear(now: Int64) throws {
        try database.write { db in
            try db.execute(
                sql: "UPDATE backlog SET deletedAt = ? WHERE deletedAt IS NULL",
                arguments: [now]
            )
        }
    }

    // MARK: - Observation

    func observeAll() -> ValueObservation<ValueReducers.Fetch<[BacklogRecord]>> {
        ValueObservation.tracking { db in
            try BacklogRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("position").asc)
                .fetchAll(db)
        }
    }
}
