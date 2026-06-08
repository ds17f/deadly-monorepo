import SwiftUI

/// Top-bar bell with a persistent unread badge (decision A). Tapping pushes the
/// full notifications inbox screen — opening it no longer clears the badge;
/// only reading/archiving does. See PLANS/in-app-messaging.md.
struct NotificationBell: View {
    @Environment(\.appContainer) private var container

    private var store: NotificationStore { container.notificationStore }

    var body: some View {
        let unread = store.notifications.unreadCount(appVersion: store.appVersion)
        NavigationLink(value: NotificationRoute.inbox) {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell")
                    .font(.body)
                    .foregroundColor(.white)
                if unread > 0 {
                    Text(unread > 99 ? "99+" : "\(unread)")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(Color.red, in: Capsule())
                        .offset(x: 8, y: -8)
                }
            }
        }
        .accessibilityLabel(unread > 0 ? "Notifications, \(unread) unread" : "Notifications")
    }
}
