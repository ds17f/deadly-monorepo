import SwiftUI

struct RecordingPicker: View {
    let show: Show
    let playlistService: PlaylistServiceImpl

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appContainer) private var container
    @State private var recordings: [Recording] = []

    var body: some View {
        NavigationStack {
            List(recordings) { recording in
                Button {
                    Task {
                        await playlistService.setRecordingAsDefault(recording)
                    }
                    dismiss()
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(recording.displayTitle)
                                .font(.subheadline)
                                .foregroundStyle(.primary)

                            if let taper = recording.taper {
                                Text("Taper: \(taper)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            if let source = recording.source {
                                Text(source)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }
                        }

                        Spacer()

                        if recording.identifier == playlistService.currentRecording?.identifier {
                            Image(systemName: "checkmark")
                                .foregroundStyle(DeadlyColors.primary)
                                .padding(.top, 2)
                        }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Choose Recording")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .task {
            recordings = (try? container.showRepository.getRecordingsForShow(show.id)) ?? []
        }
    }
}
