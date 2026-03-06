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

// MARK: - FavoritesScreen

struct FavoritesScreen: View {
    @Environment(\.appContainer) private var container
    private var service: FavoritesServiceImpl { container.favoritesService }

    @State private var sortOption: FavoritesSortOption = .dateAdded
    @State private var sortDirection: FavoritesSortDirection = .descending
    @State private var displayMode: FavoritesDisplayMode = .list
    @State private var activeDecadeFilter: Int?
    @State private var activeSeasonFilter: Season?

    // Import / Export state
    @State private var showingFilePicker = false
    @State private var importResult: BackupImportResult?
    @State private var importError: String?
    @State private var showingImportAlert = false
    @State private var exportData: Data?
    @State private var showingExportShare = false

    // QR Code sheet state
    @State private var qrCodeShow: FavoriteShow?

    // Review sheet state
    @State private var reviewTargetShow: FavoriteShow?

    private var filteredShows: [FavoriteShow] {
        service.shows.filter { favoriteShow in
            guard let decade = activeDecadeFilter else { return true }
            let decadeRange = (decade...decade + 9)
            guard decadeRange.contains(favoriteShow.show.year) else { return false }
            if let season = activeSeasonFilter {
                let month = showMonth(favoriteShow)
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
                    "No Favorite Shows",
                    systemImage: "heart",
                    description: Text("Import your favorites from the old app or browse shows to add them.")
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
            if let mode = FavoritesDisplayMode(rawValue: container.appPreferences.favoritesDisplayMode) {
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
                    Text("Favorites")
                        .font(.title3)
                        .fontWeight(.bold)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    NavigationLink(value: FavoritesRoute.downloads) {
                        Label("Downloads", systemImage: "arrow.down.circle")
                    }
                    Button {
                        showingFilePicker = true
                    } label: {
                        Label("Import Favorites", systemImage: "square.and.arrow.down")
                    }
                    Button {
                        exportData = try? container.favoritesImportExportService.exportFavorites()
                        if exportData != nil { showingExportShare = true }
                    } label: {
                        Label("Export Favorites", systemImage: "square.and.arrow.up")
                    }
                    .disabled(service.shows.isEmpty)
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .fileImporter(
            isPresented: $showingFilePicker,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else {
                    importError = "Could not access file."
                    showingImportAlert = true
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                guard let data = try? Data(contentsOf: url) else {
                    importError = "Could not read file."
                    showingImportAlert = true
                    return
                }
                do {
                    importResult = try container.favoritesImportExportService.importFavorites(from: data)
                    importError = nil
                    service.refresh(sortedBy: sortOption, direction: sortDirection)
                } catch {
                    importError = error.localizedDescription
                    importResult = nil
                }
                showingImportAlert = true
            case .failure(let error):
                importError = error.localizedDescription
                showingImportAlert = true
            }
        }
        .alert("Favorites Import", isPresented: $showingImportAlert) {
            Button("OK") {}
        } message: {
            if let result = importResult {
                Text("Imported \(result.favoritesImported) favorites, \(result.reviewsImported) reviews, \(result.preferencesImported) prefs.\n\(result.favoritesSkipped) already favorited.\n\(result.notFound) not found.")
            } else {
                Text(importError ?? "Unknown error.")
            }
        }
        .sheet(isPresented: $showingExportShare) {
            if let data = exportData {
                FavoritesExportShareSheet(data: data, filename: container.favoritesImportExportService.exportFilename())
            }
        }
        .sheet(item: $qrCodeShow) { favoriteShow in
            let show = favoriteShow.show
            QRShareSheet(
                showId: show.id,
                recordingId: show.bestRecordingId,
                showDate: DateFormatting.formatShowDate(show.date),
                venue: show.venue.name,
                location: show.venue.displayLocation,
                coverImageUrl: show.coverImageUrl,
                trackNumber: nil,
                songTitle: nil
            )
        }
        .sheet(item: $reviewTargetShow) { favoriteShow in
            let show = favoriteShow.show
            let freshReview = (try? container.reviewService.getShowReview(show.id)) ?? ShowReview(showId: show.id)
            ShowReviewSheet(
                showDate: DateFormatting.formatShowDate(show.date),
                venue: show.venue.name,
                location: show.location.displayText,
                review: freshReview,
                lineupMembers: show.lineup?.members.map(\.name) ?? []
            ) { notes, rating, recQuality, playQuality, standouts in
                let showId = show.id
                try? container.reviewService.updateShowNotes(showId, notes: notes)
                try? container.reviewService.updateShowRating(showId, rating: rating)
                try? container.reviewService.updateRecordingQuality(showId, quality: recQuality)
                try? container.reviewService.updatePlayingQuality(showId, quality: playQuality)

                let existingTags = (try? container.reviewService.getPlayerTags(showId)) ?? []
                let existingNames = Set(existingTags.map(\.playerName))
                let newNames = Set(standouts)
                for name in existingNames.subtracting(newNames) {
                    try? container.reviewService.removePlayerTag(showId: showId, playerName: name)
                }
                for name in newNames.subtracting(existingNames) {
                    try? container.reviewService.upsertPlayerTag(showId: showId, playerName: name)
                }

                service.refresh(sortedBy: sortOption, direction: sortDirection)
            } onDelete: {
                let showId = show.id
                try? container.reviewService.deleteShowReview(showId)
                service.refresh(sortedBy: sortOption, direction: sortDirection)
            }
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
                ForEach(FavoritesSortOption.allCases, id: \.self) { option in
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
                let newMode: FavoritesDisplayMode = displayMode == .list ? .grid : .list
                displayMode = newMode
                container.appPreferences.favoritesDisplayMode = newMode.rawValue
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
            ForEach(filteredShows) { favoriteShow in
                ShowRowView(favoriteShow: favoriteShow)
                    .contextMenu { contextMenu(for: favoriteShow) }
            }
            .onDelete { indexSet in
                for index in indexSet {
                    let favoriteShow = filteredShows[index]
                    try? service.removeFromFavorites(showId: favoriteShow.show.id)
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
                    ForEach(filteredShows) { favoriteShow in
                        NavigationLink(value: favoriteShow.show.id) {
                            gridCard(favoriteShow, size: cardSize)
                        }
                        .buttonStyle(.plain)
                        .contextMenu { contextMenu(for: favoriteShow) }
                    }
                }
                .padding(DeadlySpacing.screenPadding)
            }
        }
    }

    private func gridCard(_ favoriteShow: FavoriteShow, size: CGFloat) -> some View {
        let show = favoriteShow.show
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
                    if favoriteShow.isPinned {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(DeadlyColors.primary)
                    }
                    if downloadStatus == .completed {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(DeadlyColors.primary)
                    }
                    if favoriteShow.hasNotes {
                        Image(systemName: "note.text")
                            .font(.system(size: 8))
                            .foregroundStyle(.tertiary)
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

    private func sortOptionLabel(_ option: FavoritesSortOption) -> String {
        switch option {
        case .dateAdded:  return "Date Added"
        case .dateOfShow: return "Show Date"
        case .venue:      return "Venue"
        case .rating:     return "Rating"
        }
    }

    private func showMonth(_ favoriteShow: FavoriteShow) -> Int {
        let parts = favoriteShow.show.date.split(separator: "-")
        guard parts.count >= 2, let m = Int(parts[1]) else { return 0 }
        return m
    }

    // MARK: - Context menu

    @ViewBuilder
    private func contextMenu(for favoriteShow: FavoriteShow) -> some View {
        let showId = favoriteShow.show.id
        let show = favoriteShow.show

        // Review
        Button {
            reviewTargetShow = favoriteShow
        } label: {
            Label(
                favoriteShow.hasReview ? "Edit Review" : "Add Review",
                systemImage: favoriteShow.hasReview ? "star.fill" : "star"
            )
        }

        // Share
        Button {
            qrCodeShow = favoriteShow
        } label: {
            Label("Share", systemImage: "square.and.arrow.up")
        }

        Divider()

        // Pin / Unpin
        Button {
            try? service.togglePin(showId: showId)
            service.refresh(sortedBy: sortOption, direction: sortDirection)
        } label: {
            Label(
                favoriteShow.isPinned ? "Unpin" : "Pin to Top",
                systemImage: favoriteShow.isPinned ? "pin.slash" : "pin"
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

        // Remove from Favorites
        Button(role: .destructive) {
            try? service.removeFromFavorites(showId: showId)
            service.refresh(sortedBy: sortOption, direction: sortDirection)
        } label: {
            Label("Remove from Favorites", systemImage: "heart.slash")
        }
    }


}
