import Foundation
import GRDB

/// Pulls the V3 backup from the server and merges it into the local DB
/// using last-writer-wins by updated_at.
///
/// Scope: favorite shows only in this pass (mirrors the push side, which
/// also only covers favorite shows). See PLANS/mobile-server-sync.md.
///
/// Apply writes go through the DAO's [applyFromSync] method — they MUST NOT
/// call into [FavoritesPushService.enqueueAndPush], or every pull would
/// re-push what we just imported.
@MainActor
final class UserSyncApplyService {
    private let apiClient: UserSyncAPIClient
    private let favoritesDAO: FavoritesDAO
    private let authService: AuthService
    /// Set by AppContainer after both services are constructed. Used to nudge
    /// the favorites screen to re-fetch after we land remote changes — the
    /// service holds an imperative cache, not a GRDB observation.
    weak var favoritesService: FavoritesServiceImpl?

    /// Serialize concurrent applies so two triggers (foreground + post-push)
    /// don't race.
    private var inFlight: Task<ApplyResult, Error>?

    struct ApplyResult: Sendable {
        let favoriteShowsScanned: Int
        let favoriteShowsApplied: Int
        let favoriteShowsSkippedLocalNewer: Int
        let favoriteShowsSkippedMissingShow: Int
    }

    enum ApplyError: Error {
        case notSignedIn
    }

    init(
        apiClient: UserSyncAPIClient,
        favoritesDAO: FavoritesDAO,
        authService: AuthService
    ) {
        self.apiClient = apiClient
        self.favoritesDAO = favoritesDAO
        self.authService = authService
    }

    @discardableResult
    func pullAndApply(reason: String) async -> Result<ApplyResult, Error> {
        guard authService.isSignedIn else {
            return .failure(ApplyError.notSignedIn)
        }
        // Coalesce concurrent callers onto the same in-flight task.
        if let existing = inFlight {
            do { return .success(try await existing.value) } catch { return .failure(error) }
        }
        let task = Task<ApplyResult, Error> { [self] in
            defer { inFlight = nil }
            let backup = try await apiClient.pullFullBackup()
            return try applyFavoriteShows(backup.favorites.shows)
        }
        inFlight = task
        do {
            let result = try await task.value
            print("[UserSyncApply] pull[\(reason)] ok: \(result)")
            if result.favoriteShowsApplied > 0 {
                favoritesService?.refreshWithLastSort()
            }
            return .success(result)
        } catch {
            print("[UserSyncApply] pull[\(reason)] failed: \(error.localizedDescription)")
            return .failure(error)
        }
    }

    // MARK: - Internals

    private func applyFavoriteShows(_ remote: [SyncFavoriteShowV3]) throws -> ApplyResult {
        var applied = 0
        var skippedLocalNewer = 0
        var skippedMissingShow = 0

        for dto in remote {
            let local: FavoriteShowRecord?
            do {
                local = try favoritesDAO.fetchByIdIncludingTombstones(dto.showId)
            } catch {
                print("[UserSyncApply] read failed for \(dto.showId): \(error.localizedDescription)")
                continue
            }

            // LWW: keep local if its updatedAt is newer or equal. Server is in
            // seconds; local is in ms.
            let remoteUpdatedMs = dto.updatedAt * 1000
            if let local, local.updatedAt >= remoteUpdatedMs {
                skippedLocalNewer += 1
                continue
            }

            let record = makeRecord(from: dto, existing: local)
            do {
                try favoritesDAO.applyFromSync(record)
                applied += 1
            } catch let error as DatabaseError where error.resultCode == .SQLITE_CONSTRAINT {
                // FK to shows: the show isn't in the local catalog (older data
                // version). Skip — next data refresh will let this row import.
                print("[UserSyncApply] skip \(dto.showId): show not in local catalog")
                skippedMissingShow += 1
            } catch {
                print("[UserSyncApply] apply \(dto.showId) failed: \(error.localizedDescription)")
            }
        }

        return ApplyResult(
            favoriteShowsScanned: remote.count,
            favoriteShowsApplied: applied,
            favoriteShowsSkippedLocalNewer: skippedLocalNewer,
            favoriteShowsSkippedMissingShow: skippedMissingShow
        )
    }

    private func makeRecord(from dto: SyncFavoriteShowV3, existing: FavoriteShowRecord?) -> FavoriteShowRecord {
        FavoriteShowRecord(
            showId: dto.showId,
            addedToFavoritesAt: dto.addedAt * 1000,
            isPinned: dto.isPinned,
            notes: dto.notes,
            preferredRecordingId: dto.preferredRecordingId,
            // Downloads are device-local — keep whatever we already have rather
            // than overwriting with another device's download state.
            downloadedRecordingId: existing?.downloadedRecordingId ?? dto.downloadedRecordingId,
            downloadedFormat: existing?.downloadedFormat ?? dto.downloadedFormat,
            recordingQuality: dto.recordingQuality,
            playingQuality: dto.playingQuality,
            customRating: dto.customRating,
            lastAccessedAt: dto.lastAccessedAt.map { $0 * 1000 },
            tags: dto.tags?.joined(separator: ","),
            updatedAt: dto.updatedAt * 1000,
            deletedAt: dto.deletedAt.map { $0 * 1000 }
        )
    }
}
