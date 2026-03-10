import SwiftUI

struct SearchScreen: View {
    @Environment(\.appContainer) private var container
    private var searchService: SearchServiceImpl { container.searchService }

    var resetToken: Int = 0

    @State private var searchText = ""
    @FocusState private var isSearchFieldFocused: Bool
    @State private var searchTask: Task<Void, Never>?

    @State private var sortOption: SearchSortOption = .dateOfShow
    @State private var sortDirection: SearchSortDirection = .ascending

    @State private var eraOverride: [SearchResultShow]?
    @State private var eraLabel: String?

    @State private var filterPath = FilterPath()

    @State private var topRated: [Show] = []
    @State private var randomShow: Show?
    @State private var refreshCounter = 0

    /// Show results when the search bar is active or an era is selected.
    private var showingResults: Bool {
        eraOverride != nil || isSearchFieldFocused || !searchText.isEmpty
    }

    private var displayResults: [SearchResultShow] {
        let base = eraOverride ?? searchService.results
        let filtered: [SearchResultShow]
        if filterPath.isNotEmpty, let range = yearRange(for: filterPath) {
            filtered = base.filter { range.contains($0.show.year) }
        } else {
            filtered = base
        }
        if filterPath.isNotEmpty {
            // Force date ascending when a decade filter is active
            return filtered.sorted { $0.show.date < $1.show.date }
        }
        return sortResults(filtered)
    }

