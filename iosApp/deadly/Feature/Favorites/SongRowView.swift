import SwiftUI

struct SongRowView: View {
    let track: FavoriteTrack

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "heart.fill")
                .font(.subheadline)
                .foregroundStyle(DeadlyColors.primary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text(track.trackTitle)
                    .font(.body)
                    .fontWeight(.medium)
                    .lineLimit(1)

                Text("\(track.showDate) • \(track.venue)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }
}
