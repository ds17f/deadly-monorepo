import SwiftUI

/// Reusable "Add to Queue" action for any show context menu (ADR-0010 §6).
/// Appends the show to the bottom of the persistent play queue.
struct AddToQueueButton: View {
    let showId: String
    @Environment(\.appContainer) private var container

    var body: some View {
        Button {
            container.playQueueService.enqueue(showId: showId)
        } label: {
            Label("Add to Queue", systemImage: "text.append")
        }
    }
}
