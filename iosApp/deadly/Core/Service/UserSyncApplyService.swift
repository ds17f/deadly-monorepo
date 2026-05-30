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
    private let favoriteSongDAO: FavoriteSongDAO
    private let showDAO: ShowDAO
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
        let favoriteSongsScanned: Int
        let favoriteSongsApplied: Int
        let favoriteSongsSkippedLocalNewer: Int
        let favoriteSongsSkippedMissingShow: Int
    }

    enum ApplyError: Error {
        case notSignedIn
    }

    init(
        apiClient: UserSyncAPIClient,
        favoritesDAO: FavoritesDAO,
        favoriteSongDAO: FavoriteSongDAO,
        showDAO: ShowDAO,
        authService: AuthService
    ) {
        self.apiClient = apiClient
        self.favoritesDAO = favoritesDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.showDAO = showDAO
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
            let shows = try applyFavoriteShows(backup.favorites.shows)
            let songs = try applyFavoriteSongs(backup.favorites.tracks)
            return ApplyResult(
                favoriteShowsScanned: shows.scanned,
                favoriteShowsApplied: shows.applied,
                favoriteShowsSkippedLocalNewer: shows.skippedLocalNewer,
                favoriteShowsSkippedMissingShow: shows.skippedMissingShow,
                favoriteSongsScanned: songs.scanned,
                favoriteSongsApplied: songs.applied,
                favoriteSongsSkippedLocalNewer: songs.skippedLocalNewer,
                favoriteSongsSkippedMissingShow: songs.skippedMissingShow
            )
        }
        inFlight = task
        do {
            let result = try await task.value
            print("[UserSyncApply] pull[\(reason)] ok: \(result)")
            if result.favoriteShowsApplied > 0 || result.favoriteSongsApplied > 0 {
                favoritesService?.refreshWithLastSort()
            }
            return .success(result)
        } catch {
            print("[UserSyncApply] pull[\(reason)] failed: \(error.localizedDescription)")
            return .failure(error)
        }
    }

    // MARK: - Internals

    private struct Counts {
        var scanned = 0
        var applied = 0
        var skippedLocalNewer = 0
        var skippedMissingShow = 0
    }

    private func applyFavoriteShows(_ remote: [SyncFavoriteShowV3]) throws -> Counts {
        var c = Counts()
        c.scanned = remote.count

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
                c.skippedLocalNewer += 1
                continue
            }

            let record = makeRecord(from: dto, existing: local)
            do {
                try favoritesDAO.applyFromSync(record)
                c.applied += 1
            } catch let error as DatabaseError where error.resultCode == .SQLITE_CONSTRAINT {
                // FK to shows: the show isn't in the local catalog (older data
                // version). Skip — next data refresh will let this row import.
                print("[UserSyncApply] skip \(dto.showId): show not in local catalog")
                c.skippedMissingShow += 1
            } catch {
                print("[UserSyncApply] apply \(dto.showId) failed: \(error.localizedDescription)")
            }
        }
        return c
    }

    private func applyFavoriteSongs(_ remote: [SyncFavoriteTrackV3]) throws -> Counts {
        var c = Counts()
        c.scanned = remote.count

        for dto in remote {
            // Skip rows for shows we don't know about — keeps FK from blowing
            // up and avoids polluting the local table.
            if (try? showDAO.fetchById(dto.showId)) == nil {
                c.skippedMissingShow += 1
                continue
            }

            let local: FavoriteSongRecord?
            do {
                local = try favoriteSongDAO.fetchByLocalIdIncludingTombstones(forNaturalKey: dto)
            } catch {
                print("[UserSyncApply] song read failed for \(dto.showId)/\(dto.trackTitle): \(error.localizedDescription)")
                continue
            }

            let remoteUpdatedMs = dto.updatedAt * 1000
            if let local, local.updatedAt >= remoteUpdatedMs {
                c.skippedLocalNewer += 1
                continue
            }

            let record = FavoriteSongRecord(
                id: local?.id,
                showId: dto.showId,
                trackTitle: dto.trackTitle,
                trackNumber: dto.trackNumber,
                recordingId: dto.recordingId,
                createdAt: local?.createdAt ?? remoteUpdatedMs,
                updatedAt: remoteUpdatedMs,
                deletedAt: dto.deletedAt.map { $0 * 1000 }
            )
            do {
                try favoriteSongDAO.applyFromSync(record)
                c.applied += 1
            } catch let error as DatabaseError where error.resultCode == .SQLITE_CONSTRAINT {
                print("[UserSyncApply] skip song \(dto.showId)/\(dto.trackTitle): constraint")
                c.skippedMissingShow += 1
            } catch {
                print("[UserSyncApply] apply song \(dto.showId)/\(dto.trackTitle) failed: \(error.localizedDescription)")
            }
        }
        return c
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
