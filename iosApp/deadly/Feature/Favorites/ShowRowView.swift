import SwiftUI

struct ShowRowView: View {
    @Environment(\.appContainer) private var container
    let favoriteShow: FavoriteShow

    init(favoriteShow: FavoriteShow) {
        self.favoriteShow = favoriteShow
    }

    init(show: Show) {
        self.favoriteShow = FavoriteShow(
            show: show,
            addedToFavoritesAt: show.favoritedAt ?? 0
        )
    }

    private var show: Show { favoriteShow.show }

    var body: some View {
        NavigationLink(value: show.id) {
            HStack(spacing: DeadlySpacing.itemSpacing) {
                ShowArtwork(
                    recordingId: show.bestRecordingId,
                    imageUrl: show.coverImageUrl,
                    size: DeadlySize.recentArtwork,
                    cornerRadius: DeadlySize.artworkCornerRadius
                )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        if favoriteShow.isPinned {
                            Image(systemName: "pin.fill")
                                .font(.caption2)
                                .foregroundStyle(DeadlyColors.primary)
                        }
                        if container.downloadService.downloadStatus(for: show.id) == .completed {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption2)
                            .foregroundStyle(DeadlyColors.primary)
                    }
                    if favoriteShow.hasReview {
                        Image(systemName: "star.fill")
                            .font(.caption2)
                            .foregroundStyle(DeadlyColors.secondary)
                    }
                        Text(DateFormatting.formatShowDate(show.date, style: .short))
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                    }

                    Text(show.venue.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    Text(show.location.displayText)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    if show.favoritedAt != nil {
                        Text("Added \(relativeDate(from: favoriteShow.addedToFavoritesAt))")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }

                Spacer()

                if show.hasRating {
                    Text(show.displayRating)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(DeadlyColors.secondary)
                }
            }
            .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        }
        .buttonStyle(.plain)
    }

    private func relativeDate(from milliseconds: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(milliseconds) / 1000)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
