import SwiftUI

struct CollectionRowView: View {
    let collection: CollectionListItem

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(collection.name)
                .font(.headline)

            if !collection.description.isEmpty {
                Text(collection.description)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            HStack(spacing: 8) {
                Text(collection.showCountText)
                    .font(.caption)
                    .foregroundStyle(.tertiary)

                if let tag = collection.primaryTag {
                    Text(tag.capitalized)
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(DeadlyColors.primary.opacity(0.15))
                        .clipShape(Capsule())
                }
            }
        }
        .padding(.vertical, 4)
    }
}
