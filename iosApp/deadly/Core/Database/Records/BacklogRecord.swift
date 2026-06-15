import GRDB

/// Flat DB record for the `backlog` ("Up Next") table — the user's local-first
/// play-next list (ADR-0010 Amendment 2026-06-14). One row per show (showId PK);
/// ordering is by `position` ascending, head = lowest position. `deletedAt`
/// tombstones a removed show so per-action sync (slice 4) can push removes.
struct BacklogRecord: Codable, Sendable, Equatable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "backlog"

    var showId: String
    var position: Int64
    var addedAt: Int64
    /// LWW comparator for per-action sync (slice 4); bumped on every mutation.
    var updatedAt: Int64 = 0
    var deletedAt: Int64?
}
