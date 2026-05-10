import AuthenticationServices
import Foundation
import GoogleSignIn

/// Manages authentication state: sign-in via Apple/Google, token storage, user fetch.
@Observable
@MainActor
final class AuthService: NSObject {
    private(set) var currentUser: AuthUser?
    private(set) var isLoading = false

    var isSignedIn: Bool { token != nil }

    private(set) var token: String?

    private let appPreferences: AppPreferences
    private let analyticsService: AnalyticsService?

    /// Keychain keys are namespaced by environment so tokens don't collide.
    private var tokenKeychainKey: String {
        "auth_token_\(appPreferences.serverEnvironment)"
    }

    private var userKeychainKey: String {
        "auth_user_\(appPreferences.serverEnvironment)"
    }

    init(appPreferences: AppPreferences, analyticsService: AnalyticsService? = nil) {
        self.appPreferences = appPreferences
        self.analyticsService = analyticsService
        super.init()
        restoreSession()
    }

    // MARK: - Session Restore

    private func restoreSession() {
        guard let tokenData = KeychainHelper.load(key: tokenKeychainKey),
              let storedToken = String(data: tokenData, encoding: .utf8) else {
            return
        }
        token = storedToken

        // Restore cached user
        if let userData = KeychainHelper.load(key: userKeychainKey) {
            currentUser = try? JSONDecoder().decode(AuthUser.self, from: userData)
        }
    }

    /// Call when the environment changes so the service picks up the right token.
    func onEnvironmentChanged() {
        currentUser = nil
        token = nil
        restoreSession()
    }

