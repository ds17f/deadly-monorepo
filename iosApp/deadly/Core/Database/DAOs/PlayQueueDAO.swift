import Foundation
import GRDB

/// Data access for the persistent show queue (ADR-0010).
///
/// Ordering is by `position` ascending; the head (next show) is the lowest
/// position. Append goes to MAX(position)+1; head-insert shifts all rows down
/// and inserts at 0.
struct PlayQueueDAO: Sendable {
    let database: AppDatabase

    // MARK: - Fetch

    func fetchAll() throws -> [PlayQueueRecord] {
        try database.read { db in
            try PlayQueueRecord.order(Column("position").asc).fetchAll(db)
        }
    }

    func peekHead() throws -> PlayQueueRecord? {
        try database.read { db in
            try PlayQueueRecord.order(Column("position").asc).fetchOne(db)
        }
    }

    func contains(showId: String) throws -> Bool {
        try database.read { db in
            try PlayQueueRecord.filter(Column("showId") == showId).fetchCount(db) > 0
        }
    }

    // MARK: - Mutate

    /// Append to the tail.
    func append(showId: String, recordingId: String?) throws {
        try database.write { db in
            let maxPos = try Int.fetchOne(db, sql: "SELECT MAX(position) FROM play_queue") ?? -1
            var rec = PlayQueueRecord(
                id: nil, showId: showId, recordingId: recordingId,
                position: maxPos + 1, resumeTrackIndex: nil, resumePositionMs: nil,
                addedAt: Int64(Date().timeIntervalSince1970 * 1000)
            )
            try rec.insert(db)
        }
    }

    /// Insert at the head (position 0), shifting existing rows down. Carries an
    /// optional resume snapshot (interrupt re-queue).
    func insertHead(showId: String, recordingId: String?, resumeTrackIndex: Int?, resumePositionMs: Int64?) throws {
        try database.write { db in
            try db.execute(sql: "UPDATE play_queue SET position = position + 1")
            var rec = PlayQueueRecord(
                id: nil, showId: showId, recordingId: recordingId,
                position: 0, resumeTrackIndex: resumeTrackIndex, resumePositionMs: resumePositionMs,
                addedAt: Int64(Date().timeIntervalSince1970 * 1000)
            )
            try rec.insert(db)
        }
    }

    func delete(id: Int64) throws {
        _ = try database.write { db in
            try PlayQueueRecord.deleteOne(db, key: id)
        }
    }

    /// Remove and return the head (end-of-show auto-advance).
    func popHead() throws -> PlayQueueRecord? {
        try database.write { db in
            guard let head = try PlayQueueRecord.order(Column("position").asc).fetchOne(db) else { return nil }
            if let id = head.id { _ = try PlayQueueRecord.deleteOne(db, key: id) }
            return head
        }
    }

    func clear() throws {
        _ = try database.write { db in
            try PlayQueueRecord.deleteAll(db)
        }
    }

    /// Rewrite positions to match the given ordered ids (index becomes position).
    func reorder(orderedIds: [Int64]) throws {
        try database.write { db in
            for (index, id) in orderedIds.enumerated() {
                try db.execute(sql: "UPDATE play_queue SET position = ? WHERE id = ?", arguments: [index, id])
            }
        }
    }
}
