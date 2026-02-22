import Foundation

/// Recording metadata returned by the Archive.org service.
struct RecordingMetadata: Codable, Sendable, Equatable, Identifiable {
    let identifier: String
    let title: String
    let date: String?
    let venue: String?
    let description: String?
    let setlist: String?
    let source: String?
    let taper: String?
    let transferer: String?
    let lineage: String?
    let totalTracks: Int
    let totalReviews: Int

    var id: String { identifier }

    init(identifier: String, title: String, date: String? = nil, venue: String? = nil,
         description: String? = nil, setlist: String? = nil, source: String? = nil,
         taper: String? = nil, transferer: String? = nil, lineage: String? = nil,
         totalTracks: Int = 0, totalReviews: Int = 0) {
        self.identifier = identifier
        self.title = title
        self.date = date
        self.venue = venue
        self.description = description
        self.setlist = setlist
        self.source = source
        self.taper = taper
        self.transferer = transferer
        self.lineage = lineage
        self.totalTracks = totalTracks
        self.totalReviews = totalReviews
    }
}

/// A single audio track from Archive.org.
/// String? for duration/size/bitrate because Archive.org returns inconsistent types.
struct Track: Codable, Sendable, Equatable, Identifiable {
    let name: String          // filename, used as stable identifier
    let title: String?
    let trackNumber: Int?
    let duration: String?
    let format: String
    let size: String?
    let bitrate: String?
    let sampleRate: String?
    let isAudio: Bool

    var id: String { name }

    init(name: String, title: String? = nil, trackNumber: Int? = nil,
         duration: String? = nil, format: String, size: String? = nil,
         bitrate: String? = nil, sampleRate: String? = nil, isAudio: Bool = true) {
        self.name = name
        self.title = title
        self.trackNumber = trackNumber
        self.duration = duration
        self.format = format
        self.size = size
        self.bitrate = bitrate
        self.sampleRate = sampleRate
        self.isAudio = isAudio
    }
}

/// A review for an Archive.org recording.
struct Review: Codable, Sendable, Equatable {
    let reviewer: String?
    let title: String?
    let body: String?
    let rating: Int?
    let reviewDate: String?

    init(reviewer: String? = nil, title: String? = nil, body: String? = nil,
         rating: Int? = nil, reviewDate: String? = nil) {
        self.reviewer = reviewer
        self.title = title
        self.body = body
        self.rating = rating
        self.reviewDate = reviewDate
    }
}
