import SwiftUI

struct SetlistSheet: View {
    let show: Show

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let setlist = show.setlist, !setlist.sets.isEmpty {
                    setlistContent(setlist)
                } else {
                    emptyState
                }
            }
            .navigationTitle("Setlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Setlist Content

    private func setlistContent(_ setlist: Setlist) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // Header
                VStack(alignment: .leading, spacing: 4) {
                    Text(DateFormatting.formatShowDate(show.date))
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundStyle(DeadlyColors.primary)
                    Text(show.venue.name)
                        .font(.body)
                        .fontWeight(.medium)
                    Text(show.location.displayText)
                        .font(.body)
                        .foregroundStyle(.secondary)
                }

                // Sets
                ForEach(Array(setlist.sets.enumerated()), id: \.offset) { _, set in
                    setSection(set)
                }
            }
            .padding()
        }
    }

    // MARK: - Set Section

    private func setSection(_ set: SetlistSet) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            // Set header with underline accent
            VStack(alignment: .leading, spacing: 4) {
                Text(set.name.uppercased())
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(DeadlyColors.primary)
                    .tracking(1.2)
                Rectangle()
                    .fill(DeadlyColors.primary)
                    .frame(width: 60, height: 3)
            }

            // Songs
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(set.songs.enumerated()), id: \.offset) { _, song in
                    Text(song.displayName)
                        .font(.body)
                        .fontWeight(.medium)
                        .lineLimit(2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                }
            }
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "list.bullet.rectangle")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("No setlist available for this show")
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
