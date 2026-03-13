import SwiftUI

struct HomeScreen: View {
    @Environment(\.appContainer) private var container

    private var homeService: HomeServiceImpl { container.homeService }
    private var content: HomeContent { homeService.content }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                if !content.recentShows.isEmpty {
                    recentShowsSection
                }

                if !content.todayInHistory.isEmpty {
                    carouselSection("Today In Grateful Dead History", shows: content.todayInHistory)
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
        .task { await homeService.refresh() }
    }

    // MARK: - Sections

    private var recentShowsSection: some View {
        VStack(alignment: .leading, spacing: DeadlySpacing.itemSpacing) {
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
                ForEach(content.recentShows) { show in
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

    private func carouselSection(_ title: String, shows: [Show]) -> some View {
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
                                recordingCount: show.recordingCount
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
                                lines: [collection.formattedName, collection.showCountText]
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
