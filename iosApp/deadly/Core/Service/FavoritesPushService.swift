import Foundation

/// Bridges the local sync_outbox to the server. Issue 3a of
/// PLANS/mobile-server-sync.md.
///
/// Lifecycle:
/// - FavoritesServiceImpl calls `enqueueAndPush(showId:)` after every local
///   add/remove. That writes a row to sync_outbox and kicks off a background
///   flush. Failures stay in the outbox for retry.
/// - The dev "Push pending" button calls `flushPending()` directly.
@MainActor
final class FavoritesPushService {
    private let outbox: SyncOutboxDAO
    private let favoritesDAO: FavoritesDAO
    private let apiClient: UserSyncAPIClient
    private let authService: AuthService
    /// Set by AppContainer after both services are constructed. Used to fire
    /// a pull after a successful flush so we reconcile changes other devices
    /// made during our window.
    weak var userSyncApplyService: UserSyncApplyService?

    init(
        outbox: SyncOutboxDAO,
        favoritesDAO: FavoritesDAO,
        apiClient: UserSyncAPIClient,
        authService: AuthService
    ) {
        self.outbox = outbox
        self.favoritesDAO = favoritesDAO
        self.apiClient = apiClient
        self.authService = authService
    }

    // MARK: - Public API

    /// Enqueue a favorite-show change and try to flush. Fire-and-forget.
    func enqueueAndPush(showId: String) {
        do {
            try outbox.enqueue(kind: SyncOutboxRecord.Kind.favoriteShow, refId: showId)
        } catch {
            // Outbox enqueue failing is unusual — log later when we have logging.
            return
        }
        Task { [weak self] in
            _ = await self?.flushPending()
        }
    }

    struct PushResult: Sendable {
        let refId: String
        let operation: String   // "PUT" or "DELETE"
        let success: Bool
        let error: String?
    }

    /// Drains all pending favorite_show outbox entries against the server.
    /// Returns a per-entry result for the dev UI. Safe to call repeatedly.
    func flushPending() async -> [PushResult] {
        guard authService.isSignedIn else { return [] }

        let pending: [SyncOutboxRecord]
        do {
            pending = try outbox.fetchPending(kind: SyncOutboxRecord.Kind.favoriteShow)
        } catch {
            return []
        }

        var results: [PushResult] = []
        for entry in pending {
            guard let entryId = entry.id else { continue }
            let result = await push(entry: entry)
            switch result {
            case .success(let operation):
                try? outbox.delete(id: entryId)
                results.append(PushResult(refId: entry.refId, operation: operation, success: true, error: nil))
            case .failure(let operation, let error):
                try? outbox.recordFailure(id: entryId, error: error)
                results.append(PushResult(refId: entry.refId, operation: operation, success: false, error: error))
            }
        }
        // Reconcile after pushing — the server may have learned about changes
        // from other devices during our window. Only fire when something
        // actually shipped, so an empty flush stays free.
        if results.contains(where: { $0.success && $0.operation != "NOOP" }) {
            Task { [weak userSyncApplyService] in
                _ = await userSyncApplyService?.pullAndApply(reason: "after_push_flush")
            }
        }
        return results
    }

    func pendingCount() -> Int {
        (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.favoriteShow)) ?? 0
    }

    // MARK: - Internals

    private enum FlushOutcome {
        case success(operation: String)
        case failure(operation: String, error: String)
    }

    private func push(entry: SyncOutboxRecord) async -> FlushOutcome {
        let row: FavoriteShowRecord?
        do {
            row = try favoritesDAO.fetchByIdIncludingTombstones(entry.refId)
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }

        guard let row else {
            // Row was hard-deleted somewhere. Nothing to push — succeed and drop entry.
            return .success(operation: "NOOP")
        }

        if row.deletedAt != nil {
            do {
                try await apiClient.deleteFavoriteShow(showId: row.showId)
                return .success(operation: "DELETE")
            } catch {
                return .failure(operation: "DELETE", error: error.localizedDescription)
            }
        } else {
            let dto = SyncFavoriteShowV3(
                showId: row.showId,
                addedAt: row.addedToFavoritesAt / 1000, // server uses seconds
                isPinned: row.isPinned,
                lastAccessedAt: row.lastAccessedAt.map { $0 / 1000 },
                tags: row.tags?.split(separator: ",").map(String.init),
                notes: row.notes,
                preferredRecordingId: row.preferredRecordingId,
                downloadedRecordingId: row.downloadedRecordingId,
                downloadedFormat: row.downloadedFormat,
                recordingQuality: row.recordingQuality,
                playingQuality: row.playingQuality,
                customRating: row.customRating,
                updatedAt: row.updatedAt / 1000,
                deletedAt: nil
            )
            do {
                try await apiClient.putFavoriteShow(dto)
                return .success(operation: "PUT")
            } catch {
                return .failure(operation: "PUT", error: error.localizedDescription)
            }
        }
    }
}
