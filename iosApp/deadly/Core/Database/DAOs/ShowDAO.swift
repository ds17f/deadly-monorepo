import GRDB

struct ShowDAO: Sendable {
    let database: AppDatabase

    // MARK: - Insert

    func insert(_ show: ShowRecord) throws {
        try database.write { db in
            var record = show
            try record.insert(db)
        }
    }

    func insertAll(_ shows: [ShowRecord]) throws {
        try database.write { db in
            for show in shows {
                var record = show
                try record.insert(db)
            }
        }
    }

    // MARK: - Fetch

    func fetchAll() throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord.order(Column("date").asc).fetchAll(db)
        }
    }

    func fetchById(_ showId: String) throws -> ShowRecord? {
        try database.read { db in
            try ShowRecord.fetchOne(db, key: showId)
        }
    }

    func fetchByIds(_ showIds: [String]) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord.fetchAll(db, keys: showIds)
        }
    }

    func fetchCount() throws -> Int {
        try database.read { db in
            try ShowRecord.fetchCount(db)
        }
    }

    // MARK: - Date queries

    func fetchByYear(_ year: Int) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(Column("year") == year)
                .order(Column("date").asc)
                .fetchAll(db)
        }
    }

    func fetchByYearMonth(_ yearMonth: String) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(Column("yearMonth") == yearMonth)
                .order(Column("date").asc)
                .fetchAll(db)
        }
    }

    func fetchByDate(_ date: String) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(Column("date") == date)
                .order(Column("showSequence").asc)
                .fetchAll(db)
        }
    }

    func fetchInDateRange(start: String, end: String) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(Column("date") >= start && Column("date") <= end)
                .order(Column("date").asc)
                .fetchAll(db)
        }
    }

    // MARK: - Location queries

    func fetchByVenue(_ venueName: String) throws -> [ShowRecord] {
        let pattern = "%\(escapeLike(venueName))%"
        return try database.read { db in
            try ShowRecord
                .filter(sql: "venueName LIKE ? ESCAPE '\\'", arguments: [pattern])
                .order(Column("date").asc)
                .fetchAll(db)
        }
    }

    func fetchByCity(_ city: String) throws -> [ShowRecord] {
        let pattern = "%\(escapeLike(city))%"
        return try database.read { db in
            try ShowRecord
                .filter(sql: "city LIKE ? ESCAPE '\\'", arguments: [pattern])
                .order(Column("date").asc)
                .fetchAll(db)
        }
    }

    func fetchByState(_ state: String) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(Column("state") == state)
                .order(Column("date").asc)
                .fetchAll(db)
        }
    }

    // MARK: - Popular / featured

    func fetchTopRated(limit: Int) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(Column("averageRating") != nil)
                .order(Column("averageRating").desc, Column("totalReviews").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }

    /// Returns shows that occurred on the same month/day in any year (anniversary).
    func fetchOnThisDay(month: Int, day: Int) throws -> [ShowRecord] {
        try database.read { db in
            try ShowRecord
                .filter(sql: "month = ? AND CAST(SUBSTR(date, 9, 2) AS INTEGER) = ?", arguments: [month, day])
                .order(Column("year").asc)
                .fetchAll(db)
        }
    }

    // MARK: - Chronological navigation

    func fetchNext(after date: String) throws -> ShowRecord? {
        try database.read { db in
            try ShowRecord
                .filter(Column("date") > date)
                .order(Column("date").asc)
                .fetchOne(db)
        }
    }

    func fetchPrevious(before date: String) throws -> ShowRecord? {
        try database.read { db in
            try ShowRecord
                .filter(Column("date") < date)
                .order(Column("date").desc)
                .fetchOne(db)
        }
    }

    // MARK: - Search (LIKE fallback for short queries)

    /// LIKE search across venueName, city, state, and date — used when query is ≤2 chars.
    func searchLike(_ query: String) throws -> [ShowRecord] {
        let pattern = "%\(escapeLike(query))%"
        return try database.read { db in
            try ShowRecord
                .filter(sql: """
                    venueName LIKE ? ESCAPE '\\' OR
                    city LIKE ? ESCAPE '\\' OR
                    state LIKE ? ESCAPE '\\' OR
                    date LIKE ? ESCAPE '\\'
                    """, arguments: [pattern, pattern, pattern, pattern])
                .order(Column("date").asc)
                .limit(100)
                .fetchAll(db)
        }
    }

    // MARK: - Management

    func deleteAll() throws {
        try database.write { db in
            try ShowRecord.deleteAll(db)
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
