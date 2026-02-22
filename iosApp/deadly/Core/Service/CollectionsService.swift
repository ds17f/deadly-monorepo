import Foundation

// MARK: - CollectionListItem

struct CollectionListItem: Identifiable, Sendable {
    let id: String
    let name: String
    let description: String
    let tags: [String]
    let totalShows: Int
    let primaryTag: String?

    var showCountText: String {
        switch totalShows {
        case 0: return "No shows"
        case 1: return "1 show"
        default: return "\(totalShows) shows"
        }
    }
}

// MARK: - CollectionsServiceImpl

@Observable
@MainActor
final class CollectionsServiceImpl {
    private let collectionsDAO: CollectionsDAO
    private let showRepository: any ShowRepository

    // List state (no show resolution)
    private(set) var collections: [CollectionListItem] = []
    private(set) var allTags: [String] = []
    private(set) var isLoading = false

    // Detail state (with show resolution)
    private(set) var selectedCollection: DeadCollection?
    private(set) var isLoadingDetail = false

    nonisolated init(
        collectionsDAO: CollectionsDAO,
        showRepository: any ShowRepository
    ) {
        self.collectionsDAO = collectionsDAO
        self.showRepository = showRepository
    }

    // MARK: - List operations

    func loadAll() {
        isLoading = true
        defer { isLoading = false }
        do {
            let records = try collectionsDAO.fetchAll()
            collections = records.map(mapRecord)
            allTags = extractTags(from: records)
        } catch {
            // Leave existing data on error
        }
    }

    func filterByTag(_ tag: String) {
        isLoading = true
        defer { isLoading = false }
        do {
            let records = try collectionsDAO.fetchByTag(tag)
            collections = records.map(mapRecord)
        } catch {
            // Leave existing data on error
        }
    }

    func search(_ query: String) {
        isLoading = true
        defer { isLoading = false }
        do {
            let records = try collectionsDAO.search(query)
            collections = records.map(mapRecord)
        } catch {
            // Leave existing data on error
        }
    }

    // MARK: - Detail operations

    func loadCollection(id: String) {
        isLoadingDetail = true
        defer { isLoadingDetail = false }
        do {
            guard let record = try collectionsDAO.fetchById(id) else {
                selectedCollection = nil
                return
            }
            let tags = parseTags(record.tagsJson)
            let showIds = parseShowIds(record.showIdsJson)
            let shows = try showRepository.getShowsByIds(showIds)
            selectedCollection = DeadCollection(
                id: record.id,
                name: record.name,
                description: record.description,
                tags: tags,
                shows: shows
            )
        } catch {
            selectedCollection = nil
        }
    }

    // MARK: - Private

    private func mapRecord(_ record: DeadCollectionRecord) -> CollectionListItem {
        CollectionListItem(
            id: record.id,
            name: record.name,
            description: record.description,
            tags: parseTags(record.tagsJson),
            totalShows: record.totalShows,
            primaryTag: record.primaryTag
        )
    }

    private func parseTags(_ json: String) -> [String] {
        guard let data = json.data(using: .utf8),
              let tags = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return tags
    }

    private func parseShowIds(_ json: String) -> [String] {
        guard let data = json.data(using: .utf8),
              let ids = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return ids
    }

    private func extractTags(from records: [DeadCollectionRecord]) -> [String] {
        var seen = Set<String>()
        var result: [String] = []
        for record in records {
            for tag in parseTags(record.tagsJson) {
                if seen.insert(tag).inserted {
                    result.append(tag)
                }
            }
        }
        return result.sorted()
    }
}
