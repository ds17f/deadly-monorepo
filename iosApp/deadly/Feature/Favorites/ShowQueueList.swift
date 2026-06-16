import SwiftUI

/// Show Queue — the backlog list (ADR-0010 Amendment). Embedded as the third
/// Favorites tab (single home — no standalone screen). Tap a row for the action
/// menu (Play Now · Move to Top · Go to Show · Remove); Reorder to drag, swipe
/// to remove (confirmed), Clear (confirmed). Play Now consumes the show and
/// flips Autoplay to Show Queue mode. Sync (slice 4) ships the add/pop/move event.
struct ShowQueueList: View {
    @Environment(\.appContainer) private var container

    @State private var shows: [Show] = []
    @State private var editMode: EditMode = .inactive
    @State private var selectedShow: Show?
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
        .sheet(item: $selectedShow) { show in
            QueueRowActionsSheet(
                show: show,
                onPlayNow: { selectedShow = nil; playNow(show) },
                onMoveToTop: { selectedShow = nil; moveToTop(show) },
                onGoToShow: { selectedShow = nil; goToShow(show) },
                onRemove: { selectedShow = nil; pendingRemove = show },
                onDone: { selectedShow = nil }
            )
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
                row(show)
                    .contentShape(Rectangle())
                    .onTapGesture { selectedShow = show }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { pendingRemove = show } label: {
                            Label("Remove", systemImage: "trash")
                        }
                    }
                    .deleteDisabled(true)
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
            Image(systemName: "square.stack")
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

    /// Play this show now, consume it from the queue, and switch Autoplay to
    /// Show Queue mode (playing *from* the queue means you want it to keep
    /// feeding). Toast only when the mode actually flips, to avoid noise.
    private func playNow(_ show: Show) {
        if container.appPreferences.advanceMode != .showQueue {
            container.appPreferences.advanceMode = .showQueue
            container.toastPresenter.show(advanceModeToastMessage(.showQueue))
        }
        container.backlogService.remove(show.id)
        Task { await container.playlistService.playShow(show) }
    }

    /// Reorder so this show is the head — the "play out of order" answer that
    /// keeps the queue's intent intact (vs. silently consuming a mid-list show).
    private func moveToTop(_ show: Show) {
        let reordered = [show.id] + shows.filter { $0.id != show.id }.map { $0.id }
        container.backlogService.reorder(reordered)
    }

    private func goToShow(_ show: Show) {
        container.requestShowDetail(show.id)
    }

    private func move(from source: IndexSet, to destination: Int) {
        shows.move(fromOffsets: source, toOffset: destination)
        container.backlogService.reorder(shows.map { $0.id })
    }
}

/// Action menu for a tapped queue row. Mirrors `ShowActionsMenuSheet`'s style —
/// NavigationStack + List of Label rows, primary tint, medium detent — so the
/// queue's pop-up matches every other menu in the app.
private struct QueueRowActionsSheet: View {
    let show: Show
    let onPlayNow: () -> Void
    let onMoveToTop: () -> Void
    let onGoToShow: () -> Void
    let onRemove: () -> Void
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    row("Play Now", systemImage: "play.circle", action: onPlayNow)
                    row("Move to Top", systemImage: "arrow.up.to.line", action: onMoveToTop)
                    row("Go to Show", systemImage: "info.circle", action: onGoToShow)
                }
                Section {
                    Button(role: .destructive, action: onRemove) {
                        Label("Remove from Queue", systemImage: "trash")
                    }
                }
            }
            .tint(.primary)
            .navigationTitle(DateFormatting.formatShowDate(show.date))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDone)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func row(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
        }
    }
}
