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
                            Button { playFromQueue(item) } label: {
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
            .navigationTitle("Up Next")
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

    /// Play a queued show now. The playback layer pops it from the queue.
    private func playFromQueue(_ item: QueuedShowItem) {
        Task {
            await container.playlistService.loadShow(item.show.id)
            if let rid = item.recordingId,
               let rec = try? container.showRepository.getRecordingById(rid) {
                await container.playlistService.selectRecording(rec)
            }
            let idx = item.resumeTrackIndex ?? 0
            container.playlistService.playTrack(at: idx, source: "queue")
            container.playlistService.recordRecentPlay()
        }
    }
}
