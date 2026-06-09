import Foundation

/// The notifications analytics vocabulary in one place — extension methods on
/// AnalyticsService so call sites stay terse and every `notification_*` event
/// name/prop is defined once. Mirrors the server allowlist in
/// api/src/db/analytics.ts and the Android/web equivalents.
///
/// Every message-scoped event carries `id` so the admin dashboard can aggregate
/// engagement per notification.
extension AnalyticsService {
    func trackNotificationReceived(_ m: CachedNotification, reason: String) {
        track("notification_received", props: [
            "id": m.id, "category": m.category, "level": m.level, "reason": reason,
        ])
    }

    func trackNotificationImpression(_ m: CachedNotification) {
        track("notification_impression", props: [
            "id": m.id, "category": m.category, "level": m.level,
        ])
    }

    func trackNotificationOpen(_ m: CachedNotification) {
        track("notification_open", props: [
            "id": m.id, "category": m.category, "level": m.level,
            "was_unread": m.seenAt == nil,
        ])
    }

    func trackNotificationLinkTap(id: Int64, url: String) {
        track("notification_link_tap", props: ["id": id, "url": url])
    }

    func trackNotificationArchive(_ m: CachedNotification) {
        track("notification_archive", props: ["id": m.id, "category": m.category])
    }

    func trackNotificationToastShown(_ arrival: NewArrival) {
        track("notification_toast_shown", props: ["id": arrival.key, "count": arrival.count])
    }

    func trackNotificationToastTap(_ arrival: NewArrival) {
        track("notification_toast_tap", props: ["id": arrival.key])
    }

    func trackNotificationMarkAllRead(count: Int) {
        track("notification_mark_all_read", props: ["count": count])
    }

    func trackNotificationArchiveAll(count: Int) {
        track("notification_archive_all", props: ["count": count])
    }

    func trackNotificationCommunityTap() {
        track("notification_community_tap")
    }
}
