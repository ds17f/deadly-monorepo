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

    init(appPreferences: AppPreferences, store: NotificationStore, analytics: AnalyticsService) {
        self.appPreferences = appPreferences
        self.store = store
        self.analytics = analytics
    }

    /// Pull a `?since=<cursor>` delta and merge it. Best-effort: failures are
    /// swallowed (logged), matching the focus-refresh sync model.
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
            for message in store.merge(result) {
                analytics.trackNotificationReceived(message, reason: reason)
            }
            print("[Notifications] refresh[\(reason)] ok: +\(result.messages.count) (cursor=\(store.cursor))")
        } catch {
            print("[Notifications] refresh[\(reason)] failed: \(error)")
        }
    }
}
