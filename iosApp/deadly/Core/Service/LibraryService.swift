import Foundation
import GRDB

// MARK: - LibraryServiceImpl

@Observable
@MainActor
final class LibraryServiceImpl {
    private let database: AppDatabase
    private let libraryDAO: LibraryDAO
    private let showRepository: any ShowRepository

    private(set) var shows: [Show] = []
    private(set) var isLoading = false

    nonisolated init(
        database: AppDatabase,
        libraryDAO: LibraryDAO,
        showRepository: any ShowRepository
    ) {
        self.database = database
        self.libraryDAO = libraryDAO
        self.showRepository = showRepository
    }

    // MARK: - Mutations

    func addToLibrary(showId: String) throws {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        try database.write { db in
            var record = LibraryShowRecord(
                showId: showId,
                addedToLibraryAt: now,
                isPinned: false,
                libraryNotes: nil,
                preferredRecordingId: nil,
                downloadedRecordingId: nil,
                downloadedFormat: nil,
                customRating: nil,
                lastAccessedAt: nil,
                tags: nil
            )
            try record.insert(db)
            try ShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("isInLibrary").set(to: true),
                    Column("libraryAddedAt").set(to: now)
                )
        }
    }

    func removeFromLibrary(showId: String) throws {
        try database.write { db in
            try LibraryShowRecord.deleteOne(db, key: showId)
            try ShowRecord
                .filter(Column("showId") == showId)
                .updateAll(db,
                    Column("isInLibrary").set(to: false),
                    Column("libraryAddedAt").set(to: nil as Int64?)
                )
        }
    }

    func isInLibrary(showId: String) throws -> Bool {
        try libraryDAO.isInLibrary(showId)
    }

    // MARK: - Fetch

    func refresh(sortedBy option: LibrarySortOption = .dateAdded, direction: LibrarySortDirection = .descending) {
        isLoading = true
        defer { isLoading = false }
        do {
            let records = try libraryDAO.fetchAll()
            let ids = records.map(\.showId)
            let fetched = try showRepository.getShowsByIds(ids)
            shows = sort(fetched, by: option, direction: direction)
        } catch {
            // Leave existing shows on error
        }
    }

    // MARK: - Private

    private func sort(_ shows: [Show], by option: LibrarySortOption, direction: LibrarySortDirection) -> [Show] {
        let ascending = direction == .ascending
        return shows.sorted { a, b in
            let result: Bool
            switch option {
            case .dateAdded:
                let aVal = a.libraryAddedAt ?? 0
                let bVal = b.libraryAddedAt ?? 0
                result = aVal < bVal
            case .dateOfShow:
                result = a.date < b.date
            case .venue:
                result = a.venue.name.localizedCompare(b.venue.name) == .orderedAscending
            case .rating:
                let aVal = a.averageRating ?? 0
                let bVal = b.averageRating ?? 0
                result = aVal < bVal
            }
            return ascending ? result : !result
        }
    }
}
