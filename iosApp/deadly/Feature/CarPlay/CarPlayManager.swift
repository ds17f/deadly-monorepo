import CarPlay
import os
import SwiftAudioStreamEx

private let logger = Logger(subsystem: "com.grateful.deadly", category: "CarPlay")

/// Archive.org auto-generates waveform spectrograms (180x45, 4:1 aspect) for audio
/// items that lack real artwork. Detect by pixel dimensions and show the logo instead.
private func isWaveform(_ image: UIImage) -> Bool {
    let w = image.size.width * image.scale
    let h = image.size.height * image.scale
    guard h > 0 else { return false }
    return h <= 50 || w / h > 3
}

/// Builds the CPTemplate hierarchy and handles all CarPlay user interactions.
@MainActor
final class CarPlayManager {
    private let interfaceController: CPInterfaceController
    private let container: AppContainer
    private let trackResolver: CarPlayTrackResolver
    private let placeholderImage: UIImage?

    private static let deadFirstYear = 1965
    private static let deadLastYear = 1995
    /// CarPlay list item artwork size (44pt standard).
    private static let artworkSize = CGSize(width: 44, height: 44)

    init(interfaceController: CPInterfaceController, container: AppContainer) {
        self.interfaceController = interfaceController
        self.container = container
        self.trackResolver = CarPlayTrackResolver(
            showRepository: container.showRepository,
            archiveClient: container.archiveClient,
            recordingPreferenceDAO: RecordingPreferenceDAO(database: container.database),
            downloadService: container.downloadService
        )
        self.placeholderImage = UIImage(named: "deadly_logo_square")
    }

    func configure() {
        logger.info("CarPlay: configure() building tabs")
        let recentTab = buildRecentTab()
        let favoritesTab = buildFavoritesTab()
        let todayTab = buildTodayTab()
        let discoverTab = buildDiscoverTab()

        logger.info("CarPlay: setting root template with 4 tabs")
        let tabBar = CPTabBarTemplate(templates: [recentTab, favoritesTab, todayTab, discoverTab])
        interfaceController.setRootTemplate(tabBar, animated: true) { success, error in
            if let error {
                logger.error("CarPlay: setRootTemplate failed: \(error.localizedDescription)")
            } else {
                logger.info("CarPlay: setRootTemplate completed, success=\(success)")
            }
        }
    }

    // MARK: - Tab Builders

    private func buildRecentTab() -> CPListTemplate {
        let template = CPListTemplate(title: "Recent", sections: [])
        template.tabTitle = "Recent"
        template.tabImage = UIImage(systemName: "clock")
        loadRecentShows(into: template)
        return template
    }

    private func buildFavoritesTab() -> CPListTemplate {
        let template = CPListTemplate(title: "Favorites", sections: [])
        template.tabTitle = "Favorites"
        template.tabImage = UIImage(systemName: "heart.fill")
        loadFavorites(into: template)
        return template
    }

    private func buildTodayTab() -> CPListTemplate {
        let template = CPListTemplate(title: "TIGDH", sections: [])
        template.tabTitle = "TIGDH"
        template.tabImage = UIImage(systemName: "calendar")
        loadTodayShows(into: template)
        return template
    }

    private func buildDiscoverTab() -> CPListTemplate {
        let yearsItem = CPListItem(text: "Browse by Year", detailText: "1965–1995")
        yearsItem.handler = { [weak self] _, completion in
            self?.showYearsList()
            completion()
        }

        let topRatedItem = CPListItem(text: "Top Rated", detailText: "Highest rated shows")
        topRatedItem.handler = { [weak self] _, completion in
            self?.showTopRated()
            completion()
        }

        let template = CPListTemplate(title: "Discover", sections: [CPListSection(items: [yearsItem, topRatedItem])])
        template.tabTitle = "Discover"
        template.tabImage = UIImage(systemName: "magnifyingglass")
        return template
    }

    // MARK: - Data Loading

    private func loadRecentShows(into template: CPListTemplate) {
        Task {
            let shows = await container.recentShowsService.getRecentShows(limit: 20)
            let items = shows.map { buildShowItem($0) }
            template.updateSections([CPListSection(items: items.isEmpty ? [emptyItem("No recent shows")] : items)])
        }
    }