    /// Fetch a Bearer token from the custom server's dev-token endpoint.
    func fetchDevToken() async {
        let email = appPreferences.customDevEmail
        let baseUrl = appPreferences.customServerUrl
        guard !email.isEmpty, !baseUrl.isEmpty,
              let encoded = email.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(baseUrl)/api/auth/dev-token?email=\(encoded)") else {
            return
        }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return
            }
            struct DevTokenResponse: Codable { let token: String }
            let tokenResponse = try JSONDecoder().decode(DevTokenResponse.self, from: data)
            token = tokenResponse.token
            if let tokenData = tokenResponse.token.data(using: .utf8) {
                KeychainHelper.save(key: tokenKeychainKey, data: tokenData)
            }
            await fetchCurrentUser()
        } catch {
            // Network error — dev token not available
        }
    }

    // MARK: - Sign In with Apple

    private var appleSignInContinuation: CheckedContinuation<ASAuthorization, Error>?

    func signInWithApple() async throws {
        isLoading = true
        defer { isLoading = false }
        trackAuthEvent("sign_in_attempt", provider: "apple")

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]

        let authorization: ASAuthorization
        do {
            authorization = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<ASAuthorization, Error>) in
                appleSignInContinuation = continuation
                let controller = ASAuthorizationController(authorizationRequests: [request])
                controller.delegate = self
                controller.performRequests()
            }
        } catch {
            trackAuthEvent("sign_in_failure", provider: "apple", reason: classifyAppleError(error))
            throw error
        }

        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let idTokenData = credential.identityToken,
              let idToken = String(data: idTokenData, encoding: .utf8) else {
            trackAuthEvent("sign_in_failure", provider: "apple", reason: "invalid_credential")
            throw AuthError.missingCredential
        }

        // Apple sends the name only on first authorization
        var name: String?
        if let fullName = credential.fullName {
            let parts = [fullName.givenName, fullName.familyName].compactMap { $0 }
            if !parts.isEmpty { name = parts.joined(separator: " ") }
        }

        try await exchangeToken(provider: "apple", idToken: idToken, name: name)
    }

    // MARK: - Sign In with Google

    func signInWithGoogle(presenting viewController: UIViewController) async throws {
        isLoading = true
        defer { isLoading = false }
        trackAuthEvent("sign_in_attempt", provider: "google")

        let result: GIDSignInResult
        do {
            result = try await GIDSignIn.sharedInstance.signIn(withPresenting: viewController)
        } catch {
            trackAuthEvent("sign_in_failure", provider: "google", reason: classifyGoogleError(error))
            throw error
        }
        guard let idToken = result.user.idToken?.tokenString else {
            trackAuthEvent("sign_in_failure", provider: "google", reason: "invalid_credential")
            throw AuthError.missingCredential
        }

        try await exchangeToken(provider: "google", idToken: idToken, name: nil)
    }

    // MARK: - Token Exchange

    private func exchangeToken(provider: String, idToken: String, name: String?) async throws {
        let apiClient = APIClient(appPreferences: appPreferences, authToken: nil)
        var body: [String: String] = ["provider": provider, "idToken": idToken]
        if let name { body["name"] = name }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await apiClient.post(path: "/api/auth/mobile/token", body: body)
        } catch {
            trackAuthEvent("sign_in_failure", provider: provider, reason: "network")
            throw error
        }

        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            trackAuthEvent("sign_in_failure", provider: provider, reason: "server_\(statusCode)")
            throw AuthError.serverError(errorBody)
        }

        struct TokenResponse: Codable {
            let token: String
            let user: AuthUser
        }

        let tokenResponse: TokenResponse
        do {
            tokenResponse = try JSONDecoder().decode(TokenResponse.self, from: data)
        } catch {
            trackAuthEvent("sign_in_failure", provider: provider, reason: "decode_error")
            throw error
        }
        currentUser = tokenResponse.user
        token = tokenResponse.token

        // Persist to Keychain
        if let tokenData = tokenResponse.token.data(using: .utf8) {
            KeychainHelper.save(key: tokenKeychainKey, data: tokenData)
        }
        if let userData = try? JSONEncoder().encode(tokenResponse.user) {
            KeychainHelper.save(key: userKeychainKey, data: userData)
        }
        trackAuthEvent("sign_in_success", provider: provider)
    }

    private func trackAuthEvent(_ feature: String, provider: String, reason: String? = nil) {
        var props: [String: Any] = [
            "feature": feature,
            "category": "account",
            "provider": provider,
        ]
        if let reason { props["error_reason"] = reason }
        analyticsService?.track("feature_use", props: props)
    }

    /// Map Apple sign-in errors to a small, sanitized vocabulary so we never
    /// log provider-side strings (which can include user emails) into analytics.
    private func classifyAppleError(_ error: Error) -> String {
        if let asError = error as? ASAuthorizationError {
            switch asError.code {
            case .canceled: return "cancelled"
            case .invalidResponse, .notHandled, .failed: return "apple_failed"
            case .notInteractive: return "no_credential"
            default: return "unknown"
            }
        }
        if (error as NSError).domain == NSURLErrorDomain { return "network" }
        return "unknown"
    }

    /// Same intent as `classifyAppleError` for the Google SDK path.
    private func classifyGoogleError(_ error: Error) -> String {
        let nsError = error as NSError
        if nsError.domain == kGIDSignInErrorDomain {
            switch GIDSignInError.Code(rawValue: nsError.code) {
            case .canceled: return "cancelled"
            case .hasNoAuthInKeychain: return "no_credential"
            default: return "google_failed"
            }
        }
        if nsError.domain == NSURLErrorDomain { return "network" }
        return "unknown"
    }

    // MARK: - Sign Out

    func signOut() {
        KeychainHelper.delete(key: tokenKeychainKey)
        KeychainHelper.delete(key: userKeychainKey)
        token = nil
        currentUser = nil
    }

    // MARK: - Fetch Current User

    func fetchCurrentUser() async {
        guard let token else { return }
        let apiClient = APIClient(appPreferences: appPreferences, authToken: token)
        do {
            let (data, response) = try await apiClient.get(path: "/api/auth/me")
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return
            }
            currentUser = try JSONDecoder().decode(AuthUser.self, from: data)
            // Update cached user
            if let userData = try? JSONEncoder().encode(currentUser) {
                KeychainHelper.save(key: userKeychainKey, data: userData)
            }
        } catch {
            // Network error — keep existing cached state
        }
    }

}

// MARK: - ASAuthorizationControllerDelegate

extension AuthService: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        Task { @MainActor in
            appleSignInContinuation?.resume(returning: authorization)
            appleSignInContinuation = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        Task { @MainActor in
            appleSignInContinuation?.resume(throwing: error)
            appleSignInContinuation = nil
        }
    }
}

// MARK: - AuthError

enum AuthError: LocalizedError {
    case missingCredential
    case serverError(String)

    var errorDescription: String? {
        switch self {
        case .missingCredential:
            return "Could not get credentials from the provider."
        case .serverError(let message):
            return "Server error: \(message)"
        }
    }
}
