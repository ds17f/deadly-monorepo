import SwiftUI

struct SearchResultRow: View {
    let result: SearchResultShow

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
    }
}
