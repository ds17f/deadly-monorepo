import SwiftUI

struct CollectionDetailScreen: View {
    let collectionId: String

    @Environment(\.appContainer) private var container
    private var service: CollectionsServiceImpl { container.collectionsService }

    var body: some View {
        Group {
            if service.isLoadingDetail {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let collection = service.selectedCollection {
                collectionContent(collection)
            } else {
                ContentUnavailableView(
                    "Collection Not Found",
                    systemImage: "square.stack"
                )
            }
        }
        .navigationTitle(service.selectedCollection?.name ?? "Collection")
        .navigationBarTitleDisplayMode(.large)
        .task { service.loadCollection(id: collectionId) }
    }

    private func collectionContent(_ collection: DeadCollection) -> some View {
        List {
            // Header
            if !collection.description.isEmpty || !collection.tags.isEmpty {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        if !collection.description.isEmpty {
                            Text(collection.description)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }

                        HStack(spacing: 8) {
                            Text(collection.showCountText)
                                .font(.caption)
                                .foregroundStyle(.tertiary)

                            ForEach(collection.tags, id: \.self) { tag in
                                Text(tag.capitalized)
                                    .font(.caption2)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(DeadlyColors.primary.opacity(0.15))
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
            }

            // Shows
            Section("Shows") {
                if collection.shows.isEmpty {
                    Text("No shows in this collection")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(collection.shows) { show in
                        ShowRowView(show: show)
                    }
                }
            }
        }
        .listStyle(.plain)
    }
}
