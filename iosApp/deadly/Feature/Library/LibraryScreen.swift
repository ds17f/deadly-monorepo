import SwiftUI
import UniformTypeIdentifiers

// MARK: - Season

private enum Season: String, CaseIterable {
    case spring = "Spring"
    case summer = "Summer"
    case fall = "Fall"
    case winter = "Winter"

    var months: [Int] {
        switch self {
        case .spring: return [3, 4, 5]
        case .summer: return [6, 7, 8]
        case .fall:   return [9, 10, 11]
        case .winter: return [12, 1, 2]
        }
    }
}

// MARK: - Decade

private struct Decade: Identifiable {
    let label: String
    let range: ClosedRange<Int>
    var id: Int { range.lowerBound }
}

private let decades: [Decade] = [
    Decade(label: "60s", range: 1960...1969),
    Decade(label: "70s", range: 1970...1979),
    Decade(label: "80s", range: 1980...1989),
    Decade(label: "90s", range: 1990...1999),
]

// MARK: - LibraryScreen

struct LibraryScreen: View {
    @Environment(\.appContainer) private var container
    private var service: LibraryServiceImpl { container.libraryService }

    @State private var sortOption: LibrarySortOption = .dateAdded
    @State private var sortDirection: LibrarySortDirection = .descending
    @State private var displayMode: LibraryDisplayMode = .list
    @State private var activeDecadeFilter: Int?
    @State private var activeSeasonFilter: Season?
    @State private var showingFilterSheet = false

    // Import / Export state
    @State private var showingLibraryFilePicker = false
    @State private var libraryImportResult: LibraryImportResult?
    @State private var libraryImportError: String?
    @State private var showingLibraryImportAlert = false
    @State private var libraryExportData: Data?
    @State private var showingLibraryExportShare = false

    private var filteredShows: [Show] {
        service.shows.filter { show in
            guard let decade = activeDecadeFilter else { return true }
            let decadeRange = (decade...decade + 9)
            guard decadeRange.contains(show.year) else { return false }
            if let season = activeSeasonFilter {
                let month = showMonth(show)
                return season.months.contains(month)
            }
            return true
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            headerBar
            Divider()

            if service.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if service.shows.isEmpty {
                ContentUnavailableView(
                    "No Shows Saved",
                    systemImage: "bookmark",
                    description: Text("Import your library from the old app or browse shows to add them.")
                )
            } else if filteredShows.isEmpty {
                ContentUnavailableView(
                    "No Matching Shows",
                    systemImage: "line.3.horizontal.decrease.circle",
                    description: Text("Try adjusting the filter.")
                )
            } else {
                switch displayMode {
                case .list: listView
                case .grid: gridView
                }
            }
        }
        .navigationTitle("Library")
        .sheet(isPresented: $showingFilterSheet) {
            filterSheet
        }
        .task {
            service.refresh(sortedBy: sortOption, direction: sortDirection)
        }
        .onChange(of: sortOption) { _, new in
            service.refresh(sortedBy: new, direction: sortDirection)
        }
        .onChange(of: sortDirection) { _, new in
            service.refresh(sortedBy: sortOption, direction: new)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        showingLibraryFilePicker = true
                    } label: {
                        Label("Import Library", systemImage: "square.and.arrow.down")
                    }
                    Button {
                        libraryExportData = try? container.libraryImportExportService.exportLibrary()
                        if libraryExportData != nil { showingLibraryExportShare = true }
                    } label: {
                        Label("Export Library", systemImage: "square.and.arrow.up")
                    }
                    .disabled(service.shows.isEmpty)
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .fileImporter(
            isPresented: $showingLibraryFilePicker,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else {
                    libraryImportError = "Could not access file."
                    showingLibraryImportAlert = true
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                guard let data = try? Data(contentsOf: url) else {
                    libraryImportError = "Could not read file."
                    showingLibraryImportAlert = true
                    return
                }
                do {
                    libraryImportResult = try container.libraryImportExportService.importLibrary(from: data)
                    libraryImportError = nil
                    service.refresh(sortedBy: sortOption, direction: sortDirection)
                } catch {
                    libraryImportError = error.localizedDescription
                    libraryImportResult = nil
                }
                showingLibraryImportAlert = true
            case .failure(let error):
                libraryImportError = error.localizedDescription
                showingLibraryImportAlert = true
            }
        }
        .alert("Library Import", isPresented: $showingLibraryImportAlert) {
            Button("OK") {}
        } message: {
            if let result = libraryImportResult {
                Text("Imported \(result.imported) shows.\n\(result.alreadyInLibrary) already in library.\n\(result.notFound) not found in database.")
            } else {
                Text(libraryImportError ?? "Unknown error.")
            }
        }
        .sheet(isPresented: $showingLibraryExportShare) {
            if let data = libraryExportData {
                LibraryExportShareSheet(data: data, filename: container.libraryImportExportService.exportFilename())
            }
        }
    }

