import SwiftUI

struct ArtistDetailScreen: View {
    let artist: Artist
    @Environment(\.appContainer) private var container

    // Remote artist state
    @State private var shows: [ArchiveShow] = []
    @State private var isLoading = false
    @State private var error: String?
    @State private var currentPage = 1
    @State private var totalCount = 0
    @State private var hasMore = true

    // Local artist state (browse + search)
    @State private var localShows: [Show] = []
    @State private var searchText = ""
    @FocusState private var isSearchFieldFocused: Bool
    @State private var searchTask: Task<Void, Never>?

    @State private var sortOption: SearchSortOption = .dateOfShow
    @State private var sortDirection: SearchSortDirection = .ascending

    @State private var eraOverride: [SearchResultShow]?
    @State private var eraLabel: String?
    @State private var filterPath = FilterPath()
    @State private var sourceFilterPath = FilterPath()
    @State private var refreshCounter = 0

    private let pageSize = 50

    private var searchService: SearchServiceImpl { container.searchService }

    private var showingResults: Bool {
        eraOverride != nil || isSearchFieldFocused || !searchText.isEmpty
    }

    var body: some View {
        Group {
            if artist.hasLocalData {
                localArtistBody
            } else {
                remoteArtistBody
            }
        }
        .navigationTitle(artist.name)
        .navigationBarTitleDisplayMode(.large)
    }

    // MARK: - Local Artist (rich browse experience)

    private var localArtistBody: some View {
        VStack(spacing: 0) {
            localSearchBar
            if showingResults {
                localResultsView
            } else {
                localBrowseView
            }
        }
        .task {
            localShows = (try? container.showRepository.getAllShows()) ?? []
        }
        .onChange(of: searchText) { _, newValue in
            eraOverride = nil
            eraLabel = nil
            filterPath = FilterPath()
            if newValue.isEmpty {
                searchService.clearResults()
                if isSearchFieldFocused {
                    loadAllEras()
                }
                return
            }
            searchTask?.cancel()
            searchTask = Task {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
                await searchService.search(query: newValue)
            }
        }
        .onChange(of: isSearchFieldFocused) { _, isFocused in
            if isFocused && searchText.isEmpty && eraOverride == nil {
                loadAllEras()
            } else if !isFocused && searchText.isEmpty && eraOverride == nil {
                searchTask?.cancel()
                searchService.clearResults()
                eraOverride = nil
                eraLabel = nil
                filterPath = FilterPath()
                sourceFilterPath = FilterPath()
            }
        }
    }

    // MARK: - Local search bar

