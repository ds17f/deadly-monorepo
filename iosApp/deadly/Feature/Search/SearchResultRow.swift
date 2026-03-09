import SwiftUI

struct SearchResultRow: View {
    let result: SearchResultShow
    @Environment(\.appContainer) private var container
    @State private var isFavorite = false
    @State private var showReviews = false

    var body: some View {
        NavigationLink(value: result.show.id) {
            HStack(spacing: DeadlySpacing.itemSpacing) {
                ShowArtwork(
                    recordingId: result.show.bestRecordingId,
                    imageUrl: result.show.coverImageUrl,
                    size: DeadlySize.recentArtwork,
                    cornerRadius: DeadlySize.artworkCornerRadius
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text(DateFormatting.formatShowDate(result.show.date, style: .short))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundStyle(.primary)

                    Text(result.show.venue.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    Text(result.show.location.displayText)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()

                if result.show.hasRating {
                    Text(result.show.displayRating)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(DeadlyColors.secondary)
                }
            }
            .padding(.vertical, 2)
        }
        .contextMenu { favoriteContextMenu }
        .sheet(isPresented: $showReviews) {
            SearchReviewsSheet(show: result.show, archiveClient: container.archiveClient)
        }
        .task { isFavorite = (try? container.favoritesService.isFavorite(showId: result.show.id)) ?? false }
    }

    @ViewBuilder
    private var favoriteContextMenu: some View {
        Button {
            Task {
                if isFavorite {
                    try? container.favoritesService.removeFromFavorites(showId: result.show.id)
                } else {
                    try? container.favoritesService.addToFavorites(showId: result.show.id)
                }
                isFavorite.toggle()
            }
        } label: {
            Label(
                isFavorite ? "Remove from Favorites" : "Add to Favorites",
                systemImage: isFavorite ? "heart.slash" : "heart"
            )
        }

        if result.show.bestRecordingId != nil {
            Button {
                showReviews = true
            } label: {
                Label("See Reviews", systemImage: "star.bubble")
            }
        }
    }
}
