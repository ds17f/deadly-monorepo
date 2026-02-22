import Foundation

protocol ShowRepository: Sendable {
    // Single show
    func getShowById(_ showId: String) throws -> Show?
    func getShowsByIds(_ showIds: [String]) throws -> [Show]

    // Browse
    func getAllShows() throws -> [Show]
    func getShowCount() throws -> Int

    // Date queries
    func getShowsByYear(_ year: Int) throws -> [Show]
    func getShowsByYearMonth(_ yearMonth: String) throws -> [Show]
    func getShowsByDate(_ date: String) throws -> [Show]
    func getShowsForDate(month: Int, day: Int) throws -> [Show]  // "On This Day"

    // Location queries
    func getShowsByVenue(_ venueName: String) throws -> [Show]
    func getShowsByCity(_ city: String) throws -> [Show]
    func getShowsByState(_ state: String) throws -> [Show]

    // Featured
    func getTopRatedShows(limit: Int) throws -> [Show]

    // Chronological navigation
    func getNextShow(afterDate: String) throws -> Show?
    func getPreviousShow(beforeDate: String) throws -> Show?

    // Recording queries
    func getRecordingsForShow(_ showId: String) throws -> [Recording]
    func getBestRecordingForShow(_ showId: String) throws -> Recording?
    func getRecordingById(_ identifier: String) throws -> Recording?
    func getTopRatedRecordings(minRating: Double, minReviews: Int, limit: Int) throws -> [Recording]
}
