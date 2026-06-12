import SwiftUI

/// Lists the collections a show belongs to (ADR-0014). Reached from the unified
/// "⋯" menu's "Collections" item, which only appears when the show is in ≥1
/// collection. Self-contained NavigationStack so a tap can push into the
/// existing CollectionDetailScreen.
struct ShowCollectionsSheet: View {
    let collections: [CollectionListItem]
    @Binding var isPresented: Bool

    var body: some View {
        NavigationStack {
            List(collections) { collection in
                NavigationLink {
                    CollectionDetailScreen(collectionId: collection.id)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(collection.name)
                            .font(.body)
                        Text(collection.showCountText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("In Collections")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { isPresented = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
