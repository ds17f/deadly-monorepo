import GRDB

struct LibraryDAO: Sendable {
    let database: AppDatabase

    // MARK: - CRUD

    func add(_ libraryShow: LibraryShowRecord) throws {
        try database.write { db in
            var record = libraryShow
            try record.insert(db)
        }
    }

    func addAll(_ libraryShows: [LibraryShowRecord]) throws {
        try database.write { db in
            for libraryShow in libraryShows {
                var record = libraryShow
                try record.insert(db)
            }
        }
    }

    func remove(_ showId: String) throws {
        try database.write { db in
            try LibraryShowRecord.deleteOne(db, key: showId)
        }
    }

    func update(_ libraryShow: LibraryShowRecord) throws {
        try database.write { db in
            var record = libraryShow
            try record.update(db)
        }
    }

    // MARK: - Fetch

    func fetchAll() throws -> [LibraryShowRecord] {
        try database.read { db in
            try LibraryShowRecord
                .order(Column("isPinned").desc, Column("addedToLibraryAt").desc)
                .fetchAll(db)
        }
    }

    func fetchPinned() throws -> [LibraryShowRecord] {
        try database.read { db in
            try LibraryShowRecord
                .filter(Column("isPinned") == true)
                .order(Column("addedToLibraryAt").desc)
                .fetchAll(db)
        }
    }

    func fetchById(_ showId: String) throws -> LibraryShowRecord? {
        try database.read { db in
            try LibraryShowRecord.fetchOne(db, key: showId)
        }
    }

    func isInLibrary(_ showId: String) throws -> Bool {
        try database.read { db in
            try LibraryShowRecord.fetchOne(db, key: showId) != nil
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try LibraryShowRecord.fetchCount(db)
        }
    }

    // MARK: - Pin management

    func updatePinStatus(_ showId: String, isPinned: Bool) throws {
        try database.write { db in
            try LibraryShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("isPinned").set(to: isPinned))
        }
    }

    // MARK: - Recording preference

    func updatePreferredRecording(_ showId: String, recordingId: String?) throws {
        try database.write { db in
            try LibraryShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db, Column("preferredRecordingId").set(to: recordingId))
        }
    }

    func fetchPreferredRecordingId(_ showId: String) throws -> String? {
        try database.read { db in
            try LibraryShowRecord.fetchOne(db, key: showId)?.preferredRecordingId
        }
    }

    // MARK: - Management

    func clearAll() throws {
        try database.write { db in
            try LibraryShowRecord.deleteAll(db)
        }
    }

    // MARK: - Observation

    func observeAll() -> ValueObservation<ValueReducers.Fetch<[LibraryShowRecord]>> {
        ValueObservation.tracking { db in
            try LibraryShowRecord
                .order(Column("isPinned").desc, Column("addedToLibraryAt").desc)
                .fetchAll(db)
        }
    }

    func observeCount() -> ValueObservation<ValueReducers.Fetch<Int>> {
        ValueObservation.tracking { db in
            try LibraryShowRecord.fetchCount(db)
        }
    }

    // MARK: - Download tracking

    func updateDownloadedRecording(_ showId: String, recordingId: String?, format: String?) throws {
        try database.write { db in
            try LibraryShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("downloadedRecordingId").set(to: recordingId),
                    Column("downloadedFormat").set(to: format)
                )
        }
    }

    func fetchDownloadedRecordingId(_ showId: String) throws -> String? {
        try database.read { db in
            try LibraryShowRecord.fetchOne(db, key: showId)?.downloadedRecordingId
        }
    }

    func fetchShowsWithDownloads() throws -> [LibraryShowRecord] {
        try database.read { db in
            try LibraryShowRecord
                .filter(Column("downloadedRecordingId") != nil)
                .fetchAll(db)
        }
    }

    func clearDownloadedRecording(_ showId: String) throws {
        try updateDownloadedRecording(showId, recordingId: nil, format: nil)
    }
}
