import Foundation

/// Fetches the public, cacheable notifications feed and merges it into the
/// [NotificationStore]. Reuses [APIClient] with no auth token — the endpoint is
/// public (global content isn't user-specific), so the fetch fires regardless
/// of sign-in state. Mirrors UserSyncAPIClient minus the Authorization header.
@MainActor
final class NotificationService {
    private let appPreferences: AppPreferences
    let store: NotificationStore
    private let analytics: AnalyticsService
    private let authService: AuthService
    private let userSync: UserSyncAPIClient

    init(
        appPreferences: AppPreferences,
        store: NotificationStore,
        analytics: AnalyticsService,
        authService: AuthService,
        userSync: UserSyncAPIClient
    ) {
        self.appPreferences = appPreferences
        self.store = store
        self.analytics = analytics
        self.authService = authService
        self.userSync = userSync
    }

    /// Pull a `?since=<cursor>` delta and merge it. Best-effort: failures are
    /// swallowed (logged), matching the focus-refresh sync model. For a
    /// signed-in user, also pulls the per-user read/dismiss overlay and merges
    /// it atomically with the feed (ADR-0015 state-before-surface).
    func refresh(reason: String) async {
        do {
            let client = APIClient(appPreferences: appPreferences, authToken: nil)
            let since = store.cursor
            let path = since > 0 ? "/api/notifications?since=\(since)" : "/api/notifications"
            let (data, response) = try await client.get(path: path)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                print("[Notifications] refresh[\(reason)] HTTP \(http.statusCode)")
                return
            }
            let result = try JSONDecoder().decode(NotificationFetchResult.self, from: data)

            let signedIn = authService.token != nil
            let serverState: [NotificationStateRow] = signedIn
                ? ((try? await userSync.pullNotificationState()) ?? [])
                : []

            for message in store.merge(result, serverState: serverState) {
                analytics.trackNotificationReceived(message, reason: reason)
            }

            // Offline catch-up: flush any local state the server is missing
            // (an eager push that failed while offline). No-op once converged.
            if signedIn {
                for row in store.unsyncedState(against: serverState) {
                    try? await userSync.pushNotificationState(
                        id: row.notificationId, seenAt: row.seenAt, dismissedAt: row.dismissedAt)
                }
            }

            print("[Notifications] refresh[\(reason)] ok: +\(result.messages.count) (cursor=\(store.cursor))")
        } catch {
            print("[Notifications] refresh[\(reason)] failed: \(error)")
        }
    }

    /// Eager push of a local read/dismiss mutation (ADR-0015). Fire-and-forget;
    /// failures are caught by the focus-refresh catch-up flush. No-op when
    /// signed out (state stays local, like the rest of user data).
    func pushStateChange(_ change: NotificationStateChange) {
        guard authService.token != nil else { return }
        let nowSec = Int64(Date().timeIntervalSince1970)
        Task { @MainActor in
            do {
                switch change {
                case .seen(let id):
                    try await userSync.pushNotificationState(id: id, seenAt: nowSec, dismissedAt: nil)
                case .dismissed(let id):
                    try await userSync.pushNotificationState(id: id, seenAt: nil, dismissedAt: nowSec)
                case .seenAll:
                    try await userSync.pushNotificationStateBulk(seenAt: nowSec, dismissedAt: nil, ids: nil)
                case .dismissedAll:
                    try await userSync.pushNotificationStateBulk(seenAt: nil, dismissedAt: nowSec, ids: nil)
                }
            } catch {
                print("[Notifications] push state failed: \(error)")
            }
        }
    }
}
