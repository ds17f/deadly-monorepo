import Foundation
import Testing
@testable import deadly

@Suite("LastPlayedTrackStore")
struct LastPlayedTrackStoreTests {
    private func makeStore() -> LastPlayedTrackStore {
        let defaults = UserDefaults(suiteName: "deadly.test.LastPlayedTrack.\(UUID().uuidString)")!
        return LastPlayedTrackStore(defaults: defaults)
    }

    private static let sample = LastPlayedTrack(
        showId: "gd1977-05-08",
        recordingId: "gd77-05-08.sbd.hicks",
        trackIndex: 3,
        positionMs: 142_000,
        trackTitle: "Scarlet Begonias",
        showDate: "1977-05-08",
        venue: "Barton Hall",
        location: "Ithaca, NY"
    )

    @Test("load returns nil when nothing has been saved")
    func loadNilWhenEmpty() {
        let store = makeStore()
        #expect(store.load() == nil)
    }

    @Test("save and load round-trips all fields")
    func saveAndLoad() {
        let store = makeStore()
        store.save(Self.sample)
        let loaded = store.load()
        #expect(loaded == Self.sample)
    }

    @Test("clear removes saved state")
    func clearRemovesSavedState() {
        let store = makeStore()
        store.save(Self.sample)
        store.clear()
        #expect(store.load() == nil)
    }

    @Test("save overwrites previous entry")
    func saveOverwritesPrevious() {
        let store = makeStore()
        store.save(Self.sample)

        let updated = LastPlayedTrack(
            showId: "gd1977-05-08",
            recordingId: "gd77-05-08.sbd.hicks",
            trackIndex: 5,
            positionMs: 200_000,
            trackTitle: "Fire on the Mountain",
            showDate: "1977-05-08",
            venue: "Barton Hall",
            location: "Ithaca, NY"
        )
        store.save(updated)
        #expect(store.load() == updated)
    }

    @Test("nil venue and location round-trip correctly")
    func nilVenueAndLocation() {
        let store = makeStore()
        let track = LastPlayedTrack(
            showId: "gd1977-05-08",
            recordingId: "gd77-05-08.sbd.hicks",
            trackIndex: 0,
            positionMs: 0,
            trackTitle: "Truckin'",
            showDate: "1977-05-08",
            venue: nil,
            location: nil
        )
        store.save(track)
        let loaded = store.load()
        #expect(loaded?.venue == nil)
        #expect(loaded?.location == nil)
    }
}
