import Foundation

/// Bridges the local sync_outbox to the server. Issues 3a and 3b of
/// PLANS/mobile-server-sync.md.
///
/// Lifecycle:
/// - FavoritesServiceImpl calls `enqueueAndPush(showId:)` after every local
///   add/remove. ReviewService calls `enqueueAndPushFavoriteSong(localId:)`
///   after every track toggle. Both write an outbox row and kick off a
///   background flush; failures stay in the outbox for retry.
/// - The dev "Push pending" button calls `flushPending()` directly.
@MainActor
final class FavoritesPushService {
    private let outbox: SyncOutboxDAO
    private let favoritesDAO: FavoritesDAO
    private let favoriteSongDAO: FavoriteSongDAO
    private let apiClient: UserSyncAPIClient
    private let authService: AuthService
    /// Set by AppContainer after both services are constructed. Used to fire
    /// a pull after a successful flush so we reconcile changes other devices
    /// made during our window.
    weak var userSyncApplyService: UserSyncApplyService?

    init(
        outbox: SyncOutboxDAO,
        favoritesDAO: FavoritesDAO,
        favoriteSongDAO: FavoriteSongDAO,
        apiClient: UserSyncAPIClient,
        authService: AuthService
    ) {
        self.outbox = outbox
        self.favoritesDAO = favoritesDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.apiClient = apiClient
        self.authService = authService
    }

    // MARK: - Public API

    /// Enqueue a favorite-show change and try to flush. Fire-and-forget.
    func enqueueAndPush(showId: String) {
        enqueue(kind: SyncOutboxRecord.Kind.favoriteShow, refId: showId)
    }

    /// Enqueue a favorite-song change (by local row id) and try to flush.
    /// Fire-and-forget. The flusher reads the row's current state — including
    /// its tombstone marker — to decide PUT vs DELETE.
    func enqueueAndPushFavoriteSong(localId: Int64) {
        enqueue(kind: SyncOutboxRecord.Kind.favoriteSong, refId: String(localId))
    }

    private func enqueue(kind: String, refId: String) {
        do {
            try outbox.enqueue(kind: kind, refId: refId)
        } catch {
            return
        }
        Task { [weak self] in
            _ = await self?.flushPending()
        }
    }

    struct PushResult: Sendable {
        let kind: String
        let refId: String
        let operation: String   // "PUT" or "DELETE"
        let success: Bool
        let error: String?
    }

    /// Drains all pending outbox entries (shows + songs) against the server.
    /// Returns a per-entry result for the dev UI. Safe to call repeatedly.
    func flushPending() async -> [PushResult] {
        guard authService.isSignedIn else { return [] }

        var results: [PushResult] = []
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.favoriteShow))
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.favoriteSong))

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

    private func flushKind(_ kind: String) async -> [PushResult] {
        let pending: [SyncOutboxRecord]
        do {
            pending = try outbox.fetchPending(kind: kind)
        } catch {
            return []
        }

        var results: [PushResult] = []
        for entry in pending {
            guard let entryId = entry.id else { continue }
            let outcome = await push(entry: entry)
            switch outcome {
            case .success(let operation):
                try? outbox.delete(id: entryId)
                results.append(PushResult(kind: kind, refId: entry.refId, operation: operation, success: true, error: nil))
            case .failure(let operation, let error):
                try? outbox.recordFailure(id: entryId, error: error)
                results.append(PushResult(kind: kind, refId: entry.refId, operation: operation, success: false, error: error))
            }
        }
        return results
    }

    func pendingCount() -> Int {
        let shows = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.favoriteShow)) ?? 0
        let songs = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.favoriteSong)) ?? 0
        return shows + songs
    }

    // MARK: - Internals

    private enum FlushOutcome {
        case success(operation: String)
        case failure(operation: String, error: String)
    }

    private func push(entry: SyncOutboxRecord) async -> FlushOutcome {
        switch entry.kind {
        case SyncOutboxRecord.Kind.favoriteShow:
            return await pushFavoriteShow(refId: entry.refId)
        case SyncOutboxRecord.Kind.favoriteSong:
            return await pushFavoriteSong(refId: entry.refId)
        default:
            return .success(operation: "NOOP")
        }
    }

    private func pushFavoriteShow(refId: String) async -> FlushOutcome {
        let row: FavoriteShowRecord?
        do {
            row = try favoritesDAO.fetchByIdIncludingTombstones(refId)
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }
        guard let row else { return .success(operation: "NOOP") }

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
                addedAt: row.addedToFavoritesAt / 1000,
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

    private func pushFavoriteSong(refId: String) async -> FlushOutcome {
        guard let localId = Int64(refId) else {
            return .failure(operation: "?", error: "invalid song refId: \(refId)")
        }
        let row: FavoriteSongRecord?
        do {
            row = try favoriteSongDAO.fetchByLocalIdIncludingTombstones(localId)
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }
        guard let row else { return .success(operation: "NOOP") }

        if row.deletedAt != nil {
            do {
                try await apiClient.deleteFavoriteSong(showId: row.showId, trackTitle: row.trackTitle)
                return .success(operation: "DELETE")
            } catch {
                return .failure(operation: "DELETE", error: error.localizedDescription)
            }
        } else {
            let dto = SyncFavoriteTrackV3(
                id: nil, // server keys by natural (showId, trackTitle); local id wouldn't match
                showId: row.showId,
                trackTitle: row.trackTitle,
                trackNumber: row.trackNumber,
                recordingId: row.recordingId,
                updatedAt: row.updatedAt / 1000,
                deletedAt: nil
            )
            do {
                try await apiClient.putFavoriteSong(dto)
                return .success(operation: "PUT")
            } catch {
                return .failure(operation: "PUT", error: error.localizedDescription)
            }
        }
    }
}
