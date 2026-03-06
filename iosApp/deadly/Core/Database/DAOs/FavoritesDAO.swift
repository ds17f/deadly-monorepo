import GRDB

struct FavoritesDAO: Sendable {
    let database: AppDatabase

    // MARK: - CRUD

    func add(_ favoriteShow: FavoriteShowRecord) throws {
        try database.write { db in
            var record = favoriteShow
            try record.insert(db)
        }
    }

    func addAll(_ favoriteShows: [FavoriteShowRecord]) throws {
        try database.write { db in
            for favoriteShow in favoriteShows {
                var record = favoriteShow
                try record.insert(db)
            }
        }
    }

    func remove(_ showId: String) throws {
        try database.write { db in
            try FavoriteShowRecord.deleteOne(db, key: showId)
        }
    }

    func update(_ favoriteShow: FavoriteShowRecord) throws {
        try database.write { db in
            var record = favoriteShow
            try record.update(db)
        }
    }

    // MARK: - Fetch

    func fetchAll() throws -> [FavoriteShowRecord] {
        try database.read { db in
            try FavoriteShowRecord
                .order(Column("isPinned").desc, Column("addedToFavoritesAt").desc)
                .fetchAll(db)
        }
    }

    func fetchPinned() throws -> [FavoriteShowRecord] {
        try database.read { db in
            try FavoriteShowRecord
                .filter(Column("isPinned") == true)
                .order(Column("addedToFavoritesAt").desc)
                .fetchAll(db)
        }
    }

    func fetchById(_ showId: String) throws -> FavoriteShowRecord? {
        try database.read { db in
            try FavoriteShowRecord.fetchOne(db, key: showId)
        }
    }

    func isFavorite(_ showId: String) throws -> Bool {
        try database.read { db in
            try FavoriteShowRecord.fetchOne(db, key: showId) != nil
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try FavoriteShowRecord.fetchCount(db)
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
                .order(Column("isPinned").desc, Column("addedToFavoritesAt").desc)
                .fetchAll(db)
        }
    }

    func observeCount() -> ValueObservation<ValueReducers.Fetch<Int>> {
        ValueObservation.tracking { db in
            try FavoriteShowRecord.fetchCount(db)
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
                .filter(Column("downloadedRecordingId") != nil)
                .fetchAll(db)
        }
    }

    func clearDownloadedRecording(_ showId: String) throws {
        try updateDownloadedRecording(showId, recordingId: nil, format: nil)
    }
}
