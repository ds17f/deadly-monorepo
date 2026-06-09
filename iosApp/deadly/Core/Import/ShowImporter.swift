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

        // Resolve best available source type by quality ranking
        let sourceTypeRank = ["SBD", "FM", "MATRIX", "REMASTER", "AUD"]
        let bestSourceType = sourceTypeRank.first { data.sourceTypes.keys.contains($0) }

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
            bestSourceType: bestSourceType,
            averageRating: data.avgRating > 0 ? data.avgRating : nil,
            totalReviews: totalReviews,
            isFavorite: false,
            favoritedAt: nil,
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

    /// Delegates to the shared `ShowSearchText` builder so the JSON import and the
    /// prebuilt-seed import (`SeedImportService`) index identical text. The seed
    /// path has only the stored `songList`/`memberList` CSVs, so both paths feed
    /// the builder CSV inputs.
    static func buildSearchText(from data: ShowImportData) -> String {
        let songListCsv = data.setlist
            .flatMap { $0.extractSongList() }
            .map { $0.joined(separator: ",") }
        return ShowSearchText.build(
            date: data.date,
            venue: data.venue,
            locationRaw: data.locationRaw,
            memberListCsv: extractMemberList(from: data.lineup),
            songListCsv: songListCsv,
            sourceTypeKeys: Set(data.sourceTypes.keys),
            avgRating: data.avgRating,
            totalReviews: data.totalHighRatings + data.totalLowRatings
        )
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
