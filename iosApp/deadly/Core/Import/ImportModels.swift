import Foundation

// MARK: - GitHub API types

struct GitHubRelease: Decodable, Sendable {
    struct Asset: Decodable, Sendable {
        let name: String
        let browserDownloadUrl: String

        enum CodingKeys: String, CodingKey {
            case name
            case browserDownloadUrl = "browser_download_url"
        }
    }

    let tagName: String
    let assets: [Asset]

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case assets
    }

    /// First asset whose name starts with "data" and ends with ".zip".
    var dataZipAsset: Asset? {
        assets.first(where: { $0.name.hasPrefix("data") && $0.name.hasSuffix(".zip") })
    }
}

// MARK: - JSON value helper

/// Recursive JSON value used to capture polymorphic fields as raw JSON strings.
private enum JSONValue: Decodable {
    case null
    case bool(Bool)
    case integer(Int)
    case float(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null; return }
        if let v = try? c.decode(Bool.self) { self = .bool(v); return }
        if let v = try? c.decode(Int.self) { self = .integer(v); return }
        if let v = try? c.decode(Double.self) { self = .float(v); return }
        if let v = try? c.decode(String.self) { self = .string(v); return }
        if let v = try? c.decode([JSONValue].self) { self = .array(v); return }
        if let v = try? c.decode([String: JSONValue].self) { self = .object(v); return }
        self = .null
    }

    /// Foundation-compatible representation for JSONSerialization.
    var nsObject: NSObject {
        switch self {
        case .null: return NSNull()
        case .bool(let v): return NSNumber(value: v)
        case .integer(let v): return NSNumber(value: v)
        case .float(let v): return NSNumber(value: v)
        case .string(let v): return v as NSString
        case .array(let arr): return arr.map(\.nsObject) as NSArray
        case .object(let dict): return dict.mapValues(\.nsObject) as NSDictionary
        }
    }
}

// MARK: - RawJSON

/// Captures an arbitrary JSON value as a raw string during Decodable parsing.
/// Used for polymorphic fields like `setlist` that can be null, string, array, or object.
struct RawJSON: Decodable, Sendable {
    /// The serialised JSON string, or nil if the JSON value was null.
    let jsonString: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        guard !c.decodeNil() else { jsonString = nil; return }
        let value = try JSONValue(from: decoder)
        switch value {
        case .null:
            jsonString = nil
        default:
            let obj = value.nsObject
            if let data = try? JSONSerialization.data(withJSONObject: obj, options: .fragmentsAllowed),
               let str = String(data: data, encoding: .utf8) {
                jsonString = str
            } else {
                jsonString = nil
            }
        }
    }

    /// Extract song names from setlist structure: `[{songs: [{name: "..."}]}]`
    func extractSongList() -> [String]? {
        guard let str = jsonString,
              let data = str.data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        var songs: [String] = []
        for set in array {
            if let songsArr = set["songs"] as? [[String: Any]] {
                for song in songsArr {
                    if let name = song["name"] as? String, !name.isEmpty {
                        songs.append(name)
                    }
                }
            }
        }
        return songs.isEmpty ? nil : songs
    }
}

// MARK: - Show import types

struct LineupMemberData: Codable, Sendable {
    let name: String
    let instruments: String?
    let imageUrl: String?

    enum CodingKeys: String, CodingKey {
        case name
        case instruments
        case imageUrl = "image_url"
    }
}

struct TicketImageData: Decodable, Sendable {
    let url: String
    let filename: String?
    let side: String?
}

struct ShowPhotoData: Decodable, Sendable {
    let url: String
    let filename: String?
    let thumbnailUrl: String?

    enum CodingKeys: String, CodingKey {
        case url
        case filename
        case thumbnailUrl = "thumbnail_url"
    }
}

struct ShowImportData: Decodable, Sendable {
    let showId: String
    let band: String
    let venue: String
    let locationRaw: String?
    let city: String?
    let state: String?
    let country: String?
    let date: String
    let url: String?
    let setlistStatus: String?
    let setlist: RawJSON?
    let lineupStatus: String?
    let lineup: [LineupMemberData]?
    let recordings: [String]
    let bestRecording: String?
    let avgRating: Double
    let recordingCount: Int
    let totalHighRatings: Int
    let totalLowRatings: Int
    let sourceTypes: [String: Int]
    let ticketImages: [TicketImageData]
    let photos: [ShowPhotoData]