    // MARK: - Header bar

    private var headerBar: some View {
        HStack(spacing: 12) {
            Text("\(service.shows.count) shows")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Spacer()

            Menu {
                ForEach(LibrarySortOption.allCases, id: \.self) { option in
                    Button(sortOptionLabel(option)) {
                        sortOption = option
                    }
                }
            } label: {
                Label(sortOptionLabel(sortOption), systemImage: "arrow.up.arrow.down")
                    .font(.subheadline)
            }

            Button {
                sortDirection = sortDirection == .ascending ? .descending : .ascending
            } label: {
                Image(systemName: sortDirection == .ascending ? "arrow.up" : "arrow.down")
            }

            Button {
                displayMode = displayMode == .list ? .grid : .list
            } label: {
                Image(systemName: displayMode == .list ? "square.grid.2x2" : "list.bullet")
            }

            Button {
                showingFilterSheet = true
            } label: {
                Image(systemName: "line.3.horizontal.decrease.circle")
                    .foregroundStyle(activeDecadeFilter != nil ? DeadlyColors.primary : .primary)
            }
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
        .padding(.vertical, 8)
    }

    // MARK: - List view

    private var listView: some View {
        List {
            ForEach(filteredShows) { show in
                ShowRowView(show: show)
            }
            .onDelete { indexSet in
                for index in indexSet {
                    let show = filteredShows[index]
                    try? service.removeFromLibrary(showId: show.id)
                }
                service.refresh(sortedBy: sortOption, direction: sortDirection)
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Grid view

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    private var gridView: some View {
        GeometryReader { geo in
            let cardSize = (geo.size.width - DeadlySpacing.screenPadding * 2 - DeadlySpacing.gridSpacing) / 2
            ScrollView {
                LazyVGrid(columns: columns, spacing: DeadlySpacing.gridSpacing) {
                    ForEach(filteredShows) { show in
                        NavigationLink(value: show.id) {
                            gridCard(show, size: cardSize)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(DeadlySpacing.screenPadding)
            }
        }
    }

    private func gridCard(_ show: Show, size: CGFloat) -> some View {
        ZStack(alignment: .bottomLeading) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: size,
                cornerRadius: DeadlySize.cardCornerRadius
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(DateFormatting.formatShowDate(show.date, style: .short))
                    .font(.caption)
                    .fontWeight(.semibold)
                Text(show.venue.name)
                    .font(.caption2)
                    .lineLimit(1)
            }
            .foregroundStyle(.white)
            .padding(6)
            .background(.black.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
            .padding(4)
        }
    }

    // MARK: - Filter sheet

    private var filterSheet: some View {
        NavigationStack {
            List {
                Button("Clear Filters") {
                    activeDecadeFilter = nil
                    activeSeasonFilter = nil
                }
                .foregroundStyle(.red)

                ForEach(decades) { decade in
                    DisclosureGroup(decade.label) {
                        ForEach(Season.allCases, id: \.self) { season in
                            Button {
                                activeDecadeFilter = decade.range.lowerBound
                                activeSeasonFilter = season
                                showingFilterSheet = false
                            } label: {
                                HStack {
                                    Text(season.rawValue)
                                    Spacer()
                                    if activeDecadeFilter == decade.range.lowerBound && activeSeasonFilter == season {
                                        Image(systemName: "checkmark")
                                            .foregroundStyle(DeadlyColors.primary)
                                    }
                                }
                            }
                            .foregroundStyle(.primary)
                        }
                    }
                }
            }
            .navigationTitle("Filter")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { showingFilterSheet = false }
                }
            }
        }
    }

    // MARK: - Helpers

    private func sortOptionLabel(_ option: LibrarySortOption) -> String {
        switch option {
        case .dateAdded:  return "Date Added"
        case .dateOfShow: return "Show Date"
        case .venue:      return "Venue"
        case .rating:     return "Rating"
        }
    }

    private func showMonth(_ show: Show) -> Int {
        let parts = show.date.split(separator: "-")
        guard parts.count >= 2, let m = Int(parts[1]) else { return 0 }
        return m
    }
}
