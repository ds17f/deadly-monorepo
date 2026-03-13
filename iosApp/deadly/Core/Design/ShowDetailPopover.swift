import SwiftUI

/// Preview view shown during long-press context menu on show cards.
/// Displays full date, venue, location, and rating in large readable text.
struct ShowDetailPopover: View {
    let date: String
    let venue: String
    let location: String
    let rating: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(DateFormatting.formatShowDate(date))
                .font(.title3)
                .fontWeight(.semibold)

            Text(venue)
                .font(.body)
                .foregroundStyle(.secondary)

            Text(location)
                .font(.body)
                .foregroundStyle(.secondary)

            if let rating {
                Text(rating)
                    .font(.body)
                    .fontWeight(.medium)
                    .foregroundStyle(DeadlyColors.secondary)
            }
        }
        .padding()
        .frame(minWidth: 200, alignment: .leading)
    }
}
