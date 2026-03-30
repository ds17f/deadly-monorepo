import GRDB

struct CollectionsDAO: Sendable {
    let database: AppDatabase

    // MARK: - Insert

    func insert(_ collection: DeadCollectionRecord) throws {
        try database.write { db in
            var record = collection
            try record.insert(db)
        }
    }

    func insertAll(_ collections: [DeadCollectionRecord]) throws {
        try database.write { db in
            for collection in collections {
                var record = collection
                try record.insert(db)
            }
        }
    }

    // MARK: - Fetch

    /// SQL predicate that excludes collections tagged as official releases.
    private static let excludeOfficial =
        "tagsJson NOT LIKE '%\"official\"%' ESCAPE '\\' AND tagsJson NOT LIKE '%\"official-release\"%' ESCAPE '\\'"

    func fetchAll() throws -> [DeadCollectionRecord] {
        try database.read { db in
            try DeadCollectionRecord
                .filter(sql: Self.excludeOfficial)
                .fetchAll(db)
        }
    }

    func fetchById(_ id: String) throws -> DeadCollectionRecord? {
        try database.read { db in
            try DeadCollectionRecord.fetchOne(db, key: id)
        }
    }

    func fetchFeatured(limit: Int) throws -> [DeadCollectionRecord] {
        try database.read { db in
            try DeadCollectionRecord
                .filter(sql: Self.excludeOfficial)
                .order(Column("totalShows").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }

    /// Returns collections whose tagsJson contains the given tag value.
    func fetchByTag(_ tag: String) throws -> [DeadCollectionRecord] {
        let pattern = "%\"\(escapeLike(tag))\"%"
        return try database.read { db in
            try DeadCollectionRecord
                .filter(sql: "tagsJson LIKE ? ESCAPE '\\' AND \(Self.excludeOfficial)", arguments: [pattern])
                .fetchAll(db)
        }
    }

    /// LIKE search across name and description.
    func search(_ query: String) throws -> [DeadCollectionRecord] {
        let pattern = "%\(escapeLike(query))%"
        return try database.read { db in
            try DeadCollectionRecord
                .filter(sql: "(name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\') AND \(Self.excludeOfficial)", arguments: [pattern, pattern])
                .fetchAll(db)
        }
    }

    /// Returns collections whose showIdsJson contains the given showId.
    func fetchContainingShow(_ showId: String) throws -> [DeadCollectionRecord] {
        let pattern = "%\"\(escapeLike(showId))\"%"
        return try database.read { db in
            try DeadCollectionRecord
                .filter(sql: "showIdsJson LIKE ? ESCAPE '\\' AND \(Self.excludeOfficial)", arguments: [pattern])
                .fetchAll(db)
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try DeadCollectionRecord
                .filter(sql: Self.excludeOfficial)
                .fetchCount(db)
        }
    }

    // MARK: - Management

    func deleteAll() throws {
        try database.write { db in
            try DeadCollectionRecord.deleteAll(db)
        }
    }

    // MARK: - Private helpers

    private func escapeLike(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "%", with: "\\%")
            .replacingOccurrences(of: "_", with: "\\_")
    }
}
