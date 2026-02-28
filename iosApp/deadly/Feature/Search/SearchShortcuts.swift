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
    // Filters (priority 10) - these use FTS tags
    SearchShortcut(title: "Top Rated", subtitle: "Highest rated recordings", searchQuery: "top-rated", priority: 10, category: .filter),
    SearchShortcut(title: "Popular", subtitle: "Most reviewed shows", searchQuery: "popular", priority: 10, category: .filter),
    SearchShortcut(title: "Soundboard", subtitle: "Direct from the board", searchQuery: "sbd", priority: 10, category: .filter),
    SearchShortcut(title: "Audience", subtitle: "Taped from the crowd", searchQuery: "aud", priority: 10, category: .filter),

    // Venues (priority 5)
    SearchShortcut(title: "Fillmore", subtitle: "Fillmore East & West", searchQuery: "Fillmore", priority: 5, category: .venue),
    SearchShortcut(title: "Winterland", subtitle: "San Francisco", searchQuery: "Winterland", priority: 5, category: .venue),
    SearchShortcut(title: "Red Rocks", subtitle: "Morrison, CO", searchQuery: "Red Rocks", priority: 5, category: .venue),
    SearchShortcut(title: "MSG", subtitle: "Madison Square Garden", searchQuery: "Madison Square Garden", priority: 5, category: .venue),
    SearchShortcut(title: "Capitol Theater", subtitle: "Port Chester, NY", searchQuery: "Capitol Theater", priority: 5, category: .venue),
    SearchShortcut(title: "Barton Hall", subtitle: "Cornell University", searchQuery: "Barton Hall", priority: 5, category: .venue),

    // Cities (priority 5)
    SearchShortcut(title: "New York", subtitle: "The Big Apple", searchQuery: "New York", priority: 5, category: .city),
    SearchShortcut(title: "San Francisco", subtitle: "Home turf", searchQuery: "San Francisco", priority: 5, category: .city),
    SearchShortcut(title: "Chicago", subtitle: "The Windy City", searchQuery: "Chicago", priority: 5, category: .city),
    SearchShortcut(title: "Philadelphia", subtitle: "City of Brotherly Love", searchQuery: "Philadelphia", priority: 5, category: .city),
    SearchShortcut(title: "Boston", subtitle: "New England shows", searchQuery: "Boston", priority: 5, category: .city),
    SearchShortcut(title: "Los Angeles", subtitle: "SoCal shows", searchQuery: "Los Angeles", priority: 5, category: .city),

    // Songs (priority 5)
    SearchShortcut(title: "Dark Star", subtitle: "The quintessential jam", searchQuery: "Dark Star", priority: 5, category: .song),
    SearchShortcut(title: "Scarlet > Fire", subtitle: "The classic combo", searchQuery: "Scarlet Begonias", priority: 5, category: .song),
    SearchShortcut(title: "Wharf Rat", subtitle: "Ballad of August West", searchQuery: "Wharf Rat", priority: 5, category: .song),
    SearchShortcut(title: "Truckin'", subtitle: "What a long strange trip", searchQuery: "Truckin", priority: 5, category: .song),
    SearchShortcut(title: "Eyes of the World", subtitle: "Jazz-infused Garcia", searchQuery: "Eyes of the World", priority: 5, category: .song),
    SearchShortcut(title: "Sugar Magnolia", subtitle: "Sunshine daydream", searchQuery: "Sugar Magnolia", priority: 5, category: .song),

    // Members (priority 3)
    SearchShortcut(title: "Brent Era", subtitle: "Brent Mydland on keys", searchQuery: "Brent", priority: 3, category: .member),
    SearchShortcut(title: "Pigpen Era", subtitle: "Blues and soul years", searchQuery: "Pigpen", priority: 3, category: .member),
    SearchShortcut(title: "Keith Era", subtitle: "Keith Godchaux on piano", searchQuery: "Keith", priority: 3, category: .member),
]

/// 3 discover cards, rotated every 4 hours via seeded shuffle.
/// Pass refreshCounter to force re-roll on pull-to-refresh.
func discoverShortcuts(refreshCounter: Int = 0) -> [SearchShortcut] {
    rotatedSubset(of: allSearchShortcuts, count: 3, seedOffset: refreshCounter * 1000)
}

/// 8 browse cards with priority >= 5, rotated every 4 hours via seeded shuffle.
/// Pass refreshCounter to force re-roll on pull-to-refresh.
/// Excludes items already shown in discoverShortcuts to avoid duplicates.
func browseShortcuts(refreshCounter: Int = 0) -> [SearchShortcut] {
    let discoverItems = discoverShortcuts(refreshCounter: refreshCounter)
    let discoverTitles = Set(discoverItems.map(\.title))
    let eligible = allSearchShortcuts.filter {
        $0.priority >= 5 && !discoverTitles.contains($0.title)
    }
    return rotatedSubset(of: eligible, count: 8, seedOffset: 500 + refreshCounter * 1000)
}

private func rotatedSubset(of shortcuts: [SearchShortcut], count: Int, seedOffset: Int = 0) -> [SearchShortcut] {
    let seed = Int(Date().timeIntervalSince1970 / (4 * 3600)) + seedOffset
    var rng = SeededRandomNumberGenerator(seed: UInt64(seed))
    var shuffled = shortcuts
    shuffled.shuffle(using: &rng)
    return Array(shuffled.prefix(count))
}

private struct SeededRandomNumberGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        // Mix the seed using splitmix64 to ensure good bit distribution
        var z = seed &+ 0x9e3779b97f4a7c15
        z = (z ^ (z >> 30)) &* 0xbf58476d1ce4e5b9
        z = (z ^ (z >> 27)) &* 0x94d049bb133111eb
        state = z ^ (z >> 31)
        // Ensure state is never 0 (xorshift produces only zeros from zero state)
        if state == 0 { state = 1 }
    }

    mutating func next() -> UInt64 {
        // xorshift64
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
