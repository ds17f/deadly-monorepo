import Foundation
import Testing
@testable import SwiftAudioStreamEx

@Suite("CDNRecoveryPlanner")
struct CDNRecoveryPlannerTests {
    private func url(_ s: String) -> URL { URL(string: "https://\(s)")! }

    @Test("last track requires no next URL")
    func lastTrack() {
        let resolved = [url("a/1"), url("a/2")]
        #expect(CDNRecoveryPlanner.nextIndexToValidate(resolved: resolved, currentIndex: 1) == nil)
        #expect(CDNRecoveryPlanner.nextIndexToValidate(resolved: resolved, currentIndex: 0) == 1)
    }

    @Test("a local file next track is not validated")
    func localNext() {
        let resolved = [url("a/1"), URL(fileURLWithPath: "/tmp/2.mp3")]
        #expect(CDNRecoveryPlanner.nextIndexToValidate(resolved: resolved, currentIndex: 0) == nil)
    }

    @Test("install replaces current and next, keeps later tracks")
    func installKeepsRest() {
        let resolved = [url("old/0"), url("old/1"), url("old/2"), url("old/3")]
        let result = CDNRecoveryPlanner.install(
            resolved: resolved, currentIndex: 1,
            currentURL: url("new/1"), nextIndex: 2, nextURL: url("new/2")
        )
        #expect(result.resolved == [url("old/0"), url("new/1"), url("new/2"), url("old/3")])
        #expect(result.pending == [url("new/2"), url("old/3")])
    }

    @Test("install at the last index has empty pending")
    func installLast() {
        let result = CDNRecoveryPlanner.install(
            resolved: [url("old/0"), url("old/1")], currentIndex: 1,
            currentURL: url("new/1"), nextIndex: nil, nextURL: nil
        )
        #expect(result.resolved == [url("old/0"), url("new/1")])
        #expect(result.pending.isEmpty)
    }

    @Test("install with a local next keeps its mapping in pending")
    func installLocalNext() {
        let local = URL(fileURLWithPath: "/tmp/2.mp3")
        let result = CDNRecoveryPlanner.install(
            resolved: [url("old/0"), local], currentIndex: 0,
            currentURL: url("new/0"), nextIndex: nil, nextURL: nil
        )
        #expect(result.pending == [local])
    }
}
