import SwiftUI

/// Up Next — the backlog screen (ADR-0010 Amendment). Shows the local-first
/// play-next list (head first); tap to play, reorder (Edit), swipe to remove,
/// Clear. Local-only: no advance wiring (slice 3) or sync (slice 4).
struct UpNextScreen: View {
    let backlog: BacklogService
    let showRepository: any ShowRepository
    let onPlay: (Show) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var shows: [Show] = []

    var body: some View {
        NavigationStack {
            Group {
                if shows.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(shows) { show in
                            Button { onPlay(show) } label: { row(show) }
                                .buttonStyle(.plain)
                        }
                        .onDelete(perform: remove)
                        .onMove(perform: move)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Up Next")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                if !shows.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) { EditButton() }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Clear", role: .destructive) { backlog.clear() }
                    }
                }
            }
        }
        .task {
            do {
                for try await records in backlog.observe() {
                    shows = records.compactMap { try? showRepository.getShowById($0.showId) }
                }
            } catch {
                // Observation ended/cancelled — nothing to recover.
            }
        }
    }

    private func row(_ show: Show) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(show.venue.name)
                .font(.body.weight(.semibold))
                .lineLimit(1)
            Text("\(show.date) • \(show.location.displayText)")
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "list.bullet")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text("Nothing in Up Next")
                .font(.headline)
            Text("Add a show with \"Add to Up Next\" from the ⋯ menu and it plays after the current one.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
    }

    private func remove(at offsets: IndexSet) {
        for index in offsets { backlog.remove(shows[index].id) }
    }

    private func move(from source: IndexSet, to destination: Int) {
        shows.move(fromOffsets: source, toOffset: destination)
        backlog.reorder(shows.map { $0.id })
    }
}
