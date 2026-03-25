import SwiftUI

struct ArtistShowRow: View {
    let show: ArchiveShow

    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: "https://archive.org/services/img/\(show.identifier)")) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                default:
                    Rectangle()
                        .fill(Color(.systemGray5))
                        .overlay {
                            Image(systemName: "music.note")
                                .foregroundStyle(.secondary)
                        }
                }
            }
            .frame(width: 48, height: 48)
            .clipShape(RoundedRectangle(cornerRadius: DeadlySize.artworkCornerRadius))

            VStack(alignment: .leading, spacing: 2) {
                Text(show.displayDate)
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(show.displayVenue)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                if !show.displayLocation.isEmpty {
                    Text(show.displayLocation)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                }
            }

            Spacer()

            if let rating = show.displayRating {
                Text(rating)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
