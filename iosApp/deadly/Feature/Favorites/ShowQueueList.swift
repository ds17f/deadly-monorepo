import SwiftUI

/// Show Queue — the backlog list (ADR-0010 Amendment). Embedded as the third
/// Favorites tab (single home — no standalone screen). Tap to play, Reorder to
/// drag, swipe to remove (confirmed), Clear (confirmed). Local-only: no advance
/// wiring (slice 3) or sync (slice 4).
struct ShowQueueList: View {
    @Environment(\.appContainer) private var container

    @State private var shows: [Show] = []
    @State private var editMode: EditMode = .inactive
    @State private var pendingRemove: Show?
    @State private var showClearConfirm = false

    var body: some View {
        Group {
            if shows.isEmpty {
                emptyState
            } else {
                VStack(spacing: 0) {
                    header
                    list
                }
            }
        }
        .task {
            do {
                for try await records in container.backlogService.observe() {
                    shows = records.compactMap { try? container.showRepository.getShowById($0.showId) }
                }
            } catch {
                // Observation ended/cancelled — nothing to recover.
            }
        }
        .alert("Remove from queue?", isPresented: removeAlertBinding, presenting: pendingRemove) { show in
            Button("Remove", role: .destructive) { container.backlogService.remove(show.id) }
            Button("Cancel", role: .cancel) {}
        } message: { show in
            Text("\(DateFormatting.formatShowDate(show.date)) — \(show.venue.name)")
        }
        .alert("Clear show queue?", isPresented: $showClearConfirm) {
            Button("Clear", role: .destructive) { container.backlogService.clear() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes all \(shows.count) shows from your queue. Shows you're playing aren't affected.")
        }
    }

    private var header: some View {
        HStack {
            Text(shows.count == 1 ? "1 show" : "\(shows.count) shows")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
            Button(editMode.isEditing ? "Done" : "Reorder") {
                withAnimation { editMode = editMode.isEditing ? .inactive : .active }
            }
            .font(.callout)
            Button("Clear", role: .destructive) { showClearConfirm = true }
                .font(.callout)
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
        .padding(.vertical, 6)
    }

    private var list: some View {
        List {
            ForEach(shows) { show in
                Button { play(show) } label: { row(show) }
                    .buttonStyle(.plain)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { pendingRemove = show } label: {
                            Label("Remove", systemImage: "trash")
                        }
                    }
            }
            .onMove(perform: move)
        }
        .listStyle(.plain)
        .environment(\.editMode, $editMode)
    }

    private func row(_ show: Show) -> some View {
        HStack(spacing: 12) {
            ShowArtwork(
                recordingId: show.bestRecordingId,
                imageUrl: show.coverImageUrl,
                size: 52,
                cornerRadius: 8
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(DateFormatting.formatShowDate(show.date))
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                Text(show.venue.name)
                    .font(.subheadline)
                    .lineLimit(1)
                Text(show.location.displayText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
        }
        .contentShape(Rectangle())
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "list.bullet")
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text("Your show queue is empty")
                .font(.headline)
            Text("Long-press a show or use \"Add to Show Queue\" and it plays after the current one.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var removeAlertBinding: Binding<Bool> {
        Binding(get: { pendingRemove != nil }, set: { if !$0 { pendingRemove = nil } })
    }

    private func play(_ show: Show) {
        Task { await container.playlistService.playShow(show) }
    }

    private func move(from source: IndexSet, to destination: Int) {
        shows.move(fromOffsets: source, toOffset: destination)
        container.backlogService.reorder(shows.map { $0.id })
    }
}
