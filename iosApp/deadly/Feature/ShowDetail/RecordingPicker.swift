import SwiftUI

struct RecordingPicker: View {
    let show: Show
    let playlistService: PlaylistServiceImpl

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appContainer) private var container
    @State private var recordings: [Recording] = []
    @State private var pendingRecording: Recording? = nil
    @State private var showDownloadConflictAlert = false

    var body: some View {
        NavigationStack {
            List(recordings) { recording in
                Button {
                    Task {
                        guard recording.identifier != playlistService.currentRecording?.identifier else {
                            dismiss()
                            return
                        }

                        let status = container.downloadService.downloadStatus(for: show.id)
                        let hasActiveDownload: Bool
                        switch status {
                        case .notDownloaded, .cancelled, .failed:
                            hasActiveDownload = false
                        default:
                            hasActiveDownload = true
                        }

                        if hasActiveDownload,
                           let downloadedId = container.downloadService.downloadedRecordingId(for: show.id),
                           downloadedId != recording.identifier {
                            pendingRecording = recording
                            showDownloadConflictAlert = true
                        } else {
                            await playlistService.setRecordingAsDefault(recording)
                            dismiss()
                        }
                    }
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
        .alert("Switch Recording?", isPresented: $showDownloadConflictAlert) {
            Button("Cancel", role: .cancel) {
                pendingRecording = nil
            }
            Button("Switch Recording", role: .destructive) {
                guard let recording = pendingRecording else { return }
                Task {
                    container.downloadService.removeShow(show.id)
                    await playlistService.setRecordingAsDefault(recording)
                }
                pendingRecording = nil
                dismiss()
            }
        } message: {
            Text(conflictMessage)
        }
    }

    private var conflictMessage: String {
        switch container.downloadService.downloadStatus(for: show.id) {
        case .completed:
            return "This show is downloaded with a different recording. Switching will remove the download."
        case .paused:
            return "This show has a paused download for a different recording. Switching will remove it."
        default:
            return "This show is being downloaded with a different recording. Switching will cancel and remove it."
        }
    }
}