    enum CodingKeys: String, CodingKey {
        case showId = "show_id"
        case band, venue
        case locationRaw = "location_raw"
        case city, state, country, date, url
        case setlistStatus = "setlist_status"
        case setlist
        case lineupStatus = "lineup_status"
        case lineup
        case recordings
        case bestRecording = "best_recording"
        case avgRating = "avg_rating"
        case recordingCount = "recording_count"
        case totalHighRatings = "total_high_ratings"
        case totalLowRatings = "total_low_ratings"
        case sourceTypes = "source_types"
        case ticketImages = "ticket_images"
        case photos
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        showId = try c.decode(String.self, forKey: .showId)
        band = try c.decode(String.self, forKey: .band)
        venue = try c.decode(String.self, forKey: .venue)
        locationRaw = try c.decodeIfPresent(String.self, forKey: .locationRaw)
        city = try c.decodeIfPresent(String.self, forKey: .city)
        state = try c.decodeIfPresent(String.self, forKey: .state)
        country = try c.decodeIfPresent(String.self, forKey: .country)
        date = try c.decode(String.self, forKey: .date)
        url = try c.decodeIfPresent(String.self, forKey: .url)
        setlistStatus = try c.decodeIfPresent(String.self, forKey: .setlistStatus)
        setlist = try c.decodeIfPresent(RawJSON.self, forKey: .setlist)
        lineupStatus = try c.decodeIfPresent(String.self, forKey: .lineupStatus)
        lineup = try c.decodeIfPresent([LineupMemberData].self, forKey: .lineup)
        recordings = (try? c.decodeIfPresent([String].self, forKey: .recordings)) ?? []
        bestRecording = try c.decodeIfPresent(String.self, forKey: .bestRecording)
        avgRating = (try? c.decodeIfPresent(Double.self, forKey: .avgRating)) ?? 0.0
        recordingCount = (try? c.decodeIfPresent(Int.self, forKey: .recordingCount)) ?? 0
        totalHighRatings = (try? c.decodeIfPresent(Int.self, forKey: .totalHighRatings)) ?? 0
        totalLowRatings = (try? c.decodeIfPresent(Int.self, forKey: .totalLowRatings)) ?? 0
        sourceTypes = (try? c.decodeIfPresent([String: Int].self, forKey: .sourceTypes)) ?? [:]
        ticketImages = (try? c.decodeIfPresent([TicketImageData].self, forKey: .ticketImages)) ?? []
        photos = (try? c.decodeIfPresent([ShowPhotoData].self, forKey: .photos)) ?? []
    }
}

// MARK: - Recording import types

struct TrackFormatData: Decodable, Sendable {
    let format: String
    let filename: String
    let bitrate: String?
}

struct TrackData: Decodable, Sendable {
    let track: String
    let title: String
    let duration: Double
    let formats: [TrackFormatData]

    enum CodingKeys: CodingKey {
        case track, title, duration, formats
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        track = try c.decode(String.self, forKey: .track)
        title = try c.decode(String.self, forKey: .title)
        duration = (try? c.decodeIfPresent(Double.self, forKey: .duration)) ?? 0.0
        formats = (try? c.decodeIfPresent([TrackFormatData].self, forKey: .formats)) ?? []
    }
}

struct RecordingImportData: Decodable, Sendable {
    let rating: Double
    let reviewCount: Int
    let sourceType: String?
    let confidence: Double
    let date: String
    let venue: String
    let location: String
    let rawRating: Double
    let highRatings: Int
    let lowRatings: Int
    let tracks: [TrackData]
    let taper: String?
    let source: String?
    let lineage: String?

    enum CodingKeys: String, CodingKey {
        case rating
        case reviewCount = "review_count"
        case sourceType = "source_type"
        case confidence
        case date, venue, location
        case rawRating = "raw_rating"
        case highRatings = "high_ratings"
        case lowRatings = "low_ratings"
        case tracks, taper, source, lineage
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rating = (try? c.decodeIfPresent(Double.self, forKey: .rating)) ?? 0.0
        reviewCount = (try? c.decodeIfPresent(Int.self, forKey: .reviewCount)) ?? 0
        sourceType = try c.decodeIfPresent(String.self, forKey: .sourceType)
        confidence = (try? c.decodeIfPresent(Double.self, forKey: .confidence)) ?? 0.0
        date = (try? c.decode(String.self, forKey: .date)) ?? ""
        venue = (try? c.decode(String.self, forKey: .venue)) ?? ""
        location = (try? c.decode(String.self, forKey: .location)) ?? ""
        rawRating = (try? c.decodeIfPresent(Double.self, forKey: .rawRating)) ?? 0.0
        highRatings = (try? c.decodeIfPresent(Int.self, forKey: .highRatings)) ?? 0
        lowRatings = (try? c.decodeIfPresent(Int.self, forKey: .lowRatings)) ?? 0
        tracks = (try? c.decodeIfPresent([TrackData].self, forKey: .tracks)) ?? []
        taper = try c.decodeIfPresent(String.self, forKey: .taper)
        source = try c.decodeIfPresent(String.self, forKey: .source)
        lineage = try c.decodeIfPresent(String.self, forKey: .lineage)
    }
}

