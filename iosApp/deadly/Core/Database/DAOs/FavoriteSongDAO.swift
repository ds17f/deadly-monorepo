import Foundation
import GRDB

struct FavoriteSongDAO: Sendable {
    let database: AppDatabase

    private static let live = Column("deletedAt") == nil

    // MARK: - Mutation
    //
    // Toggle is the only meaningful write — favorite vs unfavorite a track.
    // Soft-delete semantics mirror FavoritesDAO: a tombstoned row stays in
    // the table so other devices learn the row was removed.

    /// Insert a new row, or resurrect an existing tombstoned one. Returns
    /// the local row id (caller passes it into the outbox refId).
    @discardableResult
    func upsertOrResurrect(
        showId: String, trackTitle: String,
        trackNumber: Int?, recordingId: String?
    ) throws -> Int64 {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return try database.write { db in
            if let existing = try Self.fetchByKey(db, showId: showId, trackTitle: trackTitle, recordingId: recordingId) {
                try db.execute(
                    sql: """
                        UPDATE favorite_songs
                           SET trackNumber = ?, recordingId = ?,
                               updatedAt = ?, deletedAt = NULL
                         WHERE id = ?
                    """,
                    arguments: [trackNumber, recordingId, now, existing.id]
                )
                return existing.id!
            }
            var record = FavoriteSongRecord(
                id: nil,
                showId: showId,
                trackTitle: trackTitle,
                trackNumber: trackNumber,
                recordingId: recordingId,
                createdAt: now,
                updatedAt: now,
                deletedAt: nil
            )
            try record.insert(db)
            return record.id!
        }
    }

    /// Soft-delete by natural key. Returns the local row id of the
    /// tombstoned row so callers can enqueue the outbox push.
    @discardableResult
    func softDelete(showId: String, trackTitle: String, recordingId: String?) throws -> Int64? {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return try database.write { db in
            guard let existing = try Self.fetchByKey(db, showId: showId, trackTitle: trackTitle, recordingId: recordingId) else {
                return nil
            }
            try db.execute(
                sql: "UPDATE favorite_songs SET deletedAt = ?, updatedAt = ? WHERE id = ?",
                arguments: [now, now, existing.id]
            )
            return existing.id
        }
    }

    /// Plain insert, used by importers that already know the record state.
    /// Caller is responsible for setting updatedAt.
    func insert(_ record: FavoriteSongRecord) throws {
        try database.write { db in
            var r = record
            try r.insert(db, onConflict: .ignore)
        }
    }

    /// Sync-apply upsert. Writes the record verbatim — does NOT clear the
    /// tombstone the way [upsertOrResurrect] does. Mirrors FavoritesDAO.
    func applyFromSync(_ record: FavoriteSongRecord) throws {
        try database.write { db in
            // Match by natural key, not local id (server id differs).
            if let existing = try Self.fetchByKey(db, showId: record.showId, trackTitle: record.trackTitle, recordingId: record.recordingId) {
                try db.execute(
                    sql: """
                        UPDATE favorite_songs
                           SET trackNumber = ?, recordingId = ?,
                               createdAt = ?, updatedAt = ?, deletedAt = ?
                         WHERE id = ?
                    """,
                    arguments: [record.trackNumber, record.recordingId, record.createdAt, record.updatedAt, record.deletedAt, existing.id]
                )
            } else {
                var r = record
                r.id = nil
                try r.insert(db)
            }
        }
    }

    func deleteForShow(_ showId: String) throws {
        try database.write { db in
            try FavoriteSongRecord.filter(Column("showId") == showId).deleteAll(db)
        }
    }

    // MARK: - Query

    func isFavorite(showId: String, trackTitle: String, recordingId: String?) throws -> Bool {
        try database.read { db in
            try Self.fetchByKey(db, showId: showId, trackTitle: trackTitle, recordingId: recordingId, liveOnly: true) != nil
        }
    }

    func fetchAll() throws -> [FavoriteSongRecord] {
        try database.read { db in
            try FavoriteSongRecord
                .filter(Self.live)
                .order(Column("createdAt").desc)
                .fetchAll(db)
        }
    }

    func fetchAllIncludingTombstones() throws -> [FavoriteSongRecord] {
        try database.read { db in
            try FavoriteSongRecord.order(Column("createdAt").desc).fetchAll(db)
        }
    }

    /// Sync helper used by [FavoritesPushService] to read a row's current
    /// state at flush time, including its tombstone marker.
    func fetchByLocalIdIncludingTombstones(_ localId: Int64) throws -> FavoriteSongRecord? {
        try database.read { db in
            try FavoriteSongRecord.fetchOne(db, key: localId)
        }
    }

    /// Sync helper used by [UserSyncApplyService] when applying a server row.
    /// Server rows arrive keyed by (showId, trackTitle, recordingId); we look up
    /// the matching local row so applyFromSync can update it in place rather
    /// than violating the natural-key uniqueness constraint.
    func fetchByLocalIdIncludingTombstones(forNaturalKey dto: SyncFavoriteTrackV3) throws -> FavoriteSongRecord? {
        try database.read { db in
            try Self.fetchByKey(db, showId: dto.showId, trackTitle: dto.trackTitle, recordingId: dto.recordingId)
        }
    }

    // MARK: - Observation

    func observeFavoriteTitles(showId: String) -> ValueObservation<ValueReducers.Fetch<Set<String>>> {
        ValueObservation.tracking { db in
            let titles = try FavoriteSongRecord
                .filter(Column("showId") == showId && Self.live)
                .fetchAll(db)
                .map(\.trackTitle)
            return Set(titles)
        }
    }

    func observeIsFavorite(showId: String, trackTitle: String, recordingId: String?) -> ValueObservation<ValueReducers.Fetch<Bool>> {
        ValueObservation.tracking { db in
            try Self.fetchByKey(db, showId: showId, trackTitle: trackTitle, recordingId: recordingId, liveOnly: true) != nil
        }
    }

    // MARK: - Internals

    private static func fetchByKey(
        _ db: Database,
        showId: String, trackTitle: String, recordingId: String?,
        liveOnly: Bool = false
    ) throws -> FavoriteSongRecord? {
        var query = FavoriteSongRecord
            .filter(Column("showId") == showId && Column("trackTitle") == trackTitle)
        if let rid = recordingId {
            query = query.filter(Column("recordingId") == rid)
        } else {
            query = query.filter(Column("recordingId") == nil)
        }
        if liveOnly {
            query = query.filter(live)
        }
        return try query.fetchOne(db)
    }
}
