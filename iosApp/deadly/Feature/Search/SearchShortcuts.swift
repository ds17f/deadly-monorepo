import SwiftUI

struct SearchShortcut: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let searchQuery: String
    let priority: Int
    let category: ShortcutCategory
}

enum ShortcutCategory: String, CaseIterable {
    case filter, venue, city, song, member

    var displayName: String {
        switch self {
        case .filter: return "Filter"
        case .venue: return "Venue"
        case .city: return "City"
        case .song: return "Song"
        case .member: return "Member"
        }
    }

    var systemImage: String {
        switch self {
        case .filter: return "line.3.horizontal.decrease.circle"
        case .venue: return "building.2"
        case .city: return "mappin.and.ellipse"
        case .song: return "music.note"
        case .member: return "person"
        }
    }

    var tintColor: Color {
        switch self {
        case .filter: return DeadlyColors.primary
        case .venue: return DeadlyColors.secondary
        case .city: return DeadlyColors.tertiary
        case .song: return .purple
        case .member: return .orange
        }
    }
}

let allSearchShortcuts: [SearchShortcut] = [
    // Venues
    SearchShortcut(title: "Fillmore West", subtitle: "San Francisco", searchQuery: "Fillmore West", priority: 1, category: .venue),
    SearchShortcut(title: "Winterland", subtitle: "San Francisco", searchQuery: "Winterland", priority: 2, category: .venue),
    SearchShortcut(title: "Madison Square Garden", subtitle: "New York", searchQuery: "Madison Square Garden", priority: 3, category: .venue),
    SearchShortcut(title: "Red Rocks", subtitle: "Morrison, CO", searchQuery: "Red Rocks", priority: 4, category: .venue),
    SearchShortcut(title: "The Capitol Theatre", subtitle: "Port Chester, NY", searchQuery: "Capitol Theatre", priority: 5, category: .venue),
    SearchShortcut(title: "Barton Hall", subtitle: "Cornell University", searchQuery: "Barton Hall", priority: 6, category: .venue),
    SearchShortcut(title: "Greek Theatre", subtitle: "Berkeley, CA", searchQuery: "Greek Theatre", priority: 7, category: .venue),

    // Cities
    SearchShortcut(title: "San Francisco", subtitle: "Bay Area shows", searchQuery: "San Francisco", priority: 1, category: .city),
    SearchShortcut(title: "New York", subtitle: "East Coast hub", searchQuery: "New York", priority: 2, category: .city),
    SearchShortcut(title: "Chicago", subtitle: "Midwest shows", searchQuery: "Chicago", priority: 3, category: .city),
    SearchShortcut(title: "Philadelphia", subtitle: "Philly shows", searchQuery: "Philadelphia", priority: 4, category: .city),
    SearchShortcut(title: "Boston", subtitle: "New England shows", searchQuery: "Boston", priority: 5, category: .city),

    // Songs
    SearchShortcut(title: "Dark Star", subtitle: "Cosmic jams", searchQuery: "Dark Star", priority: 1, category: .song),
    SearchShortcut(title: "Scarlet > Fire", subtitle: "Classic pairing", searchQuery: "Scarlet Begonias", priority: 2, category: .song),
    SearchShortcut(title: "Eyes of the World", subtitle: "Fan favorite", searchQuery: "Eyes of the World", priority: 3, category: .song),
    SearchShortcut(title: "St. Stephen", subtitle: "Early classic", searchQuery: "St. Stephen", priority: 4, category: .song),
    SearchShortcut(title: "Estimated Prophet", subtitle: "Late 70s staple", searchQuery: "Estimated Prophet", priority: 5, category: .song),
    SearchShortcut(title: "China Cat > Rider", subtitle: "Iconic combo", searchQuery: "China Cat Sunflower", priority: 6, category: .song),

    // Members
    SearchShortcut(title: "Pigpen Shows", subtitle: "Ron McKernan era", searchQuery: "Pigpen", priority: 1, category: .member),
    SearchShortcut(title: "Brent Era", subtitle: "Brent Mydland years", searchQuery: "Brent", priority: 2, category: .member),
    SearchShortcut(title: "Vince Era", subtitle: "Vince Welnick years", searchQuery: "Vince", priority: 3, category: .member),
    SearchShortcut(title: "Keith & Donna", subtitle: "Godchaux era", searchQuery: "Keith", priority: 4, category: .member),

    // Filters
    SearchShortcut(title: "Acoustic Sets", subtitle: "Unplugged shows", searchQuery: "acoustic", priority: 1, category: .filter),
    SearchShortcut(title: "Europe Tours", subtitle: "Overseas shows", searchQuery: "Europe", priority: 2, category: .filter),
    SearchShortcut(title: "Festival Shows", subtitle: "Music festivals", searchQuery: "festival", priority: 3, category: .filter),
]

/// 3 discover cards, rotated every 4 hours via seeded shuffle.
var discoverShortcuts: [SearchShortcut] {
    rotatedSubset(of: allSearchShortcuts, count: 3)
}

/// 8 browse cards, rotated every 4 hours via seeded shuffle.
var browseShortcuts: [SearchShortcut] {
    rotatedSubset(of: allSearchShortcuts, count: 8)
}

private func rotatedSubset(of shortcuts: [SearchShortcut], count: Int) -> [SearchShortcut] {
    let seed = Int(Date().timeIntervalSince1970 / (4 * 3600))
    var rng = SeededRandomNumberGenerator(seed: UInt64(seed))
    var shuffled = shortcuts
    shuffled.shuffle(using: &rng)
    return Array(shuffled.prefix(count))
}

private struct SeededRandomNumberGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        // xorshift64
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
