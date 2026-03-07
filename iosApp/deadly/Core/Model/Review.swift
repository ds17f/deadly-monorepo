import Foundation

/// Aggregated review data for a show.
struct ShowReview: Codable, Sendable, Equatable {
    let showId: String
    var notes: String?
    var overallRating: Double?
    var recordingQuality: Int?
    var playingQuality: Int?
    var reviewedRecordingId: String?
    var playerTags: [PlayerTag]

    init(showId: String, notes: String? = nil, overallRating: Double? = nil,
         recordingQuality: Int? = nil, playingQuality: Int? = nil,
         reviewedRecordingId: String? = nil,
         playerTags: [PlayerTag] = []) {
        self.showId = showId
        self.notes = notes
        self.overallRating = overallRating
        self.recordingQuality = recordingQuality
        self.playingQuality = playingQuality
        self.reviewedRecordingId = reviewedRecordingId
        self.playerTags = playerTags
    }

    var hasContent: Bool {
        (notes != nil && !notes!.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) ||
        overallRating != nil || recordingQuality != nil ||
        playingQuality != nil || !playerTags.isEmpty
    }
}

/// A favorited track with its show context.
struct FavoriteTrack: Identifiable, Sendable {
    let showId: String
    let showDate: String
    let venue: String
    let trackTitle: String
    let trackNumber: Int?
    let recordingId: String?
    let addedAt: Int64

    var id: String { "\(showId)|\(trackTitle)|\(recordingId ?? "")" }
}

/// A tagged musician for a particular show.
struct PlayerTag: Codable, Sendable, Equatable, Identifiable {
    let playerName: String
    var instruments: String?
    var isStandout: Bool
    var notes: String?

    var id: String { playerName }

    init(playerName: String, instruments: String? = nil, isStandout: Bool = true, notes: String? = nil) {
        self.playerName = playerName
        self.instruments = instruments
        self.isStandout = isStandout
        self.notes = notes
    }
}
