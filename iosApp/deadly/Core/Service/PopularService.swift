import Foundation

enum PopularDecade: String, CaseIterable, Identifiable {
    case all
    case s60 = "60s"
    case s70 = "70s"
    case s80 = "80s"
    case s90 = "90s"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return "all"
        case .s60: return "60s"
        case .s70: return "70s"
        case .s80: return "80s"
        case .s90: return "90s"
        }
    }

    /// Tolerant of unknown strings — defaults to .all.
    init(preferenceKey: String) {
        self = PopularDecade(rawValue: preferenceKey) ?? .all
    }
}

/// Per-decade pools returned by /api/popular. Each pool is up to
/// POPULAR_PER_DECADE shows, drawn with a 4h-rotated stable shuffle.
/// The display set on the home rail is computed locally via
/// `displayShows(for:seed:)` so "Show more" can re-roll without a fetch.
struct PopularContent: Sendable {
    var pool60: [Show] = []
    var pool70: [Show] = []
    var pool80: [Show] = []
    var pool90: [Show] = []

    var hasAnyContent: Bool {
        !pool60.isEmpty || !pool70.isEmpty || !pool80.isEmpty || !pool90.isEmpty
    }

    /// Number of shows to surface on the home rail in either mode.
    static let displayCount = 4

    /// Compute the home-rail display set.
    /// - `.all`: one show from each non-empty decade pool (≤ 4).
    /// - specific decade: up to 4 shows from that pool, biased toward
    ///   distinct years within the decade so the rail spans the era.
    /// `seed` controls the random selection; bumping it on "Show more"
    /// re-rolls without re-fetching. Returned shows are date-sorted.
    func displayShows(for decade: PopularDecade, seed: Int) -> [Show] {
        let picks: [Show]
        switch decade {
        case .all:
            picks = pickOnePerDecade(seed: seed)
        case .s60:
            picks = pickWithYearSpread(pool: pool60, count: Self.displayCount, seed: seed)
        case .s70:
            picks = pickWithYearSpread(pool: pool70, count: Self.displayCount, seed: seed)
        case .s80:
            picks = pickWithYearSpread(pool: pool80, count: Self.displayCount, seed: seed)
        case .s90:
            picks = pickWithYearSpread(pool: pool90, count: Self.displayCount, seed: seed)
        }
        return picks.sorted { $0.date < $1.date }
    }

    private func pickOnePerDecade(seed: Int) -> [Show] {
        let pools: [(name: String, pool: [Show])] = [
            ("60s", pool60), ("70s", pool70), ("80s", pool80), ("90s", pool90),
        ]
        var out: [Show] = []
        for (name, pool) in pools where !pool.isEmpty {
            var rng = SeededGenerator(seed: combinedSeed(seed, name))
            if let pick = pool.shuffled(using: &rng).first {
                out.append(pick)
            }
        }
        return out
    }

    private func pickWithYearSpread(pool: [Show], count: Int, seed: Int) -> [Show] {
        guard !pool.isEmpty else { return [] }
        var rng = SeededGenerator(seed: combinedSeed(seed, "decade"))
        let shuffled = pool.shuffled(using: &rng)
        var seenYears = Set<Int>()
        var primary: [Show] = []
        var fallback: [Show] = []
        for show in shuffled {
            if seenYears.contains(show.year) {
                fallback.append(show)
            } else {
                primary.append(show)
                seenYears.insert(show.year)
            }
        }
        return Array((primary + fallback).prefix(count))
    }
}

@MainActor
protocol PopularService {
    var content: PopularContent { get }
    func refresh() async
}

// MARK: - Seeded RNG

/// SplitMix64 — small, fast, deterministic. Conforms to RandomNumberGenerator
/// so `Array.shuffled(using:)` works directly.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { self.state = seed == 0 ? 0xdeadbeefcafebabe : seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

private func combinedSeed(_ a: Int, _ b: String) -> UInt64 {
    // FNV-1a-ish mix so "60s" and "70s" produce distinct streams from the
    // same caller seed. Mixing a separator the user can't inject keeps
    // adjacent decades non-trivially different.
    var h: UInt64 = 0xcbf29ce484222325 &+ UInt64(bitPattern: Int64(a))
    h = h &* 0x100000001b3
    for byte in b.utf8 {
        h ^= UInt64(byte)
        h = h &* 0x100000001b3
    }
    return h
}
