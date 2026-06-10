import Foundation
import os

/// One upcoming entry in the show queue, hydrated for display.
struct QueuedShowItem: Identifiable, Equatable {
    let id: Int64            // play_queue row id (stable; used for remove/reorder)
    let show: Show
    let recordingId: String?
    let resumeTrackIndex: Int?
    let resumePositionMs: Int64?
}

/// Persistent show queue (ADR-0010). The queue is the single source of "what
/// plays next" (Apple-Music "Playing Next" model): it holds *upcoming* shows
/// only — the currently-playing show is a separate pointer owned by playback.
///
/// Local-only: never synced, never a Favorite. Persistent across app kills;
/// shrinks as shows are consumed. Unit is always a whole show.
@Observable
@MainActor
final class PlayQueueService {
    private let logger = Logger(subsystem: "com.grateful.deadly", category: "PlayQueue")

    private let dao: PlayQueueDAO
    private let showRepository: any ShowRepository

    /// Upcoming shows, head first. Reactive (drives the Queue UI).
    private(set) var items: [QueuedShowItem] = []

    init(dao: PlayQueueDAO, showRepository: any ShowRepository) {
        self.dao = dao
        self.showRepository = showRepository
        reload()
    }

    var isEmpty: Bool { items.isEmpty }

    func reload() {
        do {
            let records = try dao.fetchAll()
            let shows = (try? showRepository.getShowsByIds(records.map { $0.showId })) ?? []
            let byId = Dictionary(shows.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
            items = records.compactMap { r in
                guard let id = r.id, let show = byId[r.showId] else { return nil }
                return QueuedShowItem(
                    id: id, show: show, recordingId: r.recordingId,
                    resumeTrackIndex: r.resumeTrackIndex, resumePositionMs: r.resumePositionMs
                )
            }
        } catch {
            logger.error("reload failed: \(error.localizedDescription)")
            items = []
        }
    }

    /// Append a show to the bottom ("Add to Queue").
    func enqueue(showId: String, recordingId: String? = nil) {
        do { try dao.append(showId: showId, recordingId: recordingId); reload() }
        catch { logger.error("enqueue failed: \(error.localizedDescription)") }
    }

    /// Insert at the head with an optional resume snapshot (interrupt re-queue).
    func enqueueNext(showId: String, recordingId: String? = nil, resumeTrackIndex: Int? = nil, resumePositionMs: Int64? = nil) {
        do {
            try dao.insertHead(showId: showId, recordingId: recordingId, resumeTrackIndex: resumeTrackIndex, resumePositionMs: resumePositionMs)
            reload()
        } catch { logger.error("enqueueNext failed: \(error.localizedDescription)") }
    }

    func remove(id: Int64) {
        do { try dao.delete(id: id); reload() }
        catch { logger.error("remove failed: \(error.localizedDescription)") }
    }

    func clear() {
        do { try dao.clear(); reload() }
        catch { logger.error("clear failed: \(error.localizedDescription)") }
    }

    /// SwiftUI `.onMove` handler: reorder upcoming entries.
    func move(from source: IndexSet, to destination: Int) {
        var ids = items.map { $0.id }
        ids.move(fromOffsets: source, toOffset: destination)
        do { try dao.reorder(orderedIds: ids); reload() }
        catch { logger.error("move failed: \(error.localizedDescription)") }
    }

    /// Remove and return the head — used by end-of-show auto-advance.
    @discardableResult
    func popNext() -> QueuedShowItem? {
        defer { reload() }
        guard let rec = try? dao.popHead(), let id = rec.id,
              let show = try? showRepository.getShowById(rec.showId) else { return nil }
        return QueuedShowItem(
            id: id, show: show, recordingId: rec.recordingId,
            resumeTrackIndex: rec.resumeTrackIndex, resumePositionMs: rec.resumePositionMs
        )
    }

    func peekNext() -> QueuedShowItem? { items.first }

    func contains(showId: String) -> Bool {
        (try? dao.contains(showId: showId)) ?? false
    }
}
