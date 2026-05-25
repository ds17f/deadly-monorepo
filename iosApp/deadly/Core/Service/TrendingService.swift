import Foundation

enum TrendingWindow: String, CaseIterable, Identifiable {
    case now, week, month, all
    var id: String { rawValue }

    var label: String {
        switch self {
        case .now: return "24h"
        case .week: return "Week"
        case .month: return "Month"
        case .all: return "All-Time"
        }
    }

    var subtitle: String {
        switch self {
        case .now: return "Last 24 hours"
        case .week: return "Last 7 days"
        case .month: return "Last 30 days"
        case .all: return "All time"
        }
    }

    /// Tolerant of unknown strings — defaults to .now.
    init(preferenceKey: String) {
        self = TrendingWindow(rawValue: preferenceKey) ?? .now
    }
}

struct TrendingContent: Sendable {
    var now: [Show] = []
    var week: [Show] = []
    var month: [Show] = []
    var all: [Show] = []

    func shows(for window: TrendingWindow) -> [Show] {
        switch window {
        case .now: return now
        case .week: return week
        case .month: return month
        case .all: return all
        }
    }
}

@MainActor
protocol TrendingService {
    var content: TrendingContent { get }
    func refresh() async
}
