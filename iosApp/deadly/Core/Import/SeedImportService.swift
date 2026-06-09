import Foundation
import GRDB

/// Imports the prebuilt catalog seed (`catalog.db`) into the live, already-migrated
/// GRDB database using `ATTACH` + bulk `INSERT…SELECT`.
///
/// Why not copy the seed file over the app DB? The seed is a **neutral
/// catalog-only** database — it has none of the device-local tables (favorites,
/// reviews, recents, sync outbox) and none of GRDB's migration bookkeeping.
/// Opening it as the live DB would drop user data and confuse the migrator.
/// Instead we keep the app's own migrated DB and copy the catalog rows into it.
/// See `docs/adr/0007-prebuilt-catalog-db.md`.
///
/// Flow (catalog writes in one transaction so the health gate — a committed
/// `data_version` row — flips only on success):
///   1. ATTACH the seed as `seed` (outside any transaction).
///   2. In a txn: clear the childless catalog tables → **upsert** `shows` (never
///      delete it, so CASCADE children holding user data survive) → copy
///      `recordings`/`dead_collections` → rebuild the `show_search` FTS on-device
///      → copy `data_version` LAST.
///   3. DETACH, reconcile denormalized favorite flags, return counts.
///
/// `shows` is upserted rather than wiped because it is the parent of CASCADE
/// children that hold user data (favorites, reviews, recording prefs, player
/// tags); deleting it would silently take them with it. See
/// `docs/adr/0009-non-destructive-catalog-refresh.md`.
///
/// FTS is rebuilt rather than shipped: `show_search.searchText` is a computed blob
/// — see `ShowSearchText`, shared with the JSON import path so search is identical
/// regardless of source. Mirrors the Android `SeedDatabaseImportService`.
struct SeedImportService: Sendable {
    let database: AppDatabase
    let showDAO: ShowDAO

    // Catalog column lists — must match data/catalog_schema.json. The live `shows`
    // table additionally has the device-local isFavorite/favoritedAt, defaulted on copy.
    private static let showCatalogCols =
        "showId,date,year,month,yearMonth,band,url,venueName,city,state,country," +
        "locationRaw,setlistStatus,setlistRaw,songList,lineupStatus,lineupRaw,memberList," +
        "showSequence,recordingsRaw,recordingCount,bestRecordingId,bestSourceType," +
        "averageRating,totalReviews,coverImageUrl,createdAt,updatedAt"

    private static let recordingCols =
        "identifier,show_id,source_type,rating,raw_rating,review_count,confidence," +
        "high_ratings,low_ratings,taper,source,lineage,source_type_string,collection_timestamp"

    private static let collectionCols =
        "id,name,description,tagsJson,showIdsJson,totalShows,primaryTag,createdAt,updatedAt"

    private static let dataVersionCols =
        "id,dataVersion,packageName,versionType,description,importedAt,gitCommit,gitTag," +
        "buildTimestamp,totalShows,totalVenues,totalFiles,totalSizeBytes"

    // `col=excluded.col` for every catalog column except the conflict key (showId)
    // and the device-local favorite columns, which the upsert must not touch.
    // Derived from showCatalogCols so it can't drift.
    private static let showUpsertAssignments: String =
        showCatalogCols.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { $0 != "showId" }
            .map { "\($0)=excluded.\($0)" }
            .joined(separator: ", ")

    struct Result: Sendable {
        let shows: Int
        let recordings: Int
    }

