import Foundation
import GRDB

struct FavoritesDAO: Sendable {
    let database: AppDatabase

    // MARK: - CRUD

    /// Upsert. Clears any tombstone (re-favoriting after delete is a resurrection).
    /// Caller is responsible for setting `updatedAt` on the incoming record.
    func add(_ favoriteShow: FavoriteShowRecord) throws {
        try database.write { db in
            var record = favoriteShow
            record.deletedAt = nil
            try record.insert(db, onConflict: .replace)
        }
    }

    /// Sync-apply upsert. Writes the record verbatim — does NOT clear the
    /// tombstone the way [add] does. Used by [UserSyncApplyService] so that
    /// a server tombstone propagates to the local DB.
    func applyFromSync(_ favoriteShow: FavoriteShowRecord) throws {
        try database.write { db in
            var record = favoriteShow
            try record.insert(db, onConflict: .replace)
        }
    }

    func addAll(_ favoriteShows: [FavoriteShowRecord]) throws {
        try database.write { db in
            for favoriteShow in favoriteShows {
                var record = favoriteShow
                record.deletedAt = nil
                try record.insert(db, onConflict: .replace)
            }
        }
    }

    /// Soft-delete: marks the row with deleted_at so sync can propagate the
    /// removal across devices. UI queries filter these out.
    func remove(_ showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("deletedAt").set(to: now),
                    Column("updatedAt").set(to: now)
                )
        }
    }

    func update(_ favoriteShow: FavoriteShowRecord) throws {
        try database.write { db in
            var record = favoriteShow
            try record.update(db)
        }
    }

    // MARK: - Fetch
    //
    // All UI-facing reads filter out tombstones (deletedAt IS NULL).
    // Sync code paths that need to see tombstones should use
    // fetchAllIncludingTombstones / fetchByIdIncludingTombstones.

    private static let live = Column("deletedAt") == nil

    func fetchAll() throws -> [FavoriteShowRecord] {
        try database.read { db in
            try FavoriteShowRecord
                .filter(Self.live)
                .order(Column("isPinned").desc, Column("addedToFavoritesAt").desc)
                .fetchAll(db)
        }
    }

    func fetchAllIncludingTombstones() throws -> [FavoriteShowRecord] {
        try database.read { db in
            try FavoriteShowRecord.fetchAll(db)
        }
    }

    func fetchPinned() throws -> [FavoriteShowRecord] {
        try database.read { db in
            try FavoriteShowRecord
                .filter(Column("isPinned") == true && Self.live)
                .order(Column("addedToFavoritesAt").desc)
                .fetchAll(db)
        }
    }

    func fetchById(_ showId: String) throws -> FavoriteShowRecord? {
        try database.read { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId && Self.live)
                .fetchOne(db)
        }
    }

    /// Sync helper: returns the row even if tombstoned. Use when pushing
    /// pending changes so the outbox sees the deleted_at marker.
    func fetchByIdIncludingTombstones(_ showId: String) throws -> FavoriteShowRecord? {
        try database.read { db in
            try FavoriteShowRecord.fetchOne(db, key: showId)
        }
    }

    func isFavorite(_ showId: String) throws -> Bool {
        try database.read { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId && Self.live)
                .fetchCount(db) > 0
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try FavoriteShowRecord.filter(Self.live).fetchCount(db)
        }
    }

    // MARK: - Pin management

    func updatePinStatus(_ showId: String, isPinned: Bool) throws {
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("isPinned").set(to: isPinned))
        }
    }

    // MARK: - Review fields

    func updateNotes(_ showId: String, notes: String?) throws {
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("notes").set(to: notes))
        }
    }

    func updateCustomRating(_ showId: String, rating: Double?) throws {
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("customRating").set(to: rating))
        }
    }

    func updateRecordingQuality(_ showId: String, quality: Int?) throws {
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("recordingQuality").set(to: quality))
        }
    }

    func updatePlayingQuality(_ showId: String, quality: Int?) throws {
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("playingQuality").set(to: quality))
        }
    }

    // MARK: - Management

    func clearAll() throws {
        try database.write { db in
            try FavoriteShowRecord.deleteAll(db)
        }
    }

    // MARK: - Observation

    func observeAll() -> ValueObservation<ValueReducers.Fetch<[FavoriteShowRecord]>> {
        ValueObservation.tracking { db in
            try FavoriteShowRecord
                .filter(Self.live)
                .order(Column("isPinned").desc, Column("addedToFavoritesAt").desc)
                .fetchAll(db)
        }
    }

    func observeCount() -> ValueObservation<ValueReducers.Fetch<Int>> {
        ValueObservation.tracking { db in
            try FavoriteShowRecord.filter(Self.live).fetchCount(db)
        }
    }

    // MARK: - Download tracking

    func updateDownloadedRecording(_ showId: String, recordingId: String?, format: String?) throws {
        try database.write { db in
            try FavoriteShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("downloadedRecordingId").set(to: recordingId),
                    Column("downloadedFormat").set(to: format)
                )
        }
    }

    func fetchDownloadedRecordingId(_ showId: String) throws -> String? {
        try database.read { db in
            try FavoriteShowRecord.fetchOne(db, key: showId)?.downloadedRecordingId
        }
    }

    func fetchShowsWithDownloads() throws -> [FavoriteShowRecord] {
        try database.read { db in
            try FavoriteShowRecord
                .filter(Column("downloadedRecordingId") != nil && Self.live)
                .fetchAll(db)
        }
    }

    func clearDownloadedRecording(_ showId: String) throws {
        try updateDownloadedRecording(showId, recordingId: nil, format: nil)
    }
}