    private func loadFavorites(into template: CPListTemplate) {
        Task {
            // Refresh favorites data
            container.favoritesService.refresh()
            container.favoritesService.refreshSongs()

            let showItems = container.favoritesService.shows.prefix(20).map { fav in
                buildShowItem(fav.show)
            }
            let songItems = container.favoritesService.songs.prefix(20).map { song in
                buildFavoriteSongItem(song)
            }

            var sections: [CPListSection] = []
            sections.append(CPListSection(
                items: showItems.isEmpty ? [emptyItem("No favorite shows")] : showItems,
                header: "Shows",
                sectionIndexTitle: nil
            ))
            sections.append(CPListSection(
                items: songItems.isEmpty ? [emptyItem("No favorite songs")] : songItems,
                header: "Songs",
                sectionIndexTitle: nil
            ))
            template.updateSections(sections)
        }
    }

    private func loadTodayShows(into template: CPListTemplate) {
        Task {
            let calendar = Calendar.current
            let month = calendar.component(.month, from: Date())
            let day = calendar.component(.day, from: Date())
            let shows = (try? container.showRepository.getShowsForDate(month: month, day: day)) ?? []
            let items = shows.map { buildShowItem($0) }
            template.updateSections([CPListSection(items: items.isEmpty ? [emptyItem("No shows on this day")] : items)])
        }
    }

    // MARK: - Show Drill-Down

    private func showYearsList() {
        let items: [CPListItem] = (Self.deadFirstYear...Self.deadLastYear).map { year in
            let item = CPListItem(text: "\(year)", detailText: nil)
            item.handler = { [weak self] _, completion in
                self?.showYearShows(year)
                completion()
            }
            return item
        }
        let template = CPListTemplate(title: "Browse by Year", sections: [CPListSection(items: items)])
        interfaceController.pushTemplate(template, animated: true, completion: nil)
    }

    private func showTopRated() {
        let template = CPListTemplate(title: "Top Rated", sections: [CPListSection(items: [loadingItem()])])
        interfaceController.pushTemplate(template, animated: true, completion: nil)

        Task {
            let shows = (try? container.showRepository.getTopRatedShows(limit: 50)) ?? []
            let items = shows.map { buildShowItem($0) }
            template.updateSections([CPListSection(items: items.isEmpty ? [emptyItem("No shows found")] : items)])
        }
    }

    private func showYearShows(_ year: Int) {
        let template = CPListTemplate(title: "\(year)", sections: [CPListSection(items: [loadingItem()])])
        interfaceController.pushTemplate(template, animated: true, completion: nil)

        Task {
            let shows = (try? container.showRepository.getShowsByYear(year)) ?? []
            let items = shows.map { buildShowItem($0) }
            template.updateSections([CPListSection(items: items.isEmpty ? [emptyItem("No shows in \(year)")] : items)])
        }
    }

    private func showTracks(for showId: String) {
        let template = CPListTemplate(title: "Tracks", sections: [CPListSection(items: [loadingItem()])])
        interfaceController.pushTemplate(template, animated: true, completion: nil)

        Task {
            do {
                guard let resolved = try await trackResolver.resolve(showId: showId) else {
                    template.updateSections([CPListSection(items: [errorItem("Unable to load tracks") { [weak self] in
                        self?.showTracks(for: showId)
                    }])])
                    return
                }

                let items: [CPListItem] = resolved.trackItems.enumerated().map { index, track in
                    let item = CPListItem(text: track.title, detailText: track.duration.map(formatDuration) ?? "")
                    item.handler = { [weak self] _, completion in
                        self?.playTracks(resolved.trackItems, startingAt: index, showId: showId)
                        completion()
                    }
                    return item
                }
                template.updateSections([CPListSection(items: items.isEmpty ? [emptyItem("No tracks found")] : items)])
            } catch {
                template.updateSections([CPListSection(items: [errorItem("Unable to load. Tap to retry.") { [weak self] in
                    self?.showTracks(for: showId)
                }])])
            }
        }
    }

    // MARK: - Playback

    private func playTracks(_ tracks: [TrackItem], startingAt index: Int, showId: String) {
        container.streamPlayer.loadQueue(tracks, startingAt: index)
        container.recentShowsService.recordShowPlay(showId: showId)
    }