    /// Copy the catalog from the seed file into the live DB. Throws on any failure
    /// (caller falls back to the JSON import).
    func importSeed(at seedURL: URL) throws -> Result {
        try validateSeedFile(seedURL)

        try database.writeWithoutTransaction { db in
            let escaped = seedURL.path.replacingOccurrences(of: "'", with: "''")
            // ATTACH must run outside a transaction.
            try db.execute(sql: "ATTACH DATABASE '\(escaped)' AS seed")
            defer { try? db.execute(sql: "DETACH DATABASE seed") }

            let seedShows = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM seed.shows") ?? 0
            guard seedShows > 0 else {
                throw ImportError.seedInvalid("seed contains no shows")
            }

            try db.inTransaction {
                // Clear the childless catalog/derived tables (no user data hangs off
                // these). `shows` is NOT deleted — it has CASCADE children holding
                // user data (favorites, reviews, prefs, …); we upsert it instead.
                // See docs/adr/0009-non-destructive-catalog-refresh.md.
                try db.execute(sql: "DELETE FROM show_search")
                try db.execute(sql: "DELETE FROM recordings")
                try db.execute(sql: "DELETE FROM dead_collections")
                try db.execute(sql: "DELETE FROM data_version")

                // Shows: upsert catalog columns by showId. On insert, default the
                // device-local favorite state; on conflict, update catalog columns
                // only and leave isFavorite/favoritedAt alone (reconciled below).
                // `WHERE true` disambiguates the INSERT…SELECT…ON CONFLICT parse.
                try db.execute(sql:
                    "INSERT INTO shows (\(Self.showCatalogCols),isFavorite,favoritedAt) " +
                    "SELECT \(Self.showCatalogCols),0,NULL FROM seed.shows WHERE true " +
                    "ON CONFLICT(showId) DO UPDATE SET \(Self.showUpsertAssignments)")
                try db.execute(sql:
                    "INSERT INTO recordings (\(Self.recordingCols)) SELECT \(Self.recordingCols) FROM seed.recordings")
                try db.execute(sql:
                    "INSERT INTO dead_collections (\(Self.collectionCols)) SELECT \(Self.collectionCols) FROM seed.dead_collections")

                try rebuildSearchIndex(db)

                // data_version LAST: the health gate keys on this committed row.
                try db.execute(sql:
                    "INSERT INTO data_version (\(Self.dataVersionCols)) SELECT \(Self.dataVersionCols) FROM seed.data_version")

                return .commit
            }
        }

        // Favorites (and other CASCADE children) were never deleted; re-derive the
        // denormalized favorite flags on the freshly-upserted shows.
        try showDAO.reconcileFavoriteFlags()

        let (shows, recordings) = try database.read { db in
            (try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM shows") ?? 0,
             try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM recordings") ?? 0)
        }
        return Result(shows: shows, recordings: recordings)
    }

    // MARK: - FTS rebuild

    /// Rebuild `show_search` from the freshly-copied catalog, reproducing the exact
    /// text the JSON importer would have indexed (via `ShowSearchText`). Source-type
    /// tags are derived from each show's recordings.
    private func rebuildSearchIndex(_ db: Database) throws {
        // show_id -> set of UPPER(source_type)
        var sourceTypesByShow: [String: Set<String>] = [:]
        let recRows = try Row.fetchAll(db, sql:
            "SELECT show_id, source_type FROM recordings WHERE source_type IS NOT NULL")
        for row in recRows {
            guard let showId = row["show_id"] as String?,
                  let src = (row["source_type"] as String?)?.uppercased() else { continue }
            sourceTypesByShow[showId, default: []].insert(src)
        }

        // Materialize (showId, searchText) first so we are not writing to show_search
        // while a cursor over `shows` is still open on the same connection.
        var entries: [(String, String)] = []
        let showRows = try Row.fetchAll(db, sql:
            "SELECT showId, date, venueName, locationRaw, memberList, songList, averageRating, totalReviews FROM shows")
        for row in showRows {
            let showId = row["showId"] as String
            let searchText = ShowSearchText.build(
                date: row["date"] as String,
                venue: row["venueName"] as String,
                locationRaw: row["locationRaw"] as String?,
                memberListCsv: row["memberList"] as String?,
                songListCsv: row["songList"] as String?,
                sourceTypeKeys: sourceTypesByShow[showId] ?? [],
                avgRating: (row["averageRating"] as Double?) ?? 0.0,
                totalReviews: (row["totalReviews"] as Int?) ?? 0
            )
            entries.append((showId, searchText))
        }

        for (showId, searchText) in entries {
            try db.execute(
                sql: "INSERT INTO show_search (showId, searchText) VALUES (?, ?)",
                arguments: [showId, searchText]
            )
        }
    }

    // MARK: - Validation

    private func validateSeedFile(_ url: URL) throws {
        let fm = FileManager.default
        guard fm.isReadableFile(atPath: url.path) else {
            throw ImportError.seedInvalid("file missing or unreadable")
        }
        guard let handle = try? FileHandle(forReadingFrom: url) else {
            throw ImportError.seedInvalid("could not open file")
        }
        defer { try? handle.close() }
        let header = handle.readData(ofLength: 16)
        let sqliteMagic = Data("SQLite format 3\u{0}".utf8)
        guard header == sqliteMagic else {
            throw ImportError.seedInvalid("not a valid SQLite database")
        }
    }
}
