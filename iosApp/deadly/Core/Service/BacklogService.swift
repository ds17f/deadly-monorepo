import Foundation
import GRDB
import os

/// The user's local-first backlog ("Up Next") — an ordered list of show ids
/// (ADR-0010 Amendment 2026-06-14). Mirrors the Android `BacklogRepository` op
/// set. Local-authoritative; sync (slice 4) ships the add/pop/move event.
///
/// Slice 1/2: store + entry points. No advance wiring, no sync yet.
@Observable
@MainActor
final class BacklogService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "BacklogService")
    private let dao: BacklogDAO

    /// Set by AppContainer after both are built. Each mutation enqueues a
    /// per-action push (slice 4); nil when signed out / before wiring.
    weak var pushService: FavoritesPushService?

    init(dao: BacklogDAO) {
        self.dao = dao
    }

    private var now: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Current backlog in play order (head first).
    func showIds() -> [String] {
        (try? dao.fetchAll().map { $0.showId }) ?? []
    }

    /// Next show id to play, without consuming it (advance *announce*).
    func peekHeadId() -> String? {
        (try? dao.peekHead())?.showId
    }

    /// Consume and return the head (advance *commit*); nil when empty.
    func popHead() -> String? {
        let popped = (try? dao.popHead(now: now)) ?? nil
        if let popped { pushService?.enqueueAndPushBacklog(showId: popped) }
        return popped
    }

    func contains(_ showId: String) -> Bool {
        (try? dao.contains(showId)) ?? false
    }

    /// Append a show to the bottom (clearing any tombstone).
    func addToBottom(_ showId: String) {
        do {
            try dao.addToBottom(showId, now: now)
            pushService?.enqueueAndPushBacklog(showId: showId)
        }
        catch { logger.error("addToBottom failed: \(error)") }
    }

    func remove(_ showId: String) {
        do {
            try dao.remove(showId, now: now)
            pushService?.enqueueAndPushBacklog(showId: showId)
        }
        catch { logger.error("remove failed: \(error)") }
    }

    /// Rewrite the order to exactly `orderedShowIds` (drag-to-reorder).
    func reorder(_ orderedShowIds: [String]) {
        do {
            try dao.reorder(orderedShowIds, now: now)
            pushService?.enqueueAndPushBacklogReorder()
        }
        catch { logger.error("reorder failed: \(error)") }
    }

    func clear() {
        let cleared = showIds()
        do {
            try dao.clear(now: now)
            cleared.forEach { pushService?.enqueueAndPushBacklog(showId: $0) }
        }
        catch { logger.error("clear failed: \(error)") }
    }

    func count() -> Int {
        (try? dao.count()) ?? 0
    }

    /// Live backlog (head first) for the Up Next screen.
    func observe() -> AsyncValueObservation<[BacklogRecord]> {
        dao.database.observe(dao.observeAll())
    }
}
