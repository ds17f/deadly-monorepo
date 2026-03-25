import SwiftUI

struct HomeScreen: View {
    @Environment(\.appContainer) private var container

    private var homeService: HomeServiceImpl { container.homeService }
    private var content: HomeContent { homeService.content }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                if !content.featuredArtists.isEmpty {
                    featuredArtistsSection
                }

                if !content.recentShows.isEmpty {
                    recentShowsSection
                }

                if !content.todayInHistory.isEmpty {
                    carouselSection("Today In History", shows: content.todayInHistory)
                }

                if !content.featuredCollections.isEmpty {
                    collectionsSection
                }

                if content.recentShows.isEmpty && content.todayInHistory.isEmpty && content.featuredCollections.isEmpty && content.featuredArtists.isEmpty && !homeService.isLoading {
                    emptyState
                }

                attribution
            }
            .padding(DeadlySpacing.screenPadding)
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { await homeService.refresh() }
    }

    // MARK: - Featured Artists

    private var featuredArtistsSection: some View {
        VStack(alignment: .leading, spacing: DeadlySpacing.itemSpacing) {
            Text("Live Music Archive")
                .font(.title2)
                .fontWeight(.bold)

            Text("Browse publicly available live recordings")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: DeadlySpacing.itemSpacing) {
                    ForEach(content.featuredArtists) { artist in
                        NavigationLink(value: ArtistRoute.detail(artist)) {
                            VStack(alignment: .leading, spacing: 8) {
                                AsyncImage(url: URL(string: artist.imageUrl)) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                    default:
                                        Rectangle()
                                            .fill(Color(.systemGray5))
                                            .overlay {
                                                Image(systemName: "music.mic")
                                                    .font(.title2)
                                                    .foregroundStyle(.secondary)
                                            }
                                    }
                                }
                                .frame(width: 120, height: 120)
                                .clipShape(RoundedRectangle(cornerRadius: DeadlySize.carouselCornerRadius))

                                Text(artist.name)
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .lineLimit(2)
                                    .frame(width: 120, alignment: .leading)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
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
            Text("Grateful Dead Collections")
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

    // MARK: - Attribution

    private var attribution: some View {
        HStack {
            Spacer()
            Text("Content provided by the [Internet Archive](https://archive.org)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .padding(.top, 8)
    }
}
