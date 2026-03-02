import SwiftUI

struct DownloadsScreen: View {
    @Environment(\.appContainer) private var container
    private var downloadService: DownloadServiceImpl { container.downloadService }

    @State private var showDeleteAllConfirmation = false
    @State private var showToDelete: Show? = nil

    private var activeDownloads: [(Show, ShowDownloadProgress)] {
        downloadService.allProgress.compactMap { showId, progress in
            guard progress.status == .downloading || progress.status == .queued else { return nil }
            guard let show = try? container.showRepository.getShowById(showId) else { return nil }
            return (show, progress)
        }.sorted { $0.0.date > $1.0.date }
    }

    private var pausedDownloads: [(Show, ShowDownloadProgress)] {
        downloadService.allProgress.compactMap { showId, progress in
            guard progress.status == .paused else { return nil }
            guard let show = try? container.showRepository.getShowById(showId) else { return nil }
            return (show, progress)
        }.sorted { $0.0.date > $1.0.date }
    }

    private var completedDownloads: [Show] {
        downloadService.allProgress.compactMap { showId, progress in
            guard progress.status == .completed else { return nil }
            return try? container.showRepository.getShowById(showId)
        }.sorted { $0.date > $1.date }
    }

    private var totalStorageUsed: Int64 {
        downloadService.totalStorageUsed()
    }

    private var hasAnyDownloads: Bool {
        !activeDownloads.isEmpty || !pausedDownloads.isEmpty || !completedDownloads.isEmpty
    }

    var body: some View {
        Group {
            if hasAnyDownloads {
                downloadsList
            } else {
                emptyState
            }
        }
        .navigationTitle("Downloads")
        .toolbar {
            if hasAnyDownloads {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) {
                        showDeleteAllConfirmation = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .alert("Remove All Downloads?", isPresented: $showDeleteAllConfirmation) {
            Button("Remove All", role: .destructive) {
                downloadService.removeAll()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete all downloaded shows and free up \(ByteCountFormatter.string(fromByteCount: totalStorageUsed, countStyle: .file)) of storage.")
        }
        .alert("Remove Download?", isPresented: Binding(
            get: { showToDelete != nil },
            set: { if !$0 { showToDelete = nil } }
        )) {
            Button("Remove", role: .destructive) {
                if let show = showToDelete {
                    downloadService.removeShow(show.id)
                    showToDelete = nil
                }
            }
            Button("Cancel", role: .cancel) { showToDelete = nil }
        } message: {
            if let show = showToDelete {
                Text("This will remove the show from \(DateFormatting.formatShowDate(show.date, style: .short)) and free up \(ByteCountFormatter.string(fromByteCount: downloadService.showStorageUsed(show.id), countStyle: .file)) of storage. This cannot be undone.")
            }
        }
    }

    private var downloadsList: some View {
        List {
            // Storage header
            Section {
                HStack {
                    Image(systemName: "internaldrive.fill")
                        .foregroundStyle(DeadlyColors.primary)
                    Text("Storage Used")
                    Spacer()
                    Text(ByteCountFormatter.string(fromByteCount: totalStorageUsed, countStyle: .file))
                        .foregroundStyle(.secondary)
                }
            }

            // Active downloads
            if !activeDownloads.isEmpty {
                Section("Downloading") {
                    ForEach(activeDownloads, id: \.0.id) { show, progress in
                        ActiveDownloadRow(
                            show: show,
                            progress: progress,
                            onPause: {
                                downloadService.pauseShow(show.id)
                            },
                            onCancel: {
                                downloadService.cancelShow(show.id)
                            }
                        )
                    }
                }
            }

            // Paused downloads
            if !pausedDownloads.isEmpty {
                Section("Paused") {
                    ForEach(pausedDownloads, id: \.0.id) { show, progress in
                        PausedDownloadRow(
                            show: show,
                            progress: progress,
                            onResume: {
                                downloadService.resumeShow(show.id)
                            },
                            onCancel: {
                                downloadService.cancelShow(show.id)
                            }
                        )
                    }
                }
            }

            // Completed downloads
            if !completedDownloads.isEmpty {
                Section("Downloaded") {
                    ForEach(completedDownloads) { show in
                        NavigationLink(value: show.id) {
                            CompletedDownloadRow(
                                show: show,
                                storageUsed: downloadService.showStorageUsed(show.id),
                                onRemove: {
                                    downloadService.removeShow(show.id)
                                }
                            )
                        }
                    }
                    .onDelete { indexSet in
                        if let index = indexSet.first {
                            showToDelete = completedDownloads[index]
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var emptyState: some View {
        ContentUnavailableView(
            "No Downloads",
            systemImage: "arrow.down.circle",
            description: Text("Downloaded shows will appear here. Tap the download button on any show to save it for offline playback.")
        )
    }
}
