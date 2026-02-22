import SwiftUI

struct ShowRowView: View {
    let show: Show

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
                    Text(DateFormatting.formatShowDate(show.date, style: .short))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundStyle(.primary)

                    Text(show.venue.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    Text(show.location.displayText)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    if let addedAt = show.libraryAddedAt {
                        Text("Added \(relativeDate(from: addedAt))")
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
        }
    }

    private func relativeDate(from milliseconds: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(milliseconds) / 1000)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
