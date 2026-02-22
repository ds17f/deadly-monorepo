import Foundation

struct GRDBShowRepository: ShowRepository {
    let showDAO: ShowDAO
    let recordingDAO: RecordingDAO

    // MARK: - Single show

    func getShowById(_ showId: String) throws -> Show? {
        try showDAO.fetchById(showId).map(mapShow)
    }

    func getShowsByIds(_ showIds: [String]) throws -> [Show] {
        try showDAO.fetchByIds(showIds).map(mapShow)
    }

    // MARK: - Browse

    func getAllShows() throws -> [Show] {
        try showDAO.fetchAll().map(mapShow)
    }

    func getShowCount() throws -> Int {
        try showDAO.fetchCount()
    }

    // MARK: - Date queries

    func getShowsByYear(_ year: Int) throws -> [Show] {
        try showDAO.fetchByYear(year).map(mapShow)
    }

    func getShowsByYearMonth(_ yearMonth: String) throws -> [Show] {
        try showDAO.fetchByYearMonth(yearMonth).map(mapShow)
    }

    func getShowsByDate(_ date: String) throws -> [Show] {
        try showDAO.fetchByDate(date).map(mapShow)
    }

    func getShowsForDate(month: Int, day: Int) throws -> [Show] {
        try showDAO.fetchOnThisDay(month: month, day: day).map(mapShow)
    }

    // MARK: - Location queries

    func getShowsByVenue(_ venueName: String) throws -> [Show] {
        try showDAO.fetchByVenue(venueName).map(mapShow)
    }

    func getShowsByCity(_ city: String) throws -> [Show] {
        try showDAO.fetchByCity(city).map(mapShow)
    }

    func getShowsByState(_ state: String) throws -> [Show] {
        try showDAO.fetchByState(state).map(mapShow)
    }

    // MARK: - Featured

    func getTopRatedShows(limit: Int) throws -> [Show] {
        try showDAO.fetchTopRated(limit: limit).map(mapShow)
    }

    // MARK: - Chronological navigation

    func getNextShow(afterDate: String) throws -> Show? {
        try showDAO.fetchNext(after: afterDate).map(mapShow)
    }

    func getPreviousShow(beforeDate: String) throws -> Show? {
        try showDAO.fetchPrevious(before: beforeDate).map(mapShow)
    }

    // MARK: - Recording queries

    func getRecordingsForShow(_ showId: String) throws -> [Recording] {
        try recordingDAO.fetchForShow(showId).map(mapRecording)
    }

    func getBestRecordingForShow(_ showId: String) throws -> Recording? {
        try recordingDAO.fetchBestForShow(showId).map(mapRecording)
    }

    func getRecordingById(_ identifier: String) throws -> Recording? {
        try recordingDAO.fetchById(identifier).map(mapRecording)
    }

    func getTopRatedRecordings(minRating: Double, minReviews: Int, limit: Int) throws -> [Recording] {
        try recordingDAO.fetchTopRated(minRating: minRating, minReviews: minReviews, limit: limit).map(mapRecording)
    }

    // MARK: - Mapping

    private func mapShow(_ record: ShowRecord) -> Show {
        let recordingIds: [String]
        if let raw = record.recordingsRaw,
           let data = raw.data(using: .utf8),
           let ids = try? JSONDecoder().decode([String].self, from: data) {
            recordingIds = ids
        } else {
            recordingIds = []
        }

        return Show(
            id: record.showId,
            date: record.date,
            year: record.year,
            band: record.band,
            venue: Venue(
                name: record.venueName,
                city: record.city,
                state: record.state,
                country: record.country
            ),
            location: Location.fromRaw(record.locationRaw, city: record.city, state: record.state),
            setlist: Setlist.parse(json: record.setlistRaw, status: record.setlistStatus),
            lineup: Lineup.parse(json: record.lineupRaw, status: record.lineupStatus),
            recordingIds: recordingIds,
            bestRecordingId: record.bestRecordingId,
            recordingCount: record.recordingCount,
            averageRating: record.averageRating.map { Float($0) },
            totalReviews: record.totalReviews,
            coverImageUrl: record.coverImageUrl,
            isInLibrary: record.isInLibrary,
            libraryAddedAt: record.libraryAddedAt
        )
    }

    private func mapRecording(_ record: RecordingRecord) -> Recording {
        Recording(
            identifier: record.identifier,
            showId: record.showId,
            sourceType: RecordingSourceType.fromString(record.sourceType),
            rating: record.rating,
            reviewCount: record.reviewCount,
            taper: record.taper,
            source: record.source,
            lineage: record.lineage,
            sourceTypeString: record.sourceTypeString
        )
    }
}
