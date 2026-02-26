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

    // Import / Export state
    @State private var showingLibraryFilePicker = false
    @State private var libraryImportResult: LibraryImportResult?
    @State private var libraryImportError: String?
    @State private var showingLibraryImportAlert = false
    @State private var libraryExportData: Data?
    @State private var showingLibraryExportShare = false

    // QR Code sheet state
    @State private var qrCodeShow: LibraryShow?

    private var filteredShows: [LibraryShow] {
        service.shows.filter { libraryShow in
            guard let decade = activeDecadeFilter else { return true }
            let decadeRange = (decade...decade + 9)
            guard decadeRange.contains(libraryShow.show.year) else { return false }
            if let season = activeSeasonFilter {
                let month = showMonth(libraryShow)
                return season.months.contains(month)
            }
            return true
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            filterChips
            sortAndDisplayControls
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
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let mode = LibraryDisplayMode(rawValue: container.appPreferences.libraryDisplayMode) {
                displayMode = mode
            }
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
            ToolbarItem(placement: .topBarLeading) {
                HStack(spacing: 8) {
                    Image("deadly_logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 28, height: 28)
                    Text("Your Library")
                        .font(.title3)
                        .fontWeight(.bold)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    NavigationLink(value: LibraryRoute.downloads) {
                        Label("Downloads", systemImage: "arrow.down.circle")
                    }
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
        .sheet(item: $qrCodeShow) { libraryShow in
            QRCodeView(show: libraryShow.show)
        }
    }

    // MARK: - Filter chips

    private var filterChips: some View {
        VStack(spacing: 4) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(decades) { decade in
                        chipButton(
                            label: decade.label,
                            isActive: activeDecadeFilter == decade.range.lowerBound
                        ) {
                            if activeDecadeFilter == decade.range.lowerBound {
                                activeDecadeFilter = nil
                                activeSeasonFilter = nil
                            } else {
                                activeDecadeFilter = decade.range.lowerBound
                                activeSeasonFilter = nil
                            }
                        }
                    }
                }
                .padding(.horizontal, DeadlySpacing.screenPadding)
            }
            .fixedSize(horizontal: false, vertical: true)

            if activeDecadeFilter != nil {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Season.allCases, id: \.self) { season in
                            chipButton(
                                label: season.rawValue,
                                isActive: activeSeasonFilter == season
                            ) {
                                activeSeasonFilter = (activeSeasonFilter == season) ? nil : season
                            }
                        }
                    }
                    .padding(.horizontal, DeadlySpacing.screenPadding)
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 2)
    }

    private func chipButton(label: String, isActive: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.caption)
                .fontWeight(.medium)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isActive ? DeadlyColors.primary : Color(.systemGray5))
                .foregroundStyle(isActive ? .white : .primary)
                .clipShape(Capsule())
        }
    }

    // MARK: - Sort and display controls

    private var sortAndDisplayControls: some View {
        HStack {
            Menu {
                ForEach(LibrarySortOption.allCases, id: \.self) { option in
                    Button(sortOptionLabel(option)) { sortOption = option }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.up.arrow.down")
                    Text(sortOptionLabel(sortOption))
                        .fontWeight(.medium)
                }
                .font(.body)
                .frame(minHeight: 44)
            }

            Button {
                sortDirection = sortDirection == .ascending ? .descending : .ascending
            } label: {
                Image(systemName: sortDirection == .ascending ? "chevron.up" : "chevron.down")
                    .font(.subheadline)
                    .frame(minWidth: 44, minHeight: 44)
            }

            Spacer()

            Button {
                let newMode: LibraryDisplayMode = displayMode == .list ? .grid : .list
                displayMode = newMode
                container.appPreferences.libraryDisplayMode = newMode.rawValue
            } label: {
                Image(systemName: displayMode == .list ? "square.grid.2x2" : "list.bullet")
                    .font(.body)
                    .frame(minWidth: 44, minHeight: 44)
            }
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
    }

    // MARK: - List view

    private var listView: some View {
        List {
            ForEach(filteredShows) { libraryShow in
                ShowRowView(libraryShow: libraryShow)
                    .contextMenu { libraryContextMenu(for: libraryShow) }
            }
            .onDelete { indexSet in
                for index in indexSet {
                    let libraryShow = filteredShows[index]
                    try? service.removeFromLibrary(showId: libraryShow.show.id)
                }
                service.refresh(sortedBy: sortOption, direction: sortDirection)
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Grid view

    private let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

    private var gridView: some View {
        GeometryReader { geo in
            let cardSize = (geo.size.width - DeadlySpacing.screenPadding * 2 - DeadlySpacing.gridSpacing * 2) / 3
            ScrollView {
                LazyVGrid(columns: columns, spacing: DeadlySpacing.itemSpacing) {
                    ForEach(filteredShows) { libraryShow in
                        NavigationLink(value: libraryShow.show.id) {
                            gridCard(libraryShow, size: cardSize)
                        }
                        .buttonStyle(.plain)
                        .contextMenu { libraryContextMenu(for: libraryShow) }
                    }
                }
                .padding(DeadlySpacing.screenPadding)
            }
        }
    }

    private func gridCard(_ libraryShow: LibraryShow, size: CGFloat) -> some View {
        let show = libraryShow.show
        let downloadStatus = container.downloadService.downloadStatus(for: show.id)
        return VStack(alignment: .leading, spacing: 4) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: size,
                cornerRadius: DeadlySize.cardCornerRadius
            )

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 2) {
                    if libraryShow.isPinned {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(DeadlyColors.primary)
                    }
                    if downloadStatus == .completed {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(DeadlyColors.primary)
                    }
                    Text(DateFormatting.formatShowDate(show.date, style: .short))
                        .font(.caption2)
                        .fontWeight(.semibold)
                        .lineLimit(1)
                }

                Text(show.venue.name)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Text(show.location.displayText)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .padding(.horizontal, 2)
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

    private func showMonth(_ libraryShow: LibraryShow) -> Int {
        let parts = libraryShow.show.date.split(separator: "-")
        guard parts.count >= 2, let m = Int(parts[1]) else { return 0 }
        return m
    }

    // MARK: - Context menu

    @ViewBuilder
    private func libraryContextMenu(for libraryShow: LibraryShow) -> some View {
        let showId = libraryShow.show.id
        let show = libraryShow.show

        // Share
        ShareLink(item: shareText(for: show)) {
            Label("Share", systemImage: "square.and.arrow.up")
        }

        // QR Code
        Button {
            qrCodeShow = libraryShow
        } label: {
            Label("Show QR Code", systemImage: "qrcode")
        }

        Divider()

        // Pin / Unpin
        Button {
            try? service.togglePin(showId: showId)
            service.refresh(sortedBy: sortOption, direction: sortDirection)
        } label: {
            Label(
                libraryShow.isPinned ? "Unpin" : "Pin to Top",
                systemImage: libraryShow.isPinned ? "pin.slash" : "pin"
            )
        }

        Divider()

        // Download actions (check live status from downloadService)
        let downloadStatus = container.downloadService.downloadStatus(for: showId)
        switch downloadStatus {
        case .notDownloaded, .cancelled, .failed:
            Button {
                Task { try? await container.downloadService.downloadShow(showId, recordingId: nil) }
            } label: {
                Label("Download", systemImage: "arrow.down.circle")
            }
        case .queued, .downloading:
            Button {
                container.downloadService.pauseShow(showId)
            } label: {
                Label("Pause Download", systemImage: "pause.circle")
            }
            Button(role: .destructive) {
                container.downloadService.cancelShow(showId)
            } label: {
                Label("Cancel Download", systemImage: "xmark.circle")
            }
        case .paused:
            Button {
                container.downloadService.resumeShow(showId)
            } label: {
                Label("Resume Download", systemImage: "play.circle")
            }
            Button(role: .destructive) {
                container.downloadService.cancelShow(showId)
            } label: {
                Label("Cancel Download", systemImage: "xmark.circle")
            }
        case .completed:
            Button(role: .destructive) {
                container.downloadService.removeShow(showId)
            } label: {
                Label("Remove Download", systemImage: "trash")
            }
        }

        Divider()

        // Remove from Library
        Button(role: .destructive) {
            try? service.removeFromLibrary(showId: showId)
            service.refresh(sortedBy: sortOption, direction: sortDirection)
        } label: {
            Label("Remove from Library", systemImage: "minus.circle")
        }
    }

    private func shareText(for show: Show) -> String {
        var lines: [String] = []
        lines.append("🌹⚡💀 Grateful Dead 💀⚡🌹")
        lines.append("")
        lines.append("📅 \(show.date)")
        lines.append("📍 \(show.venue.name)")
        let loc = show.venue.displayLocation
        if !loc.isEmpty { lines.append("🌎 \(loc)") }
        if show.hasRating { lines.append("⭐ Rating: \(show.displayRating)") }
        if let recordingId = show.bestRecordingId {
            lines.append("")
            lines.append("🔗 Listen in The Deadly app:")
            lines.append("https://share.thedeadly.app/show/\(show.id)/recording/\(recordingId)")
        }
        return lines.joined(separator: "\n")
    }
}
