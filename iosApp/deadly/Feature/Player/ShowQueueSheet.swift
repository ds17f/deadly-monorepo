import SwiftUI

/// "Up Next" — the persistent show queue (ADR-0010 §6), opened from the player.
/// Distinct from `PlayerQueueSheet`, which lists tracks within the current show.
/// Shows upcoming whole-shows with reorder, swipe-to-remove, and tap-to-play.
struct ShowQueueSheet: View {
    @Environment(\.appContainer) private var container
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            let items = container.playQueueService.items
            Group {
                if items.isEmpty {
                    ContentUnavailableView(
                        "Queue is Empty",
                        systemImage: "list.number",
                        description: Text("Add shows to the queue from a show's menu. They play in order, and each leaves the queue once it plays.")
                    )
                } else {
                    List {
                        ForEach(items) { item in
                            Button { openShow(item) } label: {
                                ShowRowView(show: item.show)
                            }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    container.playQueueService.remove(id: item.id)
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                        .onMove { from, to in
                            container.playQueueService.move(from: from, to: to)
                        }
                        .onDelete { indexSet in
                            for index in indexSet { container.playQueueService.remove(id: items[index].id) }
                        }
                    }
                }
            }
            .navigationTitle("Your Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                if !items.isEmpty {
                    ToolbarItem(placement: .primaryAction) { EditButton() }
                    ToolbarItem(placement: .destructiveAction) {
                        Button("Clear") { container.playQueueService.clear() }
                    }
                }
            }
        }
    }

    /// Open a queued show in the player *without* auto-playing — the user
    /// presses play, which pops it from the queue (if it's the head).
    private func openShow(_ item: QueuedShowItem) {
        dismiss()
        Task {
            await container.playlistService.loadShow(item.show.id)
            if let rid = item.recordingId,
               let rec = try? container.showRepository.getRecordingById(rid) {
                await container.playlistService.selectRecording(rec)
            }
            let idx = item.resumeTrackIndex ?? 0
            container.playlistService.playTrack(at: idx, source: "queue", autoPlay: false)
        }
    }
}
