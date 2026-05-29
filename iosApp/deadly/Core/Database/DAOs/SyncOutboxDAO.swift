import Foundation
import GRDB

struct SyncOutboxDAO: Sendable {
    let database: AppDatabase

    /// Idempotent enqueue. If a pending entry for (kind, refId) already
    /// exists, this no-ops — the flusher will read the row's current
    /// state at push time so the final state wins.
    func enqueue(kind: String, refId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            try db.execute(sql: """
                INSERT OR IGNORE INTO sync_outbox (kind, refId, createdAt, attemptCount)
                VALUES (?, ?, ?, 0)
            """, arguments: [kind, refId, now])
        }
    }

    func fetchPending(kind: String) throws -> [SyncOutboxRecord] {
        try database.read { db in
            try SyncOutboxRecord
                .filter(Column("kind") == kind)
                .order(Column("createdAt"))
                .fetchAll(db)
        }
    }

    func delete(id: Int64) throws {
        _ = try database.write { db in
            try SyncOutboxRecord.deleteOne(db, key: id)
        }
    }

    func recordFailure(id: Int64, error: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            try db.execute(sql: """
                UPDATE sync_outbox
                   SET lastAttemptAt = ?, attemptCount = attemptCount + 1, lastError = ?
                 WHERE id = ?
            """, arguments: [now, error, id])
        }
    }

    func pendingCount(kind: String) throws -> Int {
        try database.read { db in
            try SyncOutboxRecord.filter(Column("kind") == kind).fetchCount(db)
        }
    }
}
