import SwiftUI

struct SearchScreen: View {
    @Environment(\.appContainer) private var container
    private var searchService: SearchServiceImpl { container.searchService }

    var resetToken: Int = 0

    @State private var searchText = ""
    @State private var isSearchPresented = false
    @State private var searchTask: Task<Void, Never>?

    @State private var sortOption: SearchSortOption = .relevance
    @State private var sortDirection: SearchSortDirection = .descending

    @State private var eraOverride: [SearchResultShow]?
    @State private var eraLabel: String?

    @State private var topRated: [Show] = []
    @State private var randomShow: Show?

    /// Show results when the search bar is active or an era is selected.
    private var showingResults: Bool {
        eraOverride != nil || isSearchPresented
    }

    private var displayResults: [SearchResultShow] {
        let base = eraOverride ?? searchService.results
        return sortResults(base)
    }

    var body: some View {
        Group {
            if showingResults {
                resultsView
            } else {
                browseView
            }
        }
        .navigationTitle("Search")
        .searchable(text: $searchText, isPresented: $isSearchPresented, prompt: "Shows, venues, songs...")
        .onSubmit(of: .search) {
            searchTask?.cancel()
            eraOverride = nil
            eraLabel = nil
            Task { await searchService.search(query: searchText) }
        }
        .onChange(of: resetToken) { _, _ in
            // Tab re-tapped — return to browse
            searchTask?.cancel()
            searchText = ""
            isSearchPresented = false
            searchService.clearResults()
            eraOverride = nil
            eraLabel = nil
        }
        .onChange(of: isSearchPresented) { _, isActive in
            if !isActive {
                // "Cancel" tapped — return to browse
                searchTask?.cancel()
                searchService.clearResults()
                eraOverride = nil
                eraLabel = nil
            }
        }
        .onChange(of: searchText) { _, newValue in
            eraOverride = nil
            eraLabel = nil
            if newValue.isEmpty {
                searchService.clearResults()
                return
            }
            searchTask?.cancel()
            searchTask = Task {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
                await searchService.search(query: newValue)
            }
        }
        .task {
            topRated = (try? searchService.getTopRatedShows(limit: 10)) ?? []
            randomShow = try? searchService.getRandomShow()
        }
    }

    // MARK: - Browse view

    private var browseView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                eraSection
                discoverSection
                browseAllSection
            }
            .padding(.vertical, DeadlySpacing.screenPadding)
        }
    }

    // MARK: Era section

    private let eraColumns = [GridItem(.flexible()), GridItem(.flexible())]

    private var eraSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("By Decade")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            LazyVGrid(columns: eraColumns, spacing: DeadlySpacing.gridSpacing) {
                eraButton("60s", color: .purple)
                eraButton("70s", color: DeadlyColors.primary)
                eraButton("80s", color: DeadlyColors.tertiary)
                eraButton("90s", color: .orange)
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    private func eraButton(_ decade: String, color: Color) -> some View {
        Button {
            loadEra(decade)
        } label: {
            Text(decade)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(color, in: RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        }
        .buttonStyle(.plain)
    }

    private func loadEra(_ decade: String) {
        do {
            let shows = try searchService.searchByEra(decade)
            eraOverride = shows.map {
                SearchResultShow(show: $0, relevanceScore: 1.0, matchType: .year, hasDownloads: false, highlightedFields: [])
            }
            eraLabel = decade
        } catch {
            // silently fail
        }
    }

    // MARK: Discover section

    private var discoverSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Discover")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: DeadlySpacing.gridSpacing) {
                    ForEach(discoverShortcuts) { shortcut in
                        shortcutCard(shortcut)
                            .frame(width: 200)
                    }
                }
                .padding(.horizontal, DeadlySpacing.screenPadding)
            }
        }
    }

    // MARK: Browse all section

    private var browseAllSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Browse All")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            LazyVGrid(columns: eraColumns, spacing: DeadlySpacing.gridSpacing) {
                ForEach(browseShortcuts) { shortcut in
                    shortcutCard(shortcut)
                }
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    // MARK: Shortcut card

    private func shortcutCard(_ shortcut: SearchShortcut) -> some View {
        Button {
            searchText = shortcut.searchQuery
            isSearchPresented = true
        } label: {
            HStack(spacing: 10) {
                Image(systemName: shortcut.category.systemImage)
                    .font(.title3)
                    .foregroundStyle(shortcut.category.tintColor)
                    .frame(width: 28)

                VStack(alignment: .leading, spacing: 2) {
                    Text(shortcut.title)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Text(shortcut.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()
            }
            .padding(12)
            .background(DeadlyColors.darkSurface, in: RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Results view

    private var resultsView: some View {
        VStack(spacing: 0) {
            if !displayResults.isEmpty || eraOverride != nil {
                resultsHeader
                Divider()
            }

            if searchService.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if displayResults.isEmpty && !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else if displayResults.isEmpty {
                ContentUnavailableView(
                    "Search",
                    systemImage: "magnifyingglass",
                    description: Text("Find shows by date, venue, city, or song.")
                )
            } else {
                List(displayResults) { result in
                    SearchResultRow(result: result)
                }
                .listStyle(.plain)
            }
        }
    }

    private func clearEra() {
        eraOverride = nil
        eraLabel = nil
    }

    private var resultsHeader: some View {
        HStack(spacing: 12) {
            if eraOverride != nil {
                Button(action: clearEra) {
                    Image(systemName: "chevron.left")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
            }

            if let eraLabel {
                Text("\(displayResults.count) \(eraLabel) shows")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                Text("\(displayResults.count) results")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Menu {
                ForEach(SearchSortOption.allCases, id: \.self) { option in
                    Button(option.displayName) {
                        sortOption = option
                    }
                }
            } label: {
                Label(sortOption.displayName, systemImage: "arrow.up.arrow.down")
                    .font(.subheadline)
            }

            if sortOption != .relevance {
                Button {
                    sortDirection = sortDirection == .ascending ? .descending : .ascending
                } label: {
                    Image(systemName: sortDirection == .ascending ? "arrow.up" : "arrow.down")
                }
            }
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
        .padding(.vertical, 8)
    }

    // MARK: - Sorting

    private func sortResults(_ results: [SearchResultShow]) -> [SearchResultShow] {
        results.sorted { a, b in
            // cmp = true when a should come before b in ascending order
            let cmp: Bool
            switch sortOption {
            case .relevance:
                cmp = a.relevanceScore < b.relevanceScore
            case .rating:
                cmp = (a.show.averageRating ?? 0) < (b.show.averageRating ?? 0)
            case .dateOfShow:
                cmp = a.show.date < b.show.date
            case .venue:
                cmp = a.show.venue.name.localizedCompare(b.show.venue.name) == .orderedAscending
            case .state:
                cmp = (a.show.location.state ?? "") < (b.show.location.state ?? "")
            }
            return sortDirection == .ascending ? cmp : !cmp
        }
    }
}
