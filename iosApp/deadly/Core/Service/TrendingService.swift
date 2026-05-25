import Foundation

enum TrendingWindow: String, CaseIterable, Identifiable {
    case now, week, month, all
    var id: String { rawValue }

    var label: String {
        switch self {
        case .now: return "now"
        case .week: return "this week"
        case .month: return "this month"
        case .all: return "all time"
        }
    }

    /// Next window in cycle order. `.all` wraps back to `.now`.
    var next: TrendingWindow {
        switch self {
        case .now: return .week
        case .week: return .month
        case .month: return .all
        case .all: return .now
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
