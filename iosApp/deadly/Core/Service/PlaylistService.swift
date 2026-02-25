import Foundation

@MainActor
protocol PlaylistService: AnyObject {
    var currentShow: Show? { get }
    var currentRecording: Recording? { get }
    var tracks: [ArchiveTrack] { get }
    var isLoadingTracks: Bool { get }
    var trackLoadError: String? { get }

    // Show navigation
    var hasNextShow: Bool { get }
    var hasPreviousShow: Bool { get }

    func loadShow(_ showId: String) async
    func selectRecording(_ recording: Recording) async
    func playTrack(at index: Int)
    func recordRecentPlay()

    // Navigate to adjacent shows by date
    func navigateToNextShow() async -> Bool
    func navigateToPreviousShow() async -> Bool
}
