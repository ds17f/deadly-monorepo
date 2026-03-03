import SwiftUI

struct DeepLinkActionSheet: View {
    let deepLink: DeepLink
    let onPlayNow: () -> Void
    let onGoToShow: () -> Void
    let onAddToLibrary: () -> Void
    let onIgnore: () -> Void

    @State private var show: Show?
    @Environment(\.appContainer) private var container

    private var showId: String? {
        guard case .show(let id, _, _) = deepLink else { return nil }
        return id
    }

    private var recordingId: String? {
        guard case .show(_, let rid, _) = deepLink else { return nil }
        return rid
    }

    var body: some View {
        NavigationStack {
            List {
                // Show preview header
                Section {
                    if let show {
                        HStack(spacing: 14) {
                            ShowArtwork(
                                recordingId: recordingId ?? show.bestRecordingId,
                                imageUrl: show.coverImageUrl,
                                size: 64,
                                cornerRadius: DeadlySize.cardCornerRadius
                            )

                            VStack(alignment: .leading, spacing: 3) {
                                Text(DateFormatting.formatShowDate(show.date))
                                    .font(.headline)
                                    .fontWeight(.bold)
                                Text(show.venue.name)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                let loc = show.venue.displayLocation
                                if !loc.isEmpty {
                                    Text(loc)
                                        .font(.caption)
                                        .foregroundStyle(.tertiary)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    } else {
                        HStack(spacing: 12) {
                            RoundedRectangle(cornerRadius: DeadlySize.cardCornerRadius)
                                .fill(Color(.secondarySystemBackground))
                                .frame(width: 64, height: 64)
                            VStack(alignment: .leading, spacing: 6) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color(.secondarySystemBackground))
                                    .frame(width: 160, height: 14)
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color(.secondarySystemBackground))
                                    .frame(width: 120, height: 12)
                            }
                        }
                        .padding(.vertical, 4)
                        .redacted(reason: .placeholder)
                    }
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)

                // Actions
                Section {
                    Button(action: onPlayNow) {
                        Label("Play Now", systemImage: "play.fill")
                    }
                    Button(action: onGoToShow) {
                        Label("Go to Show", systemImage: "music.note.list")
                    }
                    Button(action: onAddToLibrary) {
                        Label("Add to Library", systemImage: "heart")
                    }
                    Button(role: .destructive, action: onIgnore) {
                        Label("Ignore", systemImage: "xmark")
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Open Link")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onIgnore)
                }
            }
        }
        .presentationDetents([.medium])
        .task {
            if let sid = showId {
                show = try? container.showRepository.getShowById(sid)
            }
        }
    }
}
