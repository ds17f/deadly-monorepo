import SwiftUI

/// Top-bar bell with an unread badge. Tapping opens the in-app messaging inbox
/// and clears the badge. Faithful port of the web `NotificationBell`.
/// See PLANS/in-app-messaging.md.
struct NotificationBell: View {
    @Environment(\.appContainer) private var container
    @State private var showingInbox = false

    private var store: NotificationStore { container.notificationStore }

    var body: some View {
        let unread = store.notifications.unreadCount()
        Button {
            showingInbox = true
            store.markAllSeen()
        } label: {
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
        .sheet(isPresented: $showingInbox) {
            NotificationInboxView()
                .environment(\.appContainer, container)
        }
    }
}

private struct NotificationInboxView: View {
    @Environment(\.appContainer) private var container
    @Environment(\.dismiss) private var dismiss
    @State private var showArchive = false

    private var store: NotificationStore { container.notificationStore }

    var body: some View {
        NavigationStack {
            let active = store.notifications.active()
            let archived = store.notifications.dismissedArchive()
            List {
                if active.isEmpty {
                    Text("You're all caught up.")
                        .foregroundColor(.secondary)
                } else {
                    Section {
                        ForEach(active) { message in
                            NotificationRow(message: message)
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        store.dismiss(message.id)
                                    } label: {
                                        Label("Dismiss", systemImage: "xmark")
                                    }
                                }
                        }
                    }
                }

                if !archived.isEmpty {
                    Section {
                        DisclosureGroup("Dismissed (\(archived.count))", isExpanded: $showArchive) {
                            ForEach(archived) { message in
                                NotificationRow(message: message, muted: true)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct NotificationRow: View {
    let message: CachedNotification
    var muted: Bool = false

    private var accent: Color {
        message.level == "warn" ? .orange : DeadlyColors.primary
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(message.title)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(accent)
            // Body renders newlines as-is (Text honors \n by default).
            Text(message.body)
                .font(.body)
                .foregroundColor(.primary)
        }
        .opacity(muted ? 0.6 : 1)
        .padding(.vertical, 2)
    }
}
