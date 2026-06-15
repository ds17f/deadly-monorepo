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
    private let recentShowDAO: RecentShowDAO
    private let showReviewDAO: ShowReviewDAO
    private let showPlayerTagDAO: ShowPlayerTagDAO
    private let recordingPreferenceDAO: RecordingPreferenceDAO
    private let backlogDAO: BacklogDAO
    private let apiClient: UserSyncAPIClient
    private let authService: AuthService

    /// Constant refId for the coalesced reorder push (only one is ever pending).
    private static let backlogReorderRef = "_order"
    /// Set by AppContainer after both services are constructed. Used to fire
    /// a pull after a successful flush so we reconcile changes other devices
    /// made during our window.
    weak var userSyncApplyService: UserSyncApplyService?

    init(
        outbox: SyncOutboxDAO,
        favoritesDAO: FavoritesDAO,
        favoriteSongDAO: FavoriteSongDAO,
        recentShowDAO: RecentShowDAO,
        showReviewDAO: ShowReviewDAO,
        showPlayerTagDAO: ShowPlayerTagDAO,
        recordingPreferenceDAO: RecordingPreferenceDAO,
        backlogDAO: BacklogDAO,
        apiClient: UserSyncAPIClient,
        authService: AuthService
    ) {
        self.outbox = outbox
        self.favoritesDAO = favoritesDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.recentShowDAO = recentShowDAO
        self.showReviewDAO = showReviewDAO
        self.showPlayerTagDAO = showPlayerTagDAO
        self.recordingPreferenceDAO = recordingPreferenceDAO
        self.backlogDAO = backlogDAO
        self.apiClient = apiClient
        self.authService = authService
    }

    /// Enqueue a backlog add/remove/pop (refId = showId) and flush.
    func enqueueAndPushBacklog(showId: String) {
        enqueue(kind: SyncOutboxRecord.Kind.backlogItem, refId: showId)
    }

    /// Enqueue a backlog reorder (single coalesced entry) and flush.
    func enqueueAndPushBacklogReorder() {
        enqueue(kind: SyncOutboxRecord.Kind.backlogReorder, refId: Self.backlogReorderRef)
    }

    /// Re-push a set of backlog rows the pull found diverged (local newer or
    /// missing on the server), then flush once. The anti-entropy backstop that
    /// heals dropped add/remove events. No-op when the set is empty.
    @discardableResult
    func reconcileBacklog(showIds: [String]) async -> [PushResult] {
        guard !showIds.isEmpty else { return [] }
        print("[FavoritesPush] reconcileBacklog: re-pushing \(showIds.count) diverged backlog rows")
        for showId in showIds {
            enqueueRow(kind: SyncOutboxRecord.Kind.backlogItem, refId: showId)
        }
        // Positions can have diverged too; coalesce a single reorder push so the
        // server order matches local after the rows land.
        enqueueRow(kind: SyncOutboxRecord.Kind.backlogReorder, refId: Self.backlogReorderRef)
        return await flushPending()
    }

    /// Enqueue every local favorite (shows + songs) plus the top recents,
    /// then flush. Backs both the one-time startup backfill and a manual
    /// "Sync now". Idempotent for favorites (server upserts by natural key);
    /// recents re-announce, which is cheap and self-corrects.
    @discardableResult
    func enqueueAllLocalAndFlush() async -> [PushResult] {
        let shows = (try? favoritesDAO.fetchAll()) ?? []
        for show in shows {
            enqueueRow(kind: SyncOutboxRecord.Kind.favoriteShow, refId: show.showId)
        }
        let songs = (try? favoriteSongDAO.fetchAll()) ?? []
        for song in songs {
            if let id = song.id {
                enqueueRow(kind: SyncOutboxRecord.Kind.favoriteSong, refId: String(id))
            }
        }
        let recents = (try? recentShowDAO.fetchRecent(limit: 4)) ?? []
        for record in recents {
            enqueueRow(kind: SyncOutboxRecord.Kind.recent, refId: record.showId)
        }
        let reviews = (try? showReviewDAO.fetchAll()) ?? []
        for review in reviews {
            enqueueRow(kind: SyncOutboxRecord.Kind.review, refId: review.showId)
        }
        let recordingPrefs = (try? recordingPreferenceDAO.fetchAll()) ?? []
        for pref in recordingPrefs {
            enqueueRow(kind: SyncOutboxRecord.Kind.recordingPref, refId: pref.showId)
        }
        let backlog = (try? backlogDAO.fetchAll()) ?? []
        for item in backlog {
            enqueueRow(kind: SyncOutboxRecord.Kind.backlogItem, refId: item.showId)
        }
        if !backlog.isEmpty {
            enqueueRow(kind: SyncOutboxRecord.Kind.backlogReorder, refId: Self.backlogReorderRef)
        }
        return await flushPending()
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

    /// Enqueue a recent-show play (refId is the showId). Fire-and-forget.
    func enqueueAndPushRecent(showId: String) {
        enqueue(kind: SyncOutboxRecord.Kind.recent, refId: showId)
    }

    /// Enqueue a review change (refId is the showId). Fire-and-forget. The
    /// flusher reads the review row + its player tags at push time; a tombstone
    /// becomes a DELETE.
    func enqueueAndPushReview(showId: String) {
        enqueue(kind: SyncOutboxRecord.Kind.review, refId: showId)
    }

    /// Enqueue a recording-preference change (refId is the showId).
    /// Fire-and-forget. The flusher reads the row at push time; an absent row
    /// becomes a DELETE.
    func enqueueAndPushRecordingPref(showId: String) {
        enqueue(kind: SyncOutboxRecord.Kind.recordingPref, refId: showId)
    }

    private func enqueueRow(kind: String, refId: String) {
        try? outbox.enqueue(kind: kind, refId: refId)
    }

    private func enqueue(kind: String, refId: String) {
        enqueueRow(kind: kind, refId: refId)
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
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.recent))
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.review))
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.recordingPref))
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.backlogItem))
        results.append(contentsOf: await flushKind(SyncOutboxRecord.Kind.backlogReorder))

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
        let recents = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.recent)) ?? 0
        let reviews = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.review)) ?? 0
        let recordingPrefs = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.recordingPref)) ?? 0
        let backlogItems = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.backlogItem)) ?? 0
        let backlogReorders = (try? outbox.pendingCount(kind: SyncOutboxRecord.Kind.backlogReorder)) ?? 0
        return shows + songs + recents + reviews + recordingPrefs + backlogItems + backlogReorders
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
        case SyncOutboxRecord.Kind.recent:
            return await pushRecent(refId: entry.refId)
        case SyncOutboxRecord.Kind.review:
            return await pushReview(refId: entry.refId)
        case SyncOutboxRecord.Kind.recordingPref:
            return await pushRecordingPref(refId: entry.refId)
        case SyncOutboxRecord.Kind.backlogItem:
            return await pushBacklogItem(refId: entry.refId)
        case SyncOutboxRecord.Kind.backlogReorder:
            return await pushBacklogReorder()
        default:
            return .success(operation: "NOOP")
        }
    }

    // Backlog item push by showId. Read the row (incl. tombstones) at push
    // time: a live row is a PUT, a tombstoned or absent row is a DELETE.
    private func pushBacklogItem(refId: String) async -> FlushOutcome {
        let row: BacklogRecord?
        do {
            row = try backlogDAO.fetchByIdIncludingTombstones(refId)
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }

        if let row, row.deletedAt == nil {
            let item = SyncBacklogItemV3(
                showId: row.showId,
                position: Int(row.position),
                addedAt: row.addedAt / 1000,
                updatedAt: row.updatedAt / 1000,
                deletedAt: nil
            )
            do {
                try await apiClient.putBacklogItem(item)
                return .success(operation: "PUT")
            } catch {
                return .failure(operation: "PUT", error: error.localizedDescription)
            }
        } else {
            do {
                try await apiClient.deleteBacklogItem(showId: refId)
                return .success(operation: "DELETE")
            } catch {
                return .failure(operation: "DELETE", error: error.localizedDescription)
            }
        }
    }

    // Reorder push: read the current live order and PUT the whole list.
    private func pushBacklogReorder() async -> FlushOutcome {
        let showIds: [String]
        do {
            showIds = try backlogDAO.fetchAll().map { $0.showId }
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }
        do {
            try await apiClient.reorderBacklog(showIds: showIds)
            return .success(operation: "PUT")
        } catch {
            return .failure(operation: "PUT", error: error.localizedDescription)
        }
    }

    // Recording prefs push by showId. The flusher reads the current row at
    // push time: a live row is a PUT, an absent row is a DELETE.
    private func pushRecordingPref(refId: String) async -> FlushOutcome {
        let row: RecordingPreferenceRecord?
        do {
            row = try recordingPreferenceDAO.fetch(refId)
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }

        if let row {
            do {
                try await apiClient.putRecordingPref(showId: row.showId, recordingId: row.recordingId)
                return .success(operation: "PUT")
            } catch {
                return .failure(operation: "PUT", error: error.localizedDescription)
            }
        } else {
            do {
                try await apiClient.deleteRecordingPref(showId: refId)
                return .success(operation: "DELETE")
            } catch {
                return .failure(operation: "DELETE", error: error.localizedDescription)
            }
        }
    }

    // Recents are announce-on-play: refId is the showId and the server stamps
    // the time, so there's no local row to read or tombstone to honor (v0).
    private func pushRecent(refId: String) async -> FlushOutcome {
        do {
            try await apiClient.putRecent(showId: refId)
            return .success(operation: "PUT")
        } catch {
            return .failure(operation: "PUT", error: error.localizedDescription)
        }
    }

    // Reviews push by showId. Player tags travel with the review (the server
    // replaces all tags for the show on PUT), so we gather them at push time.
    // A tombstoned review row becomes a DELETE.
    private func pushReview(refId: String) async -> FlushOutcome {
        let row: ShowReviewRecord?
        do {
            row = try showReviewDAO.fetchByShowIdIncludingTombstones(refId)
        } catch {
            return .failure(operation: "?", error: "local read failed: \(error.localizedDescription)")
        }
        guard let row else { return .success(operation: "NOOP") }

        if row.deletedAt != nil {
            do {
                try await apiClient.deleteReview(showId: row.showId)
                return .success(operation: "DELETE")
            } catch {
                return .failure(operation: "DELETE", error: error.localizedDescription)
            }
        } else {
            let tags = ((try? showPlayerTagDAO.fetchForShow(row.showId)) ?? []).map {
                SyncPlayerTagV3(
                    playerName: $0.playerName,
                    instruments: $0.instruments,
                    isStandout: $0.isStandout,
                    notes: $0.notes
                )
            }
            let dto = SyncReviewV3(
                showId: row.showId,
                notes: row.notes,
                overallRating: row.customRating,
                recordingQuality: row.recordingQuality,
                playingQuality: row.playingQuality,
                reviewedRecordingId: row.reviewedRecordingId,
                playerTags: tags.isEmpty ? nil : tags,
                updatedAt: row.updatedAt / 1000,
                deletedAt: nil
            )
            do {
                try await apiClient.putReview(dto)
                return .success(operation: "PUT")
            } catch {
                return .failure(operation: "PUT", error: error.localizedDescription)
            }
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
