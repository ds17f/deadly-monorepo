import SwiftUI

struct HomeScreen: View {
    @Environment(\.appContainer) private var container

    private var homeService: HomeServiceImpl { container.homeService }
    private var trendingService: TrendingServiceImpl { container.trendingService }
    private var appPreferences: AppPreferences { container.appPreferences }
    private var content: HomeContent { homeService.content }
    private var trendingWindow: TrendingWindow {
        TrendingWindow(preferenceKey: appPreferences.homeTrendingWindow)
    }
    private var trendingShows: [Show] {
        trendingService.content.shows(for: trendingWindow)
    }
    private var trendingCardSize: CarouselCardSize {
        CarouselCardSize(preferenceKey: appPreferences.homeTrendingCardSize)
    }
    private var todayCardSize: CarouselCardSize {
        CarouselCardSize(preferenceKey: appPreferences.homeTodayCardSize)
    }
    private var collectionsCardSize: CarouselCardSize {
        CarouselCardSize(preferenceKey: appPreferences.homeCollectionsCardSize)
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                if !content.recentShows.isEmpty {
                    recentShowsSection
                }

                // Trending + Today in History — order honors the
                // "Show trending above Today" preference.
                if appPreferences.homeTrendingAboveToday {
                    if !trendingShows.isEmpty { trendingSection }
                    if !content.todayInHistory.isEmpty {
                        carouselSection("Today in Grateful Dead History", shows: content.todayInHistory, size: todayCardSize.width)
                    }
                } else {
                    if !content.todayInHistory.isEmpty {
                        carouselSection("Today in Grateful Dead History", shows: content.todayInHistory, size: todayCardSize.width)
                    }
                    if !trendingShows.isEmpty { trendingSection }
                }

                if !content.featuredCollections.isEmpty {
                    collectionsSection
                }

                if content.recentShows.isEmpty && content.todayInHistory.isEmpty && content.featuredCollections.isEmpty && !homeService.isLoading {
                    emptyState
                }
            }
            .padding(DeadlySpacing.screenPadding)
        }
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await homeService.refresh()
            await trendingService.refresh()
        }
    }

    // MARK: - Trending

    private var trendingSection: some View {
        VStack(alignment: .leading, spacing: DeadlySpacing.itemSpacing) {
            let cycle = {
                let next = trendingWindow.next
                appPreferences.homeTrendingWindow = next.rawValue
                container.analyticsService.track("feature_use", props: [
                    "feature": "set_home_trending_window",
                    "category": "preference",
                    "value": next.rawValue,
                ])
            }
            HStack(spacing: 12) {
                Button(action: cycle) {
                    Text("Trending \(trendingWindow.label)")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Trending \(trendingWindow.label). Tap to change window.")

                Spacer(minLength: 8)

                Button(action: cycle) {
                    Text("Show \(trendingWindow.next.label)")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                .accessibilityHidden(true)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: DeadlySpacing.itemSpacing) {
                    ForEach(trendingShows) { show in
                        NavigationLink(value: show.id) {
                            ShowCarouselCard(
                                imageRecordingId: show.bestRecordingId,
                                imageUrl: show.coverImageUrl,
                                lines: [show.date],
                                recordingCount: show.recordingCount,
                                size: trendingCardSize.width
                            )
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            NavigationLink(value: show.id) {
                                Label("View Show", systemImage: "eye")
                            }
                        } preview: {
                            ShowDetailPopover(
                                date: show.date,
                                venue: show.venue.name,
                                location: show.location.displayText,
                                rating: show.hasRating ? show.displayRating : nil
                            )
                        }
                    }
                }
            }
        }
    }

    // MARK: - Sections

    private var recentShowsSection: some View {
        // Row count is a persisted preference (1..4). Each row = 2 shows.
        let limit = max(1, min(4, appPreferences.homeRecentRows)) * 2
        let shows = Array(content.recentShows.prefix(limit))

        return VStack(alignment: .leading, spacing: DeadlySpacing.itemSpacing) {
            Text("Recently Played")
                .font(.title2)
                .fontWeight(.bold)

            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: DeadlySpacing.gridSpacing),
                    GridItem(.flexible(), spacing: DeadlySpacing.gridSpacing)
                ],
                spacing: DeadlySpacing.gridVerticalSpacing
            ) {
                ForEach(shows) { show in
                    NavigationLink(value: show.id) {
                        RecentShowCard(show: show)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        NavigationLink(value: show.id) {
                            Label("View Show", systemImage: "eye")
                        }
                    } preview: {
                        ShowDetailPopover(
                            date: show.date,
                            venue: show.venue.name,
                            location: show.location.displayText,
                            rating: show.hasRating ? show.displayRating : nil
                        )
                    }
                }
            }
        }
    }

    private func carouselSection(_ title: String, shows: [Show], size: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: DeadlySpacing.itemSpacing) {
            Text(title)
                .font(.title2)
                .fontWeight(.bold)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: DeadlySpacing.itemSpacing) {
                    ForEach(shows) { show in
                        NavigationLink(value: show.id) {
                            ShowCarouselCard(
                                imageRecordingId: show.bestRecordingId,
                                imageUrl: show.coverImageUrl,
                                lines: [show.date, show.venue.name, show.location.displayText],
                                recordingCount: show.recordingCount,
                                size: size
                            )
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            NavigationLink(value: show.id) {
                                Label("View Show", systemImage: "eye")
                            }
                        } preview: {
                            ShowDetailPopover(
                                date: show.date,
                                venue: show.venue.name,
                                location: show.location.displayText,
                                rating: show.hasRating ? show.displayRating : nil
                            )
                        }
                    }
                }
            }
        }
    }

    private var collectionsSection: some View {
        VStack(alignment: .leading, spacing: DeadlySpacing.itemSpacing) {
            Text("Featured Collections")
                .font(.title2)
                .fontWeight(.bold)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: DeadlySpacing.itemSpacing) {
                    ForEach(content.featuredCollections) { collection in
                        NavigationLink(value: CollectionRoute.detail(collection.id)) {
                            ShowCarouselCard(
                                imageRecordingId: nil,
                                imageUrl: nil,
                                lines: [collection.formattedName, collection.showCountText],
                                size: collectionsCardSize.width
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView(
            "No Data",
            systemImage: "music.note.list",
            description: Text("Import show data from Settings to get started.")
        )
        .frame(maxWidth: .infinity)
    }
}