    var body: some View {
        VStack(spacing: 0) {
            searchBar
            if showingResults {
                resultsView
            } else {
                browseView
            }
        }
        .toolbar(showingResults ? .hidden : .visible, for: .navigationBar)
        .animation(.easeInOut(duration: 0.2), value: showingResults)
        .onChange(of: resetToken) { _, _ in
            // Tab re-tapped — return to browse
            searchTask?.cancel()
            searchText = ""
            isSearchFieldFocused = false
            searchService.clearResults()
            eraOverride = nil
            eraLabel = nil
            filterPath = FilterPath()
        }
        .onChange(of: isSearchFieldFocused) { _, isFocused in
            if !isFocused && searchText.isEmpty {
                searchTask?.cancel()
                searchService.clearResults()
                eraOverride = nil
                eraLabel = nil
                filterPath = FilterPath()
            }
        }
        .onChange(of: searchText) { _, newValue in
            eraOverride = nil
            eraLabel = nil
            filterPath = FilterPath()
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

    // MARK: - Search bar

    private var searchBar: some View {
        HStack(spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                TextField("What do you want to listen to?", text: $searchText)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .focused($isSearchFieldFocused)
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

    // MARK: - Browse view

    private var browseView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                eraSection
                discoverSection
                browseAllSection
            }
            .padding(.top, DeadlySpacing.screenPadding)
            .padding(.bottom, DeadlySpacing.screenPadding)
        }
        .refreshable {
            refreshCounter += 1
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
                eraButton("60s", imageName: "decade_1960s")
                eraButton("70s", imageName: "decade_1970s")
                eraButton("80s", imageName: "decade_1980s")
                eraButton("90s", imageName: "decade_1990s")
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    private func eraButton(_ decade: String, imageName: String) -> some View {
        Button {
            loadEra(decade)
        } label: {
            Image(imageName)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(maxWidth: .infinity)
                .frame(height: 80)
                .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        }
        .buttonStyle(.plain)
    }

    private func loadEra(_ decade: String) {
        searchText = ""
        searchTask?.cancel()
        // Load all shows upfront — chips handle the decade filtering
        loadAllEras()
        eraLabel = decade
        let tree = FilterNode.decadeCascadeTree()
        if let node = tree.first(where: { $0.id == decade }) {
            filterPath = FilterPath(nodes: [node])
        }
    }

    // MARK: Discover section

    private let discoverGradients: [[Color]] = [
        [Color(hex: "1976D2"), Color(hex: "42A5F5")],  // Blue
        [Color(hex: "388E3C"), Color(hex: "66BB6A")],  // Green
        [Color(hex: "D32F2F"), Color(hex: "EF5350")],  // Red
        [Color(hex: "7B1FA2"), Color(hex: "AB47BC")],  // Purple
        [Color(hex: "E64A19"), Color(hex: "FF7043")],  // Orange
        [Color(hex: "00796B"), Color(hex: "26A69A")],  // Teal
    ]

    private var discoverSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Discover")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            HStack(spacing: DeadlySpacing.gridSpacing) {
                ForEach(Array(discoverShortcuts(refreshCounter: refreshCounter).enumerated()), id: \.element.id) { index, shortcut in
                    discoverCard(shortcut, gradientIndex: index)
                }
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    private func discoverCard(_ shortcut: SearchShortcut, gradientIndex: Int) -> some View {
        let gradient = discoverGradients[gradientIndex % discoverGradients.count]

        return Button {
            activateSearch(query: shortcut.searchQuery)
        } label: {
            ZStack(alignment: .topTrailing) {
                // Gradient background
                LinearGradient(
                    colors: gradient,
                    startPoint: .top,
                    endPoint: .bottom
                )

                // Logo watermark
                Image("deadly_logo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 40, height: 40)
                    .opacity(0.2)
                    .padding(8)

                // Title and subtitle
                VStack(alignment: .leading, spacing: 4) {
                    Spacer()
                    Text(shortcut.title)
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                        .lineLimit(2)
                    Text(shortcut.subtitle)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
            }
            .frame(height: 220)
            .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        }
        .buttonStyle(.plain)
    }

    // MARK: Browse all section

    private let browseAllColors: [Color] = [
        Color(hex: "1976D2"),  // Blue
        Color(hex: "388E3C"),  // Green
        Color(hex: "D32F2F"),  // Red
        Color(hex: "7B1FA2"),  // Purple
        Color(hex: "E64A19"),  // Orange
        Color(hex: "00796B"),  // Teal
        Color(hex: "5D4037"),  // Brown
        Color(hex: "455A64"),  // Blue Grey
    ]

    private var browseAllSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Browse All")
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, DeadlySpacing.screenPadding)

            LazyVGrid(columns: eraColumns, spacing: DeadlySpacing.gridSpacing) {
                ForEach(Array(browseShortcuts(refreshCounter: refreshCounter).enumerated()), id: \.element.id) { index, shortcut in
                    browseCard(shortcut, colorIndex: index)
                }
            }
            .padding(.horizontal, DeadlySpacing.screenPadding)
        }
    }

    // MARK: Browse card

    private func browseCard(_ shortcut: SearchShortcut, colorIndex: Int) -> some View {
        let backgroundColor = browseAllColors[colorIndex % browseAllColors.count]

        return Button {
            activateSearch(query: shortcut.searchQuery)
        } label: {
            VStack(spacing: 6) {
                Spacer()
                Text(shortcut.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                Text(shortcut.subtitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(1)
                Spacer()
            }
            .frame(maxWidth: .infinity)
            .frame(height: 120)
            .background(
                backgroundColor,
                in: RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Results view

    private var resultsView: some View {
        VStack(spacing: 0) {
            if !displayResults.isEmpty || eraOverride != nil {
                resultsHeader
                if eraOverride != nil || !searchService.results.isEmpty {
                    HierarchicalFilterChips(
                        filterTree: FilterNode.decadeCascadeTree(),
                        selectedPath: $filterPath
                    )
                    .padding(.vertical, 4)
                }
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
        filterPath = FilterPath()
    }

    private func loadAllEras() {
        var all: [SearchResultShow] = []
        for decade in ["60s", "70s", "80s", "90s"] {
            if let shows = try? searchService.searchByEra(decade) {
                all.append(contentsOf: shows.map {
                    SearchResultShow(show: $0, relevanceScore: 1.0, matchType: .year, hasDownloads: false, highlightedFields: [])
                })
            }
        }
        eraOverride = all
        eraLabel = nil
    }

    /// Activates search UI and immediately runs search (skips debounce)
    private func activateSearch(query: String) {
        searchTask?.cancel()
        searchText = query
        isSearchFieldFocused = true
        Task { await searchService.search(query: query) }
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

    // MARK: - Sorting

    private func yearRange(for path: FilterPath) -> ClosedRange<Int>? {
        guard let deepest = path.nodes.last else { return nil }

        // Specific year
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
