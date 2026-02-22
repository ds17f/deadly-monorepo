import Foundation

@MainActor
protocol PlaylistService: AnyObject {
    var currentShow: Show? { get }
    var currentRecording: Recording? { get }
    var tracks: [ArchiveTrack] { get }
    var isLoadingTracks: Bool { get }
    var trackLoadError: String? { get }

    func loadShow(_ showId: String) async
    func selectRecording(_ recording: Recording) async
    func playTrack(at index: Int)
    func recordRecentPlay()
}
