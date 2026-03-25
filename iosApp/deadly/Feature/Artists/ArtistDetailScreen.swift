import SwiftUI

struct ArtistDetailScreen: View {
    let artist: Artist
    @Environment(\.appContainer) private var container

    @State private var shows: [ArchiveShow] = []
    @State private var isLoading = false
    @State private var error: String?
    @State private var currentPage = 1
    @State private var totalCount = 0
    @State private var hasMore = true

    private let pageSize = 50

    var body: some View {
        Group {
            if isLoading && shows.isEmpty {
                ProgressView("Loading shows…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error, shows.isEmpty {
                ContentUnavailableView(
                    "Unable to Load",
                    systemImage: "exclamationmark.triangle",
                    description: Text(error)
                )
            } else if shows.isEmpty {
                ContentUnavailableView(
                    "No Shows",
                    systemImage: "music.note.list",
                    description: Text("No recordings found for \(artist.name)")
                )
            } else {
                showList
            }
        }
        .navigationTitle(artist.name)
        .navigationBarTitleDisplayMode(.large)
        .task { await loadFirstPage() }
    }

    // MARK: - Show List

    private var showList: some View {
        List {
            Section {
                ForEach(shows) { show in
                    NavigationLink(value: show.identifier) {
                        ArtistShowRow(show: show)
                    }
                    .buttonStyle(.plain)
                }

                if hasMore {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .task { await loadNextPage() }
                }
            } header: {
                Text("\(totalCount) recordings")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Loading

    private func loadFirstPage() async {
        guard shows.isEmpty else { return }
        isLoading = true
        error = nil
        defer { isLoading = false }

        do {
            let result = try await container.archiveSearchClient.searchShows(
                artist: artist, page: 1, pageSize: pageSize
            )
            shows = result.shows
            totalCount = result.totalCount
            currentPage = 1
            hasMore = shows.count < totalCount
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func loadNextPage() async {
        guard !isLoading, hasMore else { return }
        isLoading = true
        defer { isLoading = false }

        let nextPage = currentPage + 1
        do {
            let result = try await container.archiveSearchClient.searchShows(
                artist: artist, page: nextPage, pageSize: pageSize
            )
            shows.append(contentsOf: result.shows)
            currentPage = nextPage
            hasMore = shows.count < result.totalCount
        } catch {
            // Silently fail on pagination — user still has existing results
        }
    }
}
