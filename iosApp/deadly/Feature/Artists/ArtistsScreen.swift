import SwiftUI

struct ArtistsScreen: View {
    @Environment(\.appContainer) private var container

    private var artists: [Artist] { container.appPreferences.enabledBrowsableArtists }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: DeadlySpacing.sectionSpacing) {
                header

                artistGrid

                attribution
            }
            .padding(DeadlySpacing.screenPadding)
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Live Music Archive")
                .font(.title2)
                .fontWeight(.bold)

            Text("Browse publicly available live recordings from the Internet Archive.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Artist Grid

    private var artistGrid: some View {
        LazyVGrid(
            columns: [
                GridItem(.flexible(), spacing: DeadlySpacing.gridSpacing),
                GridItem(.flexible(), spacing: DeadlySpacing.gridSpacing),
            ],
            spacing: DeadlySpacing.sectionSpacing
        ) {
            ForEach(artists) { artist in
                NavigationLink(value: ArtistRoute.detail(artist)) {
                    ArtistCard(artist: artist)
                }
                .buttonStyle(.plain)
            }
        }
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
        .padding(.top, DeadlySpacing.sectionSpacing)
    }
}

// MARK: - Artist Card

private struct ArtistCard: View {
    let artist: Artist

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AsyncImage(url: URL(string: artist.imageUrl)) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                case .failure:
                    placeholderImage
                default:
                    placeholderImage
                        .overlay { ProgressView() }
                }
            }
            .frame(width: DeadlySize.carouselCard, height: DeadlySize.carouselCard)
            .clipShape(RoundedRectangle(cornerRadius: DeadlySize.carouselCornerRadius))

            Text(artist.name)
                .font(.subheadline)
                .fontWeight(.medium)
                .lineLimit(2)
        }
        .frame(width: DeadlySize.carouselCard)
        .accessibilityElement(children: .combine)
    }

    private var placeholderImage: some View {
        Rectangle()
            .fill(Color(.systemGray5))
            .frame(width: DeadlySize.carouselCard, height: DeadlySize.carouselCard)
            .overlay {
                Image(systemName: "music.mic")
                    .font(.largeTitle)
                    .foregroundStyle(.secondary)
            }
    }
}

// MARK: - Route

enum ArtistRoute: Hashable {
    case detail(Artist)
}
