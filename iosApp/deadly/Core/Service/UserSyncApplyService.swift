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
    private let showReviewDAO: ShowReviewDAO
    private let showPlayerTagDAO: ShowPlayerTagDAO
    private let recordingPreferenceDAO: RecordingPreferenceDAO
    private let backlogDAO: BacklogDAO
    private let showDAO: ShowDAO
    private let authService: AuthService

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
        let reviewsScanned: Int
        let reviewsApplied: Int
        let reviewsSkippedLocalNewer: Int
        let reviewsSkippedMissingShow: Int
        // Show-queue rows the server is missing or has an older copy of, found
        // while applying the pull. The app re-pushes these so a dropped
        // add/remove event self-heals on the next foreground (anti-entropy).
        var backlogPushIds: [String] = []
    }

    enum ApplyError: Error {
        case notSignedIn
    }

    init(
        apiClient: UserSyncAPIClient,
        favoritesDAO: FavoritesDAO,
        favoriteSongDAO: FavoriteSongDAO,
        showReviewDAO: ShowReviewDAO,
        showPlayerTagDAO: ShowPlayerTagDAO,
        recordingPreferenceDAO: RecordingPreferenceDAO,
        backlogDAO: BacklogDAO,
        showDAO: ShowDAO,
        authService: AuthService
    ) {
        self.apiClient = apiClient
        self.favoritesDAO = favoritesDAO
        self.favoriteSongDAO = favoriteSongDAO
        self.showReviewDAO = showReviewDAO
        self.showPlayerTagDAO = showPlayerTagDAO
        self.recordingPreferenceDAO = recordingPreferenceDAO
        self.backlogDAO = backlogDAO
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
            let reviews = try applyReviews(backup.reviews)
            applyRecordingPreferences(backup.recordingPreferences)
            let backlogPushIds = applyBacklog(backup.backlog ?? [])
            return ApplyResult(
                favoriteShowsScanned: shows.scanned,
                favoriteShowsApplied: shows.applied,
                favoriteShowsSkippedLocalNewer: shows.skippedLocalNewer,
                favoriteShowsSkippedMissingShow: shows.skippedMissingShow,
                favoriteSongsScanned: songs.scanned,
                favoriteSongsApplied: songs.applied,
                favoriteSongsSkippedLocalNewer: songs.skippedLocalNewer,
                favoriteSongsSkippedMissingShow: songs.skippedMissingShow,
                reviewsScanned: reviews.scanned,
                reviewsApplied: reviews.applied,
                reviewsSkippedLocalNewer: reviews.skippedLocalNewer,
                reviewsSkippedMissingShow: reviews.skippedMissingShow,
                backlogPushIds: backlogPushIds
            )
        }
        inFlight = task
        do {
            let result = try await task.value
            print("[UserSyncApply] pull[\(reason)] ok: \(result)")
            // FavoritesServiceImpl observes the underlying tables — the UI
            // re-publishes automatically on apply writes. No explicit refresh
            // needed here (was the imperative-cache band-aid).
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

    private func applyReviews(_ remote: [SyncReviewV3]) throws -> Counts {
        var c = Counts()
        c.scanned = remote.count

        for dto in remote {
            // Reviews FK to shows — skip ones missing from the local catalog.
            if (try? showDAO.fetchById(dto.showId)) == nil {
                c.skippedMissingShow += 1
                continue
            }

            let local: ShowReviewRecord?
            do {
                local = try showReviewDAO.fetchByShowIdIncludingTombstones(dto.showId)
            } catch {
                print("[UserSyncApply] review read failed for \(dto.showId): \(error.localizedDescription)")
                continue
            }

            let remoteUpdatedMs = dto.updatedAt * 1000
            if let local, local.updatedAt >= remoteUpdatedMs {
                c.skippedLocalNewer += 1
                continue
            }

            let record = ShowReviewRecord(
                showId: dto.showId,
                notes: dto.notes,
                customRating: dto.overallRating,
                recordingQuality: dto.recordingQuality,
                playingQuality: dto.playingQuality,
                reviewedRecordingId: dto.reviewedRecordingId,
                createdAt: local?.createdAt ?? remoteUpdatedMs,
                updatedAt: remoteUpdatedMs,
                deletedAt: dto.deletedAt.map { $0 * 1000 }
            )
            do {
                try showReviewDAO.upsert(record)
                // Player tags travel with the review — replace the local set
                // (skip when the review is tombstoned; there are no tags then).
                try showPlayerTagDAO.removeForShow(dto.showId)
                if dto.deletedAt == nil, let tags = dto.playerTags {
                    for tag in tags {
                        try showPlayerTagDAO.upsert(ShowPlayerTagRecord(
                            id: nil,
                            showId: dto.showId,
                            playerName: tag.playerName,
                            instruments: tag.instruments,
                            isStandout: tag.isStandout,
                            notes: tag.notes,
                            createdAt: remoteUpdatedMs
                        ))
                    }
                }
                c.applied += 1
            } catch let error as DatabaseError where error.resultCode == .SQLITE_CONSTRAINT {
                print("[UserSyncApply] skip review \(dto.showId): constraint")
                c.skippedMissingShow += 1
            } catch {
                print("[UserSyncApply] apply review \(dto.showId) failed: \(error.localizedDescription)")
            }
        }
        return c
    }

    // Recording prefs are one row per show keyed by showId, FK'd to shows.
    // LWW on updatedAt; a remote tombstone deletes the local row. iOS keeps no
    // local tombstone column, so a clear is a hard delete.
    private func applyRecordingPreferences(_ remote: [SyncRecordingPrefV3]) {
        var applied = 0, skippedLocalNewer = 0, skippedMissingShow = 0

        for dto in remote {
            if (try? showDAO.fetchById(dto.showId)) == nil {
                skippedMissingShow += 1
                continue
            }

            let local = try? recordingPreferenceDAO.fetch(dto.showId)
            let remoteUpdatedMs = dto.updatedAt * 1000
            if let local, local.updatedAt >= remoteUpdatedMs {
                skippedLocalNewer += 1
                continue
            }

            do {
                if dto.deletedAt != nil {
                    try recordingPreferenceDAO.delete(dto.showId)
                } else {
                    try recordingPreferenceDAO.upsert(RecordingPreferenceRecord(
                        showId: dto.showId,
                        recordingId: dto.recordingId,
                        updatedAt: remoteUpdatedMs
                    ))
                }
                applied += 1
            } catch {
                print("[UserSyncApply] apply recording pref \(dto.showId) failed: \(error.localizedDescription)")
            }
        }
        print("[UserSyncApply] recording_prefs: scanned=\(remote.count) applied=\(applied) " +
              "skippedLocalNewer=\(skippedLocalNewer) skippedMissingShow=\(skippedMissingShow)")
    }

    // Backlog (Show Queue). One row per show keyed by showId; LWW on updatedAt,
    // a remote tombstone tombstones locally. No FK to shows, so unknown shows
    // are stored and simply ignored by the UI until the catalog knows them.
    /// Returns showIds the app should re-push: local rows the server is missing
    /// or has an older copy of (incl. tombstones). The reverse half of the merge
    /// — the pull carries server→local; this detects local→server divergence so a
    /// dropped add/remove event heals on the next pull.
    private func applyBacklog(_ remote: [SyncBacklogItemV3]) -> [String] {
        var applied = 0, skippedLocalNewer = 0
        for dto in remote {
            let local = (try? backlogDAO.fetchByIdIncludingTombstones(dto.showId)) ?? nil
            let remoteUpdatedMs = dto.updatedAt * 1000
            if let local, local.updatedAt >= remoteUpdatedMs {
                skippedLocalNewer += 1
                continue
            }
            let record = BacklogRecord(
                showId: dto.showId,
                position: Int64(dto.position),
                addedAt: dto.addedAt * 1000,
                updatedAt: remoteUpdatedMs,
                deletedAt: dto.deletedAt.map { $0 * 1000 }
            )
            do {
                try backlogDAO.applyFromSync(record)
                applied += 1
            } catch {
                print("[UserSyncApply] apply backlog \(dto.showId) failed: \(error.localizedDescription)")
            }
        }

        // Reverse delta: compare local rows (incl. tombstones) against the server
        // list at SECOND granularity. The wire format truncates ms, so a strict ms
        // comparison would flag every row as "local newer" forever and re-push on
        // every pull. A row needs pushing when the server lacks it or its
        // second-precision updatedAt is older than local's.
        let serverSecByShow = Dictionary(remote.map { ($0.showId, $0.updatedAt) },
                                         uniquingKeysWith: { a, _ in a })
        let pushIds: [String] = ((try? backlogDAO.fetchAllIncludingTombstones()) ?? [])
            .filter { row in
                guard let serverSec = serverSecByShow[row.showId] else {
                    // Server has no row. Only a LIVE local row is a real add it
                    // missed — a local tombstone the server never had is already
                    // converged (server-absent == not in queue), so don't re-push
                    // a DELETE that would just 404 on every foreground.
                    return row.deletedAt == nil
                }
                // Server has the row: push when local is strictly newer (covers
                // live edits and a remove the server hasn't seen).
                return (row.updatedAt / 1000) > serverSec
            }
            .map { $0.showId }

        print("[UserSyncApply] backlog: scanned=\(remote.count) applied=\(applied) skippedLocalNewer=\(skippedLocalNewer) toPush=\(pushIds.count)")
        return pushIds
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
