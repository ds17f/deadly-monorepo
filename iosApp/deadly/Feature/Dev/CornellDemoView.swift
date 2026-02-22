import SwiftUI
import SwiftAudioStreamEx

struct CornellDemoView: View {
    @State private var player = StreamPlayer()
    @State private var sliderValue: Double?

    private static let base = "https://archive.org/download/gd77-05-08.sbd.hicks.4982.sbeok.shnf/"
    private static let album = "1977-05-08 Barton Hall"

    // Full Cornell '77 SBD (Hicks transfer) — all 20 tracks
    let cornellTracks: [TrackItem] = [
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t01.mp3")!, title: "Minglewood Blues", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t02.mp3")!, title: "Loser", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t03.mp3")!, title: "El Paso", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t04.mp3")!, title: "They Love Each Other", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t05.mp3")!, title: "Jack Straw", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t06.mp3")!, title: "Deal", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t07.mp3")!, title: "Lazy Lightning > Supplication", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t08.mp3")!, title: "Brown Eyed Women", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d1t09.mp3")!, title: "Mama Tried", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d2t01.mp3")!, title: "Row Jimmy", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d2t02.mp3")!, title: "Dancin' In The Streets", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d2t03.mp3")!, title: "Take A Step Back", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d2t04.mp3")!, title: "Scarlet Begonias > Fire On The Mountain", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t01.mp3")!, title: "Estimated Prophet", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t02.mp3")!, title: "Tuning", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t03.mp3")!, title: "Saint Stephen", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t04.mp3")!, title: "Not Fade Away", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t05.mp3")!, title: "Saint Stephen", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t06.mp3")!, title: "Morning Dew", artist: "Grateful Dead", albumTitle: album),
        TrackItem(url: URL(string: "\(base)gd77-05-08eaton-d3t07.mp3")!, title: "Saturday Night", artist: "Grateful Dead", albumTitle: album),
    ]

    /// Index of Estimated Prophet — start here for the St. Stephen > NFA > St. Stephen gapless run
    private let scarletFireIndex = 12

    var body: some View {
        VStack(spacing: 16) {
            // State debug info
            stateLabel
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.top, 8)

            Spacer()

            // Current track
            Text(player.currentTrack?.title ?? "No track loaded")
                .font(.title2)
                .fontWeight(.semibold)
            Text(player.currentTrack?.albumTitle ?? "")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            // Progress
            Slider(
                value: Binding(
                    get: { sliderValue ?? player.progress.progress },
                    set: { sliderValue = $0 }
                ),
                in: 0...1
            ) { editing in
                if !editing, let value = sliderValue {
                    let target = value * player.progress.duration
                    player.seek(to: target)
                    sliderValue = nil
                }
            }
            .padding(.horizontal)
            HStack {
                Text(formatTime(sliderValue.map { $0 * player.progress.duration } ?? player.progress.currentTime))
                Spacer()
                Text("-\(formatTime(player.progress.remaining))")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.horizontal)

            // Queue info
            if player.queueState.totalTracks > 0 {
                Text("Track \(player.queueState.currentIndex + 1) of \(player.queueState.totalTracks)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            // Controls
            HStack(spacing: 40) {
                Button {
                    player.previous()
                } label: {
                    Image(systemName: "backward.fill")
                        .font(.title2)
                }
                .disabled(!player.queueState.hasPrevious && player.progress.currentTime < 3)

                Button {
                    player.togglePlayPause()
                } label: {
                    Image(systemName: player.playbackState.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 50))
                }
                .disabled(player.playbackState.isIdle)

                Button {
                    player.next()
                } label: {
                    Image(systemName: "forward.fill")
                        .font(.title2)
                }
                .disabled(!player.queueState.hasNext)
            }

            Spacer()

            // Load queue buttons
            VStack(spacing: 8) {
                Button("Cornell '77 — Full Show") {
                    player.loadQueue(cornellTracks, startingAt: 0)
                }
                .buttonStyle(.borderedProminent)

                Button("Cornell '77 — Scarlet > Fire") {
                    player.loadQueue(cornellTracks, startingAt: scarletFireIndex)
                }
                .buttonStyle(.bordered)
            }
            .padding(.bottom, 4)

            // Stop button
            if player.playbackState.isActive {
                Button("Stop", role: .destructive) {
                    player.stop()
                }
                .font(.caption)
            }
        }
        .padding()
        .navigationTitle("Cornell '77 Demo")
    }

    @ViewBuilder
    var stateLabel: some View {
        switch player.playbackState {
        case .idle:
            Text("Idle")
        case .loading:
            Text("Loading...")
        case .buffering:
            Text("Buffering...")
        case .playing:
            Text("Playing")
                .foregroundStyle(.green)
        case .paused:
            Text("Paused")
        case .ended:
            Text("Queue ended")
        case .error(let error):
            Text("Error: \(error.localizedDescription)")
                .foregroundStyle(.red)
                .lineLimit(2)
                .font(.caption2)
        }
    }

    func formatTime(_ seconds: TimeInterval) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let mins = Int(seconds) / 60
        let secs = Int(seconds) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

#Preview {
    CornellDemoView()
}
