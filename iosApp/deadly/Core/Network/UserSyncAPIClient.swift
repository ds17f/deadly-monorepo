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

    func putFavoriteShow(_ show: SyncFavoriteShowV3) async throws {
        let body = try JSONEncoder().encode(show)
        let (data, response) = try await request(
            method: "PUT",
            path: "/api/user/favorites/shows/\(show.showId)",
            body: body
        )
        try ensureOK(data: data, response: response)
    }

    /// Announce a play. The server stamps last_played_at and bumps the count;
    /// no client timestamp or body is sent.
    func putRecent(showId: String) async throws {
        let (data, response) = try await request(
            method: "PUT",
            path: "/api/user/recent/\(showId)",
            body: nil
        )
        try ensureOK(data: data, response: response)
    }

    func deleteFavoriteShow(showId: String) async throws {
        let (data, response) = try await request(
            method: "DELETE",
            path: "/api/user/favorites/shows/\(showId)",
            body: nil
        )
        // 404 is fine — server already lacks the row (e.g., previous attempt
        // succeeded but we didn't get to record it).
        if let http = response as? HTTPURLResponse, http.statusCode == 404 { return }
        try ensureOK(data: data, response: response)
    }

    func putFavoriteSong(_ song: SyncFavoriteTrackV3) async throws {
        let body = try JSONEncoder().encode(song)
        let (data, response) = try await request(
            method: "PUT",
            path: "/api/user/favorites/songs",
            body: body
        )
        try ensureOK(data: data, response: response)
    }

    /// Delete by natural key. Server resolves the row by (user, showId, trackTitle);
    /// mobile clients don't track the server-side autoincrement id.
    func deleteFavoriteSong(showId: String, trackTitle: String) async throws {
        var components = URLComponents()
        components.path = "/api/user/favorites/songs"
        components.queryItems = [
            URLQueryItem(name: "showId", value: showId),
            URLQueryItem(name: "trackTitle", value: trackTitle),
        ]
        let path = components.percentEncodedPath + "?" + (components.percentEncodedQuery ?? "")
        let (data, response) = try await request(method: "DELETE", path: path, body: nil)
        if let http = response as? HTTPURLResponse, http.statusCode == 404 { return }
        try ensureOK(data: data, response: response)
    }

    // MARK: - Internals

    private func client() throws -> APIClient {
        guard let token = authService.token else { throw UserSyncError.notSignedIn }
        return APIClient(appPreferences: appPreferences, authToken: token)
    }

    private func get(path: String) async throws -> (Data, URLResponse) {
        try await client().get(path: path)
    }

    private func request(method: String, path: String, body: Data?) async throws -> (Data, URLResponse) {
        guard let token = authService.token else { throw UserSyncError.notSignedIn }
        let baseURL = appPreferences.apiBaseUrl
        var req = URLRequest(url: URL(string: "\(baseURL)\(path)")!)
        req.httpMethod = method
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if body != nil {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = body
        }
        return try await URLSession.shared.data(for: req)
    }

    private func ensureOK(data: Data, response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { return }
        if !(200..<300).contains(http.statusCode) {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw UserSyncError.http(statusCode: http.statusCode, body: body)
        }
    }
}
