import Foundation

/// URLSession-based API client for thedeadly.app backend.
/// Injects Authorization header when a token is available.
struct APIClient: Sendable {
    private let baseURL: String
    private let authToken: String?
    private let session: URLSession

    init(appPreferences: AppPreferences, authToken: String?, session: URLSession = .shared) {
        self.baseURL = appPreferences.apiBaseUrl
        self.authToken = authToken
        self.session = session
    }

    func get(path: String) async throws -> (Data, URLResponse) {
        var request = URLRequest(url: URL(string: "\(baseURL)\(path)")!)
        request.httpMethod = "GET"
        applyHeaders(&request)
        return try await session.data(for: request)
    }

    func post(path: String, body: [String: String]) async throws -> (Data, URLResponse) {
        var request = URLRequest(url: URL(string: "\(baseURL)\(path)")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        applyHeaders(&request)
        request.httpBody = try JSONEncoder().encode(body)
        return try await session.data(for: request)
    }

    private func applyHeaders(_ request: inout URLRequest) {
        if let authToken {
            request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        }
    }
}
