import SwiftUI

/// Shows all thumbs-up tracks grouped by show.
struct FavoritesScreen: View {
    @Environment(\.appContainer) private var container
    @State private var tracks: [FavoriteTrack] = []
    @State private var isLoading = true

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if tracks.isEmpty {
                ContentUnavailableView(
                    "No Favorite Performances",
                    systemImage: "heart",
                    description: Text("Favorite tracks while listening to build your collection.")
                )
            } else {
                let grouped = Dictionary(grouping: tracks, by: \.showId)
                let sortedShowIds = grouped.keys.sorted { a, b in
                    let aDate = grouped[a]?.first?.showDate ?? ""
                    let bDate = grouped[b]?.first?.showDate ?? ""
                    return aDate > bDate
                }
                List {
                    ForEach(sortedShowIds, id: \.self) { showId in
                        let showTracks = grouped[showId] ?? []
                        if let first = showTracks.first {
                            Section {
                                ForEach(showTracks) { track in
                                    Text(track.trackTitle)
                                        .font(.body)
                                }
                            } header: {
                                NavigationLink(value: showId) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(DateFormatting.formatShowDate(first.showDate))
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                            .foregroundStyle(.primary)
                                        Text(first.venue)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("Favorite Performances")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            isLoading = true
            tracks = (try? container.reviewService.getThumbsUpTracks()) ?? []
            isLoading = false
        }
    }
}
