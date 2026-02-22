import Foundation

/// Parses `collections.json` and resolves `show_selector` patterns into `DeadCollectionRecord` values.
struct CollectionsImporter {
    let showDAO: ShowDAO

    // MARK: - Entry point

    /// Parse `collections.json` from the given extraction directory and resolve show IDs.
    /// Returns the resolved records ready for insertion. Never throws — failures per collection are skipped.
    func importCollections(from extractDir: URL, now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> [DeadCollectionRecord] {
        guard let collectionsFile = findCollectionsFile(in: extractDir) else {
            return []
        }
        guard let data = try? Data(contentsOf: collectionsFile),
              let wrapper = try? JSONDecoder().decode(CollectionsWrapper.self, from: data) else {
            return []
        }

        return wrapper.collections.compactMap { collection in
            let showIds = resolveShowIds(for: collection.showSelector)
            return makeRecord(from: collection, showIds: showIds, now: now)
        }
    }

    // MARK: - Show selector resolution

    func resolveShowIds(for selector: ShowSelectorData?) -> [String] {
        guard let selector = selector else { return [] }
        var resolved = Set<String>()

        // Explicit show IDs
        resolved.formUnion(selector.showIds)

        // Specific dates
        for date in selector.dates {
            if let shows = try? showDAO.fetchByDate(date) {
                resolved.formUnion(shows.map(\.showId))
            }
        }

        // Date ranges (plural format)
        for range in selector.ranges {
            if let shows = try? showDAO.fetchInDateRange(start: range.start, end: range.end) {
                resolved.formUnion(shows.map(\.showId))
            }
        }

        // Single range with optional exclusions
        if let range = selector.range {
            if let shows = try? showDAO.fetchInDateRange(start: range.start, end: range.end) {
                var rangeIds = Set(shows.map(\.showId))

                for exclusion in selector.exclusionRanges {
                    if let ex = try? showDAO.fetchInDateRange(start: exclusion.from, end: exclusion.to) {
                        rangeIds.subtract(ex.map(\.showId))
                    }
                }
                for date in selector.exclusionDates {
                    if let ex = try? showDAO.fetchByDate(date) {
                        rangeIds.subtract(ex.map(\.showId))
                    }
                }
                resolved.formUnion(rangeIds)
            }
        }

        // Venue LIKE matches
        for venue in selector.venues {
            if let shows = try? showDAO.fetchByVenue(venue) {
                resolved.formUnion(shows.map(\.showId))
            }
        }

        // Year matches
        for year in selector.years {
            if let shows = try? showDAO.fetchByYear(year) {
                resolved.formUnion(shows.map(\.showId))
            }
        }

        return resolved.sorted()
    }

    // MARK: - Record construction

    private func makeRecord(
        from data: CollectionImportData,
        showIds: [String],
        now: Int64
    ) -> DeadCollectionRecord? {
        guard let tagsJson = encodeStringArray(data.tags),
              let showIdsJson = encodeStringArray(showIds) else {
            return nil
        }
        return DeadCollectionRecord(
            id: data.id,
            name: data.name,
            description: data.description,
            tagsJson: tagsJson,
            showIdsJson: showIdsJson,
            totalShows: showIds.count,
            primaryTag: data.tags.first,
            createdAt: now,
            updatedAt: now
        )
    }

    // MARK: - Helpers

    private func findCollectionsFile(in extractDir: URL) -> URL? {
        let fm = FileManager.default
        let candidates = [
            extractDir.appendingPathComponent("collections.json"),
            extractDir.appendingPathComponent("data/collections.json"),
        ]
        if let found = candidates.first(where: { fm.fileExists(atPath: $0.path) }) {
            return found
        }
        // Try one level deeper (ZIP may contain a root folder)
        guard let contents = try? fm.contentsOfDirectory(at: extractDir, includingPropertiesForKeys: [.isDirectoryKey]) else {
            return nil
        }
        for item in contents {
            let candidate = item.appendingPathComponent("collections.json")
            if fm.fileExists(atPath: candidate.path) { return candidate }
        }
        return nil
    }

    private func encodeStringArray(_ arr: [String]) -> String? {
        guard let data = try? JSONSerialization.data(withJSONObject: arr),
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }
}
