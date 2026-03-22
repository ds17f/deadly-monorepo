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

    /// Keychain keys are namespaced by environment so tokens don't collide.
    private var tokenKeychainKey: String {
        "auth_token_\(appPreferences.serverEnvironment)"
    }

    private var userKeychainKey: String {
        "auth_user_\(appPreferences.serverEnvironment)"
    }

    init(appPreferences: AppPreferences) {
        self.appPreferences = appPreferences
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

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]

        let authorization = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<ASAuthorization, Error>) in
            appleSignInContinuation = continuation
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.performRequests()
        }

        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let idTokenData = credential.identityToken,
              let idToken = String(data: idTokenData, encoding: .utf8) else {
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

        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: viewController)
        guard let idToken = result.user.idToken?.tokenString else {
            throw AuthError.missingCredential
        }

        try await exchangeToken(provider: "google", idToken: idToken, name: nil)
    }

    // MARK: - Token Exchange

    private func exchangeToken(provider: String, idToken: String, name: String?) async throws {
        let apiClient = APIClient(appPreferences: appPreferences, authToken: nil)
        var body: [String: String] = ["provider": provider, "idToken": idToken]
        if let name { body["name"] = name }

        let (data, response) = try await apiClient.post(path: "/api/auth/mobile/token", body: body)

        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw AuthError.serverError(errorBody)
        }

        struct TokenResponse: Codable {
            let token: String
            let user: AuthUser
        }

        let tokenResponse = try JSONDecoder().decode(TokenResponse.self, from: data)
        currentUser = tokenResponse.user
        token = tokenResponse.token

        // Persist to Keychain
        if let tokenData = tokenResponse.token.data(using: .utf8) {
            KeychainHelper.save(key: tokenKeychainKey, data: tokenData)
        }
        if let userData = try? JSONEncoder().encode(tokenResponse.user) {
            KeychainHelper.save(key: userKeychainKey, data: userData)
        }
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
