import SwiftUI

struct RecentShowCard: View {
    let show: Show

    var body: some View {
        HStack(spacing: 6) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: DeadlySize.recentArtwork,
                cornerRadius: DeadlySize.artworkCornerRadius
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(show.date)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .lineLimit(1)
                Text(show.location.displayText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if show.recordingCount == 0 {
                    Text("No recordings")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(height: DeadlySize.recentCardHeight)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(4)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius))
        .opacity(show.recordingCount == 0 ? 0.5 : 1.0)
    }
}