    private func playFavoriteSong(_ song: FavoriteTrack) {
        Task {
            do {
                let resolved: CarPlayTrackResolver.ResolvedShow?
                if let recordingId = song.recordingId {
                    resolved = try await trackResolver.resolve(showId: song.showId, recordingId: recordingId)
                } else {
                    resolved = try await trackResolver.resolve(showId: song.showId)
                }

                guard let resolved else {
                    showAlert("Unable to load this song")
                    return
                }

                // Find matching track by title and track number
                let matchedIndex = resolved.trackItems.firstIndex(where: { item in
                    if let trackNum = song.trackNumber,
                       let itemNum = item.metadata["trackNumber"],
                       let num = Int(itemNum), num == trackNum {
                        return true
                    }
                    return item.title.localizedCaseInsensitiveCompare(song.trackTitle) == .orderedSame
                }) ?? 0

                playTracks(resolved.trackItems, startingAt: matchedIndex, showId: song.showId)
            } catch {
                showAlert("Unable to load this song")
            }
        }
    }

    // MARK: - Item Builders

    private func buildShowItem(_ show: Show) -> CPListItem {
        let subtitle = [show.venue.name, show.location.displayText]
            .filter { !$0.isEmpty }
            .joined(separator: " \u{2022} ")
        let item = CPListItem(text: formatShowDate(show.date), detailText: subtitle, image: placeholderImage)
        if let sfSymbol = show.bestSourceType.sfSymbolName {
            let config = UIImage.SymbolConfiguration(pointSize: 20, weight: .semibold)
            item.setAccessoryImage(UIImage(systemName: sfSymbol, withConfiguration: config))
        }
        item.handler = { [weak self] _, completion in
            self?.showTracks(for: show.id)
            completion()
        }
        loadArtwork(for: show, into: item)
        return item
    }

    private func buildFavoriteSongItem(_ song: FavoriteTrack) -> CPListItem {
        let detail = "\(formatShowDate(song.showDate)) — \(song.venue)"
        let item = CPListItem(text: song.trackTitle, detailText: detail, image: placeholderImage)
        item.handler = { [weak self] _, completion in
            self?.playFavoriteSong(song)
            completion()
        }
        if let url = artworkUrl(coverImageUrl: nil, recordingId: song.recordingId) {
            loadArtwork(url: url, into: item)
        }
        return item
    }

    private func loadingItem() -> CPListItem {
        CPListItem(text: "Loading...", detailText: nil)
    }

    private func emptyItem(_ text: String) -> CPListItem {
        CPListItem(text: text, detailText: nil)
    }

    private func errorItem(_ text: String, retry: @escaping () -> Void) -> CPListItem {
        let item = CPListItem(text: text, detailText: "Tap to retry")
        item.handler = { _, completion in
            retry()
            completion()
        }
        return item
    }

    // MARK: - Artwork

    private func artworkUrl(coverImageUrl: String?, recordingId: String?) -> URL? {
        if let coverImageUrl, let url = URL(string: coverImageUrl) {
            return url
        }
        if let recordingId {
            return URL(string: "https://archive.org/services/img/\(recordingId)")
        }
        return nil
    }

    private func loadArtwork(for show: Show, into item: CPListItem) {
        guard let url = artworkUrl(coverImageUrl: show.coverImageUrl, recordingId: show.bestRecordingId) else { return }
        loadArtwork(url: url, into: item)
    }

    private func loadArtwork(url: URL, into item: CPListItem) {
        // Check memory cache synchronously to avoid flicker
        if let cached = ImageCache.shared.cachedImage(for: url), !isWaveform(cached) {
            item.setImage(cached)
            return
        }
        Task {
            guard let image = await ImageCache.shared.image(for: url), !isWaveform(image) else { return }
            item.setImage(image)
        }
    }

    // MARK: - Alerts

    private func showAlert(_ message: String) {
        let action = CPAlertAction(title: "OK", style: .default, handler: { _ in })
        let alert = CPAlertTemplate(titleVariants: [message], actions: [action])
        interfaceController.presentTemplate(alert, animated: true, completion: nil)
    }

    // MARK: - Formatting

    private func formatShowDate(_ dateString: String) -> String {
        let parts = dateString.split(separator: "-")
        guard parts.count == 3,
              let month = Int(parts[1]),
              let day = Int(parts[2]) else { return dateString }
        let monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
                          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
        guard month >= 1, month <= 12 else { return dateString }
        return "\(monthNames[month - 1]) \(day), \(parts[0])"
    }

    private func formatDuration(_ seconds: TimeInterval) -> String {
        let total = Int(seconds)
        let mins = total / 60
        let secs = total % 60
        return String(format: "%d:%02d", mins, secs)
    }
}
