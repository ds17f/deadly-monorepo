import Foundation

// MARK: - V3 Sync DTOs
//
// Mirror api/src/db/userdata.ts. Kept separate from BackupExportV3
// (FavoritesExportFormat.swift) because the file-export envelope is a
// subset — it omits recentShows / playbackPosition and several optional
// favorite fields. The sync wire format must be lossless.

struct SyncBackupV3: Codable {
    let version: Int
    let exportedAt: Int64
    let app: String
    let favorites: SyncFavorites
    let reviews: [SyncReviewV3]
    let recordingPreferences: [SyncRecordingPrefV3]
    let settings: SyncSettingsV3?
    let recentShows: [SyncRecentShowV3]?
    let playbackPosition: SyncPlaybackPositionV3?
}

struct SyncFavorites: Codable {
    let shows: [SyncFavoriteShowV3]
    let tracks: [SyncFavoriteTrackV3]
}

struct SyncFavoriteShowV3: Codable {
    let showId: String
    let addedAt: Int64
    let isPinned: Bool
    let lastAccessedAt: Int64?
    let tags: [String]?
    let notes: String?
    let preferredRecordingId: String?
    let downloadedRecordingId: String?
    let downloadedFormat: String?
    let recordingQuality: Int?
    let playingQuality: Int?
    let customRating: Double?
    let updatedAt: Int64
    let deletedAt: Int64?
}

struct SyncFavoriteTrackV3: Codable {
    let id: Int64?
    let showId: String
    let trackTitle: String
    let trackNumber: Int?
    let recordingId: String?
    let updatedAt: Int64
    let deletedAt: Int64?
}

struct SyncReviewV3: Codable {
    let showId: String
    let notes: String?
    let overallRating: Double?
    let recordingQuality: Int?
    let playingQuality: Int?
    let reviewedRecordingId: String?
    let playerTags: [SyncPlayerTagV3]?
    let updatedAt: Int64
    let deletedAt: Int64?
}

struct SyncPlayerTagV3: Codable {
    let playerName: String
    let instruments: String?
    let isStandout: Bool
    let notes: String?
}

struct SyncRecordingPrefV3: Codable {
    let showId: String
    let recordingId: String
    let updatedAt: Int64
    let deletedAt: Int64?
}

struct SyncSettingsV3: Codable {
    let includeShowsWithoutRecordings: Bool?
    let favoritesDisplayMode: String?
    let forceOnline: Bool?
    let sourceBadgeStyle: String?
    let shareAttachImage: Bool?
    let eqEnabled: Bool?
    let eqPreset: String?
    let eqBandLevels: String?
    let updatedAt: Int64
}

struct SyncRecentShowV3: Codable {
    let showId: String
    let lastPlayedAt: Int64
    let firstPlayedAt: Int64
    let totalPlayCount: Int
    let deletedAt: Int64?
}

struct SyncPlaybackPositionV3: Codable {
    let showId: String
    let recordingId: String
    let trackIndex: Int
    let positionMs: Int64
    let date: String?
    let venue: String?
    let location: String?
    let updatedAt: Int64
}

// MARK: - Errors

enum UserSyncError: LocalizedError {
    case notSignedIn
    case http(statusCode: Int, body: String)

    var errorDescription: String? {
        switch self {
        case .notSignedIn: return "Not signed in"
        case .http(let code, let body): return "HTTP \(code): \(body)"
        }
    }
}

// MARK: - Client

/// Typed client over /api/user/*. Reads the auth token from AuthService
/// at call time so token refresh / sign-out propagate without rewiring.
@MainActor
struct UserSyncAPIClient {
    private let appPreferences: AppPreferences
    private let authService: AuthService

    init(appPreferences: AppPreferences, authService: AuthService) {
        self.appPreferences = appPreferences
        self.authService = authService
    }

    func pullFullBackup() async throws -> SyncBackupV3 {
        let (data, response) = try await get(path: "/api/user/sync")
        try ensureOK(data: data, response: response)
        return try JSONDecoder().decode(SyncBackupV3.self, from: data)
    }

    // MARK: - Internals

    private func client() throws -> APIClient {
        guard let token = authService.token else { throw UserSyncError.notSignedIn }
        return APIClient(appPreferences: appPreferences, authToken: token)
    }

    private func get(path: String) async throws -> (Data, URLResponse) {
        try await client().get(path: path)
    }

    private func ensureOK(data: Data, response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { return }
        if !(200..<300).contains(http.statusCode) {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw UserSyncError.http(statusCode: http.statusCode, body: body)
        }
    }
}
