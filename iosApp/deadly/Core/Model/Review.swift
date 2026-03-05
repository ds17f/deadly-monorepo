import Foundation

/// Aggregated review data for a show.
struct ShowReview: Codable, Sendable, Equatable {
    let showId: String
    var notes: String?
    var overallRating: Double?
    var recordingQuality: Int?
    var playingQuality: Int?
    var reviewedRecordingId: String?
    var trackReviews: [TrackReview]
    var playerTags: [PlayerTag]

    init(showId: String, notes: String? = nil, overallRating: Double? = nil,
         recordingQuality: Int? = nil, playingQuality: Int? = nil,
         reviewedRecordingId: String? = nil,
         trackReviews: [TrackReview] = [], playerTags: [PlayerTag] = []) {
        self.showId = showId
        self.notes = notes
        self.overallRating = overallRating
        self.recordingQuality = recordingQuality
        self.playingQuality = playingQuality
        self.reviewedRecordingId = reviewedRecordingId
        self.trackReviews = trackReviews
        self.playerTags = playerTags
    }

    var hasContent: Bool {
        notes != nil || overallRating != nil || recordingQuality != nil ||
        playingQuality != nil || !trackReviews.isEmpty || !playerTags.isEmpty
    }
}

/// Per-track review with thumbs and optional star rating.
struct TrackReview: Codable, Sendable, Equatable, Identifiable {
    let trackTitle: String
    var trackNumber: Int?
    var recordingId: String?
    var thumbs: Int?       // 1=up, -1=down, nil=unrated
    var starRating: Int?   // 1-5
    var notes: String?

    var id: String { "\(trackTitle)|\(recordingId ?? "")" }
    var isThumbsUp: Bool { thumbs == 1 }
    var isThumbsDown: Bool { thumbs == -1 }
    var hasRating: Bool { thumbs != nil || starRating != nil }
}

/// A thumbs-up track with its show context.
struct FavoriteTrack: Identifiable, Sendable {
    let showId: String
    let showDate: String
    let venue: String
    let trackTitle: String
    let trackNumber: Int?
    let recordingId: String?

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