// MARK: - Collections import types

struct CollectionsWrapper: Decodable, Sendable {
    let collections: [CollectionImportData]
}

struct CollectionImportData: Decodable, Sendable {
    let id: String
    let name: String
    let description: String
    let tags: [String]
    let showSelector: ShowSelectorData?

    enum CodingKeys: String, CodingKey {
        case id, name, description, tags
        case showSelector = "show_selector"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        description = try c.decode(String.self, forKey: .description)
        tags = (try? c.decodeIfPresent([String].self, forKey: .tags)) ?? []
        showSelector = try c.decodeIfPresent(ShowSelectorData.self, forKey: .showSelector)
    }
}

struct ShowSelectorData: Decodable, Sendable {
    let dates: [String]
    let ranges: [DateRangeData]
    let range: DateRangeData?
    let exclusionRanges: [ExternalDateRangeData]
    let exclusionDates: [String]
    let showIds: [String]
    let venues: [String]
    let years: [Int]

    enum CodingKeys: String, CodingKey {
        case dates, ranges, range
        case exclusionRanges = "exclusion_ranges"
        case exclusionDates = "exclusion_dates"
        case showIds = "show_ids"
        case venues, years
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        dates = (try? c.decodeIfPresent([String].self, forKey: .dates)) ?? []
        ranges = (try? c.decodeIfPresent([DateRangeData].self, forKey: .ranges)) ?? []
        range = try? c.decodeIfPresent(DateRangeData.self, forKey: .range)
        exclusionRanges = (try? c.decodeIfPresent([ExternalDateRangeData].self, forKey: .exclusionRanges)) ?? []
        exclusionDates = (try? c.decodeIfPresent([String].self, forKey: .exclusionDates)) ?? []
        showIds = (try? c.decodeIfPresent([String].self, forKey: .showIds)) ?? []
        venues = (try? c.decodeIfPresent([String].self, forKey: .venues)) ?? []
        years = (try? c.decodeIfPresent([Int].self, forKey: .years)) ?? []
    }
}

struct DateRangeData: Decodable, Sendable {
    let start: String
    let end: String
}

struct ExternalDateRangeData: Decodable, Sendable {
    let from: String
    let to: String
}

// MARK: - Manifest

struct ManifestData: Decodable, Sendable {
    struct PackageInfo: Decodable, Sendable {
        let version: String?
    }
    struct BuildInfo: Decodable, Sendable {
        let gitCommit: String?
        let buildTimestamp: String?

        enum CodingKeys: String, CodingKey {
            case gitCommit = "git_commit"
            case buildTimestamp = "build_timestamp"
        }
    }
    let packageInfo: PackageInfo?
    let buildInfo: BuildInfo?

    enum CodingKeys: String, CodingKey {
        case packageInfo = "package"
        case buildInfo = "build_info"
    }
}

// MARK: - Progress types

enum ImportPhase: String, Sendable {
    case checking = "CHECKING"
    case downloading = "DOWNLOADING"
    case extracting = "EXTRACTING"
    case readingShows = "READING_SHOWS"
    case readingRecordings = "READING_RECORDINGS"
    case importingShows = "IMPORTING_SHOWS"
    case importingRecordings = "IMPORTING_RECORDINGS"
    case importingCollections = "IMPORTING_COLLECTIONS"
    case finalizing = "FINALIZING"
    case completed = "COMPLETED"
    case failed = "FAILED"
}

struct ImportProgress: Sendable {
    let phase: ImportPhase
    let processed: Int
    let total: Int
    let message: String

    var fraction: Double {
        total > 0 ? Double(processed) / Double(total) : 0
    }
}

struct ImportResult: Sendable {
    let success: Bool
    let importedShows: Int
    let importedRecordings: Int
    let importedCollections: Int
    let message: String
}
