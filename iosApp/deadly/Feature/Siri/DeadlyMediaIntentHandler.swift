import Intents
import SwiftAudioStreamEx

/// Handles INPlayMediaIntent from Siri — resolves and plays shows from voice commands.
/// Works both from the phone and from CarPlay's Siri button.
@MainActor
final class DeadlyMediaIntentHandler: NSObject, INPlayMediaIntentHandling {
    private let container: AppContainer
    private let trackResolver: CarPlayTrackResolver

    init(container: AppContainer) {
        self.container = container
        self.trackResolver = CarPlayTrackResolver(
            showRepository: container.showRepository,
            archiveClient: container.archiveClient,
            recordingPreferenceDAO: RecordingPreferenceDAO(database: container.database),
            downloadService: container.downloadService
        )
        super.init()
    }

    // MARK: - INPlayMediaIntentHandling

    func handle(intent: INPlayMediaIntent) async -> INPlayMediaIntentResponse {
        guard let searchTerm = intent.mediaSearch?.mediaName ?? intent.mediaSearch?.genreNames?.first else {
            return INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil)
        }

        let parsed = DeadlyMediaSearchResolver.parse(searchTerm)

        do {
            switch parsed {
            case .date(let isoDate):
                return try await playShowByDate(isoDate)

            case .venue(let venue):
                return try await playShowByVenue(venue)

            case .city(let city):
                return try await playShowByCity(city)

            case .general(let query):
                return try await playShowBySearch(query)
            }
        } catch {
            return INPlayMediaIntentResponse(code: .failure, userActivity: nil)
        }
    }

    func resolveMediaItems(for intent: INPlayMediaIntent) async -> [INPlayMediaMediaItemResolutionResult] {
        guard let searchTerm = intent.mediaSearch?.mediaName ?? intent.mediaSearch?.genreNames?.first else {
            return [.unsupported()]
        }

        let parsed = DeadlyMediaSearchResolver.parse(searchTerm)

        do {
            let shows: [Show]
            switch parsed {
            case .date(let isoDate):
                shows = try container.showRepository.getShowsByDate(isoDate)
            case .venue(let venue):
                shows = try container.showRepository.getShowsByVenue(venue)
            case .city(let city):
                shows = try container.showRepository.getShowsByCity(city)
            case .general(let query):
                await container.searchService.search(query: query)
                shows = container.searchService.results.map(\.show)
            }

            if shows.isEmpty {
                return [.unsupported()]
            }

            let mediaItems = shows.prefix(5).map { show in
                let item = INMediaItem(
                    identifier: show.id,
                    title: "\(show.date) — \(show.venue.name)",
                    type: .album,
                    artwork: nil
                )
                return item
            }

            if mediaItems.count == 1 {
                return [.success(with: mediaItems[0])]
            }
            return [INPlayMediaMediaItemResolutionResult.disambiguation(with: Array(mediaItems))]
        } catch {
            return [.unsupported()]
        }
    }

    // MARK: - Playback Helpers

    private func playShowByDate(_ isoDate: String) async throws -> INPlayMediaIntentResponse {
        let shows = try container.showRepository.getShowsByDate(isoDate)
        guard let show = shows.first else {
            return INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil)
        }
        return try await playShow(show.id)
    }

    private func playShowByVenue(_ venue: String) async throws -> INPlayMediaIntentResponse {
        let shows = try container.showRepository.getShowsByVenue(venue)
        if shows.isEmpty {
            return INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil)
        }
        if shows.count == 1 {
            return try await playShow(shows[0].id)
        }
        // Multiple matches — pick the highest rated
        let best = shows.max(by: { ($0.averageRating ?? 0) < ($1.averageRating ?? 0) })
        return try await playShow(best?.id ?? shows[0].id)
    }

    private func playShowByCity(_ city: String) async throws -> INPlayMediaIntentResponse {
        let shows = try container.showRepository.getShowsByCity(city)
        if shows.isEmpty {
            return INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil)
        }
        let best = shows.max(by: { ($0.averageRating ?? 0) < ($1.averageRating ?? 0) })
        return try await playShow(best?.id ?? shows[0].id)
    }

    private func playShowBySearch(_ query: String) async throws -> INPlayMediaIntentResponse {
        await container.searchService.search(query: query)
        guard let firstResult = container.searchService.results.first else {
            return INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil)
        }
        return try await playShow(firstResult.show.id)
    }

    private func playShow(_ showId: String) async throws -> INPlayMediaIntentResponse {
        guard let resolved = try await trackResolver.resolve(showId: showId) else {
            return INPlayMediaIntentResponse(code: .failureUnknownMediaType, userActivity: nil)
        }

        container.streamPlayer.loadQueue(resolved.trackItems, startingAt: 0)
        container.recentShowsService.recordShowPlay(showId: showId)
        return INPlayMediaIntentResponse(code: .success, userActivity: nil)
    }
}
