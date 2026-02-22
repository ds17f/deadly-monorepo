import Foundation

/// Converts `ShowImportData` (parsed from JSON) into database records.
struct ShowImporter {

    // MARK: - ShowRecord

    static func makeRecord(from data: ShowImportData, now: Int64 = currentMillis()) -> ShowRecord {
        let (year, month, yearMonth) = parseDateComponents(data.date)
        let songList: String? = data.setlist.flatMap { $0.extractSongList() }.map { $0.joined(separator: ",") }
        let memberList = extractMemberList(from: data.lineup)
        let coverImageUrl = resolveCoverImageUrl(from: data)
        let lineupRaw = encodeLineup(data.lineup)
        let recordingsRaw = encodeStringArray(data.recordings)
        let totalReviews = data.totalHighRatings + data.totalLowRatings

        return ShowRecord(
            showId: data.showId,
            date: data.date,
            year: year,
            month: month,
            yearMonth: yearMonth,
            band: data.band,
            url: data.url,
            venueName: data.venue,
            city: data.city,
            state: data.state,
            country: data.country ?? "USA",
            locationRaw: data.locationRaw,
            setlistStatus: data.setlistStatus,
            setlistRaw: data.setlist?.jsonString,
            songList: songList,
            lineupStatus: data.lineupStatus,
            lineupRaw: lineupRaw,
            memberList: memberList,
            showSequence: 1,
            recordingsRaw: recordingsRaw,
            recordingCount: data.recordingCount,
            bestRecordingId: data.bestRecording,
            averageRating: data.avgRating > 0 ? data.avgRating : nil,
            totalReviews: totalReviews,
            isInLibrary: false,
            libraryAddedAt: nil,
            coverImageUrl: coverImageUrl,
            createdAt: now,
            updatedAt: now
        )
    }

    // MARK: - ShowSearchRecord (FTS)

    static func makeSearchRecord(from data: ShowImportData) -> ShowSearchRecord {
        let searchText = buildSearchText(from: data)
        return ShowSearchRecord(rowid: nil, showId: data.showId, searchText: searchText)
    }

    // MARK: - Search text construction

    static func buildSearchText(from data: ShowImportData) -> String {
        var parts: [String] = []

        // Date variations
        let dateParts = data.date.split(separator: "-").map(String.init)
        guard dateParts.count == 3 else {
            return data.date
        }
        let yearStr = dateParts[0]
        let monthStr = dateParts[1]
        let dayStr = dateParts[2]
        let monthInt = Int(monthStr) ?? 0
        let dayInt = Int(dayStr) ?? 0
        let yearShort = String(yearStr.suffix(2))
        let yearPrefix = String(yearStr.prefix(3))

        parts.append(data.date)          // "1977-05-08"
        parts.append(yearStr)            // "1977"
        parts.append(yearShort)          // "77"
        parts.append(yearPrefix)         // "197"

        let delimiters = ["-", "/", "."]

        // Day/month/year formats
        for d in delimiters {
            parts.append("\(monthInt)\(d)\(dayInt)\(d)\(yearShort)")   // 5-8-77
            parts.append("\(monthStr)\(d)\(dayStr)\(d)\(yearStr)")     // 05-08-1977
            parts.append("\(yearStr)\(d)\(monthInt)\(d)\(dayInt)")     // 1977-5-8
        }

        // Month/year formats
        for d in delimiters {
            parts.append("\(monthInt)\(d)\(yearShort)")   // 5-77
            parts.append("\(yearStr)\(d)\(monthStr)")     // 1977-05
            parts.append("\(yearStr)\(d)\(monthInt)")     // 1977-5
            parts.append("\(yearShort)\(d)\(monthInt)")   // 77-5
        }

        // Location
        parts.append(data.venue)
        if let loc = data.locationRaw { parts.append(loc) }

        // Members (space-separated names)
        if let members = extractMemberList(from: data.lineup) {
            parts.append(members.replacingOccurrences(of: ",", with: " "))
        }

        // Songs (space-separated)
        if let songs = data.setlist.flatMap({ $0.extractSongList() }) {
            parts.append(songs.joined(separator: " "))
        }

        // Source type tags
        if data.sourceTypes.keys.contains("SBD") { parts.append("soundboard sbd") }
        if data.sourceTypes.keys.contains("AUD") { parts.append("audience aud") }
        if data.sourceTypes.keys.contains("MATRIX") { parts.append("matrix") }

        // Quality tags
        let totalReviews = data.totalHighRatings + data.totalLowRatings
        if data.avgRating >= 4.0 && totalReviews >= 10 { parts.append("top-rated") }
        if totalReviews >= 50 { parts.append("popular") }

        return parts.joined(separator: " ")
    }

    // MARK: - Private helpers

    private static func parseDateComponents(_ date: String) -> (year: Int, month: Int, yearMonth: String) {
        let parts = date.split(separator: "-").map(String.init)
        guard parts.count >= 2 else { return (0, 0, date) }
        let year = Int(parts[0]) ?? 0
        let month = Int(parts[1]) ?? 0
        let yearMonth = "\(parts[0])-\(parts[1])"
        return (year, month, yearMonth)
    }

    private static func extractMemberList(from lineup: [LineupMemberData]?) -> String? {
        guard let lineup = lineup else { return nil }
        let names = lineup.compactMap { $0.name.isEmpty ? nil : $0.name }
        return names.isEmpty ? nil : names.joined(separator: ",")
    }

    private static func resolveCoverImageUrl(from data: ShowImportData) -> String? {
        data.ticketImages.first(where: { $0.side == "front" })?.url
            ?? data.ticketImages.first(where: { $0.side == "unknown" })?.url
            ?? data.photos.first?.url
    }

    private static func encodeLineup(_ lineup: [LineupMemberData]?) -> String? {
        guard let lineup = lineup, !lineup.isEmpty else { return nil }
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(lineup),
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }

    static func encodeStringArray(_ arr: [String]) -> String? {
        guard !arr.isEmpty else { return nil }
        guard let data = try? JSONSerialization.data(withJSONObject: arr),
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }

    static func currentMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
