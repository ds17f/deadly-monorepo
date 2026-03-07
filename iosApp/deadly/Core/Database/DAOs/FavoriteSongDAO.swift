import GRDB

struct FavoriteSongDAO: Sendable {
    let database: AppDatabase

    // MARK: - Insert

    func insert(_ record: FavoriteSongRecord) throws {
        try database.write { db in
            var r = record
            try r.insert(db, onConflict: .ignore)
        }
    }

    // MARK: - Delete

    func delete(showId: String, trackTitle: String, recordingId: String?) throws {
        try database.write { db in
            if let rid = recordingId {
                try db.execute(
                    sql: "DELETE FROM favorite_songs WHERE showId = ? AND trackTitle = ? AND recordingId = ?",
                    arguments: [showId, trackTitle, rid]
                )
            } else {
                try db.execute(
                    sql: "DELETE FROM favorite_songs WHERE showId = ? AND trackTitle = ? AND recordingId IS NULL",
                    arguments: [showId, trackTitle]
                )
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
            if let rid = recordingId {
                return try FavoriteSongRecord
                    .filter(Column("showId") == showId && Column("trackTitle") == trackTitle && Column("recordingId") == rid)
                    .fetchOne(db) != nil
            } else {
                return try FavoriteSongRecord
                    .filter(Column("showId") == showId && Column("trackTitle") == trackTitle && Column("recordingId") == nil)
                    .fetchOne(db) != nil
            }
        }
    }

    func fetchAll() throws -> [FavoriteSongRecord] {
        try database.read { db in
            try FavoriteSongRecord.order(Column("createdAt").desc).fetchAll(db)
        }
    }

    // MARK: - Observation

    func observeFavoriteTitles(showId: String) -> ValueObservation<ValueReducers.Fetch<Set<String>>> {
        ValueObservation.tracking { db in
            let titles = try FavoriteSongRecord
                .filter(Column("showId") == showId)
                .fetchAll(db)
                .map(\.trackTitle)
            return Set(titles)
        }
    }

    func observeIsFavorite(showId: String, trackTitle: String, recordingId: String?) -> ValueObservation<ValueReducers.Fetch<Bool>> {
        ValueObservation.tracking { db in
            if let rid = recordingId {
                return try FavoriteSongRecord
                    .filter(Column("showId") == showId && Column("trackTitle") == trackTitle && Column("recordingId") == rid)
                    .fetchOne(db) != nil
            } else {
                return try FavoriteSongRecord
                    .filter(Column("showId") == showId && Column("trackTitle") == trackTitle && Column("recordingId") == nil)
                    .fetchOne(db) != nil
            }
        }
    }
}