    private var localSearchBar: some View {
        HStack(spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                TextField("Search within \(artist.name)", text: $searchText)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .focused($isSearchFieldFocused)
                    .toolbar {
                        ToolbarItemGroup(placement: .keyboard) {
                            Spacer()
                            Button("Done") {
                                isSearchFieldFocused = false
                            }
                        }
                    }
                    .onSubmit {
                        searchTask?.cancel()
                        eraOverride = nil
                        eraLabel = nil
                        Task { await searchService.search(query: searchText) }
                    }
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(10)
            .background(Color(.systemGray5), in: RoundedRectangle(cornerRadius: 12))

            if showingResults {
                Button("Cancel") {
                    searchText = ""
                    isSearchFieldFocused = false
                    searchTask?.cancel()
                    searchService.clearResults()
                    eraOverride = nil
                    eraLabel = nil
                }
                .foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
        .padding(.vertical, DeadlySpacing.screenPadding)
    }

    // MARK: - Local browse view

    private var localBrowseView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                EraSection { decade in
                    loadEra(decade)
                }

                DiscoverSection(refreshCounter: refreshCounter) { query in
                    activateSearch(query: query)
                }

                BrowseAllSection(refreshCounter: refreshCounter) { query in
                    activateSearch(query: query)
                }

                // Paginated show list
                if !localShows.isEmpty {
                    localShowListSection
                }
            }
            .padding(.top, DeadlySpacing.screenPadding)
            .padding(.bottom, DeadlySpacing.screenPadding)
        }
        .refreshable {
            refreshCounter += 1
        }
    }

    private var localShowListSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localShows.count) recordings")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            LazyVStack(spacing: 0) {
                ForEach(localShows.map { $0.toArchiveShow() }) { show in
                    NavigationLink(value: show.identifier) {
                        ArtistShowRow(show: show)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, DeadlySpacing.screenPadding)
                    .padding(.vertical, 6)
                    Divider()
                        .padding(.leading, DeadlySpacing.screenPadding)
                }
            }
        }
    }

    // MARK: - Local results view

    private var displayResults: [SearchResultShow] {
        let base = eraOverride ?? searchService.results
        var filtered: [SearchResultShow]
        if filterPath.isNotEmpty, let range = yearRange(for: filterPath) {
            filtered = base.filter { range.contains($0.show.year) }
        } else {
            filtered = base
        }
        if sourceFilterPath.isNotEmpty, let sourceNode = sourceFilterPath.nodes.last {
            filtered = filtered.filter {
                sourceTypeMatches($0.show.bestSourceType, filterId: sourceNode.id)
            }
        }
        if filterPath.isNotEmpty {
            return filtered.sorted { $0.show.date < $1.show.date }
        }
        return sortResults(filtered)
    }

    private var localResultsView: some View {
        VStack(spacing: 0) {
            if !displayResults.isEmpty || eraOverride != nil {
                if eraOverride != nil || !searchService.results.isEmpty {
                    HierarchicalFilterChips(
                        filterTree: FilterNode.decadeCascadeTree(),
                        selectedPath: $filterPath
                    )
                    .padding(.bottom, 4)
                    HierarchicalFilterChips(
                        filterTree: FilterNode.sourceTypeTree(),
                        selectedPath: $sourceFilterPath
                    )
                    .padding(.bottom, 8)
                }
                localResultsHeader
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
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(displayResults) { result in
                            SearchResultRow(result: result)
                                .padding(.horizontal, DeadlySpacing.screenPadding)
                                .padding(.vertical, 6)
                            Divider()
                                .padding(.leading, DeadlySpacing.screenPadding)
                        }
                    }
                }
                .scrollDismissesKeyboard(.immediately)
            }
        }
    }

    private var localResultsHeader: some View {
        HStack(spacing: 12) {
            if eraOverride != nil {
                Button(action: clearEra) {
                    Image(systemName: "chevron.left")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
            }

            if filterPath.isNotEmpty {
                let total = (eraOverride ?? searchService.results).count
                Text("\(displayResults.count) of \(total) shows")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                Text("\(displayResults.count) shows")
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

    // MARK: - Local helpers

    private func sourceTypeMatches(_ sourceType: RecordingSourceType, filterId: String) -> Bool {
        switch filterId {
        case "SBD": return sourceType == .soundboard
        case "AUD": return sourceType == .audience
        case "FM": return sourceType == .fm
        case "MATRIX": return sourceType == .matrix
        default: return false
        }
    }

    private func yearRange(for path: FilterPath) -> ClosedRange<Int>? {
        guard let deepest = path.nodes.last else { return nil }
        if let year = Int(deepest.id) { return year...year }
        switch deepest.id {
        case "early_70s": return 1970...1974
        case "late_70s":  return 1975...1979
        case "early_80s": return 1980...1984
        case "late_80s":  return 1985...1989
        case "60s":       return 1965...1969
        case "70s":       return 1970...1979
        case "80s":       return 1980...1989
        case "90s":       return 1990...1995
        default:          return nil
        }
    }

    private func sortResults(_ results: [SearchResultShow]) -> [SearchResultShow] {
        results.sorted { a, b in
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

    private func loadEra(_ decade: String) {
        searchText = ""
        searchTask?.cancel()
        loadAllEras()
        eraLabel = decade
        let tree = FilterNode.decadeCascadeTree()
        if let node = tree.first(where: { $0.id == decade }) {
            filterPath = FilterPath(nodes: [node])
        }
    }

    private func loadAllEras() {
        var all: [SearchResultShow] = []
        for decade in ["60s", "70s", "80s", "90s"] {
            if let shows = try? searchService.searchByEra(decade) {
                all.append(contentsOf: shows.map {
                    SearchResultShow(show: $0, relevanceScore: 1.0, matchType: .year)
                })
            }
        }
        eraOverride = all
        eraLabel = nil
    }

    private func clearEra() {
        eraOverride = nil
        eraLabel = nil
        filterPath = FilterPath()
        sourceFilterPath = FilterPath()
    }

    private func activateSearch(query: String) {
        searchTask?.cancel()
        searchText = query
        isSearchFieldFocused = true
        Task { await searchService.search(query: query) }
    }

    // MARK: - Remote Artist (paginated IA API)

    private var remoteArtistBody: some View {
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
                remoteShowList
            }
        }
        .task { await loadFirstPage() }
    }

    private var remoteShowList: some View {
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

    // MARK: - Remote loading

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
