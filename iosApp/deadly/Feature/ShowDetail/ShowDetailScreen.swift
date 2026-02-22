import SwiftUI
import SwiftAudioStreamEx

struct ShowDetailScreen: View {
    let showId: String

    @Environment(\.appContainer) private var container

    private var playlistService: PlaylistServiceImpl { container.playlistService }
    private var streamPlayer: StreamPlayer { container.streamPlayer }

    @State private var showRecordingPicker = false

    var body: some View {
        Group {
            if let show = playlistService.currentShow {
                showContent(show)
            } else if playlistService.trackLoadError != nil {
                errorState
            } else {
                ProgressView("Loading show…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .task { await playlistService.loadShow(showId) }
    }

    // MARK: - Show content

    private func showContent(_ show: Show) -> some View {
        List {
            // Header
            Section {
                VStack(alignment: .center, spacing: 12) {
                    ShowArtwork(
                        recordingId: playlistService.currentRecording?.identifier,
                        imageUrl: show.coverImageUrl,
                        size: 200,
                        cornerRadius: DeadlySize.carouselCornerRadius
                    )

                    Text(DateFormatting.formatShowDate(show.date))
                        .font(.headline)

                    Text(show.venue.name)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Text(show.location.displayText)
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if show.hasRating {
                        Text(show.displayRating)
                            .font(.caption)
                            .foregroundStyle(DeadlyColors.secondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            // Recording bar
            if let recording = playlistService.currentRecording {
                Section {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(recording.displayTitle)
                                .font(.subheadline)

                            if let taper = recording.taper {
                                Text("Taper: \(taper)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }

                        Spacer()

                        if show.hasMultipleRecordings {
                            Button("Switch") {
                                showRecordingPicker = true
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                    }
                }
            }

            // Track list
            Section("Tracks") {
                if playlistService.isLoadingTracks {
                    HStack {
                        Spacer()
                        ProgressView("Loading tracks…")
                        Spacer()
                    }
                    .listRowSeparator(.hidden)
                } else if let error = playlistService.trackLoadError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                } else {
                    ForEach(Array(playlistService.tracks.enumerated()), id: \.element.id) { index, track in
                        TrackListRow(
                            track: track,
                            index: index,
                            isPlaying: isCurrentTrack(track)
                        )
                        .contentShape(Rectangle())
                        .onTapGesture {
                            playlistService.playTrack(at: index)
                            playlistService.recordRecentPlay()
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .sheet(isPresented: $showRecordingPicker) {
            RecordingPicker(show: show, playlistService: playlistService)
        }
    }

    // MARK: - Error state

    private var errorState: some View {
        ContentUnavailableView(
            "Show Not Found",
            systemImage: "exclamationmark.triangle",
            description: Text(playlistService.trackLoadError ?? "")
        )
    }

    // MARK: - Helpers

    private func isCurrentTrack(_ track: ArchiveTrack) -> Bool {
        guard let recording = playlistService.currentRecording,
              let currentURL = streamPlayer.currentTrack?.url else { return false }
        return currentURL == track.streamURL(recordingId: recording.identifier)
    }
}
