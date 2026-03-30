import Foundation

/// Parses Siri search terms (dates, venues, songs) into show queries.
struct DeadlyMediaSearchResolver {
    enum SearchResult {
        case date(String)           // ISO date "1977-05-08"
        case venue(String)          // Venue name
        case city(String)           // City name
        case general(String)        // Fallback: raw query for FTS
    }

    /// Known venue aliases for common shorthand.
    private static let venueAliases: [String: String] = [
        "msg": "Madison Square Garden",
        "the garden": "Madison Square Garden",
        "the fillmore": "Fillmore",
        "fillmore east": "Fillmore East",
        "fillmore west": "Fillmore West",
        "red rocks": "Red Rocks Amphitheatre",
        "the cap": "Capitol Theatre",
        "cap theatre": "Capitol Theatre",
        "winterland": "Winterland",
        "barton hall": "Barton Hall",
    ]

    /// Two-digit year expansion for live music era (1960s–present).
    private static func expandYear(_ twoDigit: Int) -> Int? {
        switch twoDigit {
        case 60...99: return 1900 + twoDigit
        case 0...25:  return 2000 + twoDigit
        default:      return nil
        }
    }

    /// Attempt to parse a query string into a structured search result.
    static func parse(_ query: String) -> SearchResult {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        let lower = trimmed.lowercased()

        // 1. Check venue aliases first
        if let venue = venueAliases[lower] {
            return .venue(venue)
        }

        // 2. Try date patterns
        if let date = parseDate(trimmed) {
            return .date(date)
        }

        // 3. Check if it looks like a well-known venue name
        for (alias, fullName) in venueAliases where lower.contains(alias) {
            return .venue(fullName)
        }

        // 4. Fallback to general search
        return .general(trimmed)
    }

    /// Attempts to parse various date formats into ISO date string "YYYY-MM-DD".
    static func parseDate(_ input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespaces)

        // Pattern: M/D/YY or M/D/YYYY
        if let match = trySlashDate(trimmed) { return match }

        // Pattern: M-D-YY or M-D-YYYY
        if let match = tryDashDate(trimmed) { return match }

        // Pattern: "Month D YYYY" or "Month D, YYYY"
        if let match = tryWrittenDate(trimmed) { return match }

        // Pattern: just a 4-digit year
        if let year = Int(trimmed), year >= 1960, year <= 2025 {
            return nil // Return nil for bare years — caller should handle as year browse
        }

        return nil
    }

    private static func trySlashDate(_ input: String) -> String? {
        let parts = input.split(separator: "/")
        guard parts.count == 3,
              let month = Int(parts[0]),
              let day = Int(parts[1]) else { return nil }

        let yearPart = String(parts[2])
        let year: Int
        if yearPart.count <= 2, let twoDigit = Int(yearPart), let expanded = expandYear(twoDigit) {
            year = expanded
        } else if let fourDigit = Int(yearPart), fourDigit >= 1900 {
            year = fourDigit
        } else {
            return nil
        }

        guard month >= 1, month <= 12, day >= 1, day <= 31 else { return nil }
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    private static func tryDashDate(_ input: String) -> String? {
        let parts = input.split(separator: "-")
        // Distinguish from ISO dates (YYYY-MM-DD) — if first part is 4 digits, it's already ISO-like
        guard parts.count == 3, let first = parts.first, first.count <= 2 else { return nil }
        guard let month = Int(parts[0]), let day = Int(parts[1]) else { return nil }

        let yearPart = String(parts[2])
        let year: Int
        if yearPart.count <= 2, let twoDigit = Int(yearPart), let expanded = expandYear(twoDigit) {
            year = expanded
        } else if let fourDigit = Int(yearPart), fourDigit >= 1900 {
            year = fourDigit
        } else {
            return nil
        }

        guard month >= 1, month <= 12, day >= 1, day <= 31 else { return nil }
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    private static let monthNames: [String: Int] = [
        "january": 1, "february": 2, "march": 3, "april": 4,
        "may": 5, "june": 6, "july": 7, "august": 8,
        "september": 9, "october": 10, "november": 11, "december": 12,
        "jan": 1, "feb": 2, "mar": 3, "apr": 4,
        "jun": 6, "jul": 7, "aug": 8,
        "sep": 9, "oct": 10, "nov": 11, "dec": 12
    ]

    private static func tryWrittenDate(_ input: String) -> String? {
        // "May 8 1977" or "May 8, 1977"
        let cleaned = input.replacingOccurrences(of: ",", with: "")
        let parts = cleaned.split(separator: " ").map(String.init)
        guard parts.count == 3 else { return nil }

        guard let month = monthNames[parts[0].lowercased()],
              let day = Int(parts[1]),
              let year = Int(parts[2]),
              year >= 1900 else { return nil }

        guard month >= 1, month <= 12, day >= 1, day <= 31 else { return nil }
        return String(format: "%04d-%02d-%02d", year, month, day)
    }
}
