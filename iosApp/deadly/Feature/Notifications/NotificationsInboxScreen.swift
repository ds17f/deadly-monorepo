import SwiftUI

/// Navigation route for the notifications inbox (pushed from the header bell).
enum NotificationRoute: Hashable {
    case inbox
}

/// Full-screen in-app messaging inbox (decision B). A list of messages → tap a
/// row to read its detail (marks it read). Inbox/Archived toggle, bulk
/// mark-all-read / archive-all, and a community footer.
/// See PLANS/in-app-messaging.md.
struct NotificationsInboxScreen: View {
    @Environment(\.appContainer) private var container
    @State private var showArchive = false
    @State private var selected: CachedNotification?

    private var store: NotificationStore { container.notificationStore }

    var body: some View {
        let version = store.appVersion
        let active = store.notifications.active(appVersion: version)
        let archived = store.notifications.dismissedArchive(appVersion: version)
        let list = showArchive ? archived : active

        List {
            if !showArchive && !active.isEmpty {
                Section {
                    EmptyView()
                } header: {
                    HStack {
                        Button("Mark all read") { store.markAllSeen() }
                            .disabled(!active.contains { $0.seenAt == nil })
                        Spacer()
                        Button("Archive all", role: .destructive) { store.archiveAll() }
                    }
                    .font(.footnote)
                    .textCase(nil)
                }
            }

            if list.isEmpty {
                Text(showArchive ? "Nothing archived." : "You're all caught up.")
                    .foregroundColor(.secondary)
            } else {
                ForEach(list) { message in
                    Button {
                        if !showArchive && message.seenAt == nil { store.markRead(message.id) }
                        selected = message
                    } label: {
                        NotificationRowView(message: message, unread: !showArchive && message.seenAt == nil)
                    }
                    .buttonStyle(.plain)
                    .swipeActions(edge: .trailing) {
                        if !showArchive {
                            Button(role: .destructive) {
                                store.dismiss(message.id)
                            } label: {
                                Label("Archive", systemImage: "archivebox")
                            }
                        }
                    }
                }
            }

            Section {
                Link(destination: Community.subredditURL) {
                    Text("More at \(Community.subredditHandle) →")
                        .font(.footnote)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .listRowBackground(Color.clear)
        }
        .refreshable {
            await container.notificationService.refresh(reason: "pull")
        }
        .navigationTitle(showArchive ? "Archived" : "Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if showArchive {
                    Button("Inbox") { showArchive = false }
                } else if !archived.isEmpty {
                    Button("Archived (\(archived.count))") { showArchive = true }
                }
            }
        }
        .navigationDestination(item: $selected) { message in
            NotificationDetailView(message: message, archived: showArchive)
        }
    }
}

private struct NotificationRowView: View {
    let message: CachedNotification
    let unread: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: categorySymbol(message.category))
                .font(.footnote)
                .foregroundColor(.secondary)
                .padding(.top, 2)
            if unread {
                Circle()
                    .fill(DeadlyColors.primary)
                    .frame(width: 8, height: 8)
                    .padding(.top, 6)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(message.title)
                        .font(.subheadline)
                        .fontWeight(unread ? .semibold : .regular)
                        .lineLimit(1)
                    Spacer()
                    Text(timeAgo(message.createdAt))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                Text(previewLine(message.body))
                    .font(.body)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 2)
    }
}

private struct NotificationDetailView: View {
    @Environment(\.appContainer) private var container
    @Environment(\.dismiss) private var dismiss
    let message: CachedNotification
    let archived: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Image(systemName: categorySymbol(message.category))
                        .foregroundColor(.secondary)
                    Text(message.title)
                        .font(.title2).fontWeight(.bold)
                }
                Text(timeAgo(message.createdAt))
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(notificationBody(message.body, linkColor: DeadlyColors.primary))
                    .font(.body)
                if !archived {
                    Button {
                        container.notificationStore.dismiss(message.id)
                        dismiss()
                    } label: {
                        Label("Archive", systemImage: "archivebox")
                    }
                    .buttonStyle(.bordered)
                    .padding(.top, 8)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .navigationTitle("Message")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Monochrome SF Symbols (tinted `.secondary`) instead of colorful emoji.
private func categorySymbol(_ category: String) -> String {
    switch category {
    case "release": return "shippingbox"
    case "feature": return "sparkles"
    case "outage": return "exclamationmark.triangle"
    default: return "megaphone"
    }
}

private func previewLine(_ body: String) -> String {
    let firstLine = body.split(separator: "\n", omittingEmptySubsequences: true).first.map(String.init)?
        .trimmingCharacters(in: .whitespaces) ?? ""
    return firstLine.count > 120 ? String(firstLine.prefix(120)) + "…" : firstLine
}

private func timeAgo(_ createdAtSecs: Int64) -> String {
    let secs = Int64(Date().timeIntervalSince1970) - createdAtSecs
    switch secs {
    case ..<60: return "just now"
    case ..<3600: return "\(secs / 60)m ago"
    case ..<86_400: return "\(secs / 3600)h ago"
    case ..<604_800: return "\(secs / 86_400)d ago"
    default:
        let f = DateFormatter()
        f.dateFormat = "MMM d"
        return f.string(from: Date(timeIntervalSince1970: TimeInterval(createdAtSecs)))
    }
}
