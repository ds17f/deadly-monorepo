import Foundation

/// What auto-advance does when the current show ends (ADR-0010 Amendment).
///
/// Three explicit modes (the "Autoplay" ∞ control cycles through them):
/// - `none`: stop at the end of the show.
/// - `showQueue`: play the head of the Show Queue, then stop when it drains
///   (curation ran out — don't spill into uncurated territory).
/// - `chronological`: play the next show by date, ignoring the queue.
enum AdvanceMode: String, CaseIterable, Sendable {
    case none
    case showQueue
    case chronological

    var displayName: String {
        switch self {
        case .none: return "Off"
        case .showQueue: return "Show Queue"
        case .chronological: return "Chronological"
        }
    }

    /// The ∞ control cycles None → Show Queue → Chronological → None.
    func next() -> AdvanceMode {
        let all = Self.allCases
        let i = all.firstIndex(of: self)!
        return all[(i + 1) % all.count]
    }
}
