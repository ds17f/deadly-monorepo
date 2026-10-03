import Foundation
import Testing
@testable import SwiftAudioStreamEx

@Suite("CDNURLResolver")
struct CDNURLResolverTests {
    private let canonical = URL(string: "https://archive.org/download/item/t01.mp3")!
    private let fallbackA = URL(string: "https://ia800504.us.archive.org/33/items/item/t01.mp3")!
    private let fallbackB = URL(string: "https://ia600504.us.archive.org/33/items/item/t01.mp3")!
    private let cdn = URL(string: "https://dn720303.ca.archive.org/0/items/item/t01.mp3")!

    /// Fetcher that answers from a table. A missing entry is a transport error.
    private func resolver(_ table: [URL: CDNHeaderResponse]) -> CDNURLResolver {
        CDNURLResolver { url, _ in
            guard let response = table[url] else {
                throw URLError(.timedOut)
            }
            return response
        }
    }

    @Test("accepts HTTP 200 and 206")
    func acceptsSuccess() async {
        for status in [200, 206] {
            let r = resolver([canonical: CDNHeaderResponse(finalURL: canonical, statusCode: status)])
            let result = await r.resolve(candidates: [canonical])
            #expect(result.resolvedURL == canonical)
            #expect(result.attempts.count == 1)
            #expect(result.attempts[0].isSuccess)
        }
    }

    @Test("rejects 404 and 500")
    func rejectsErrorStatus() async {
        for status in [404, 500] {
            let r = resolver([canonical: CDNHeaderResponse(finalURL: canonical, statusCode: status)])
            let result = await r.resolve(candidates: [canonical])
            #expect(result.resolvedURL == nil)
            #expect(result.attempts[0].statusCode == status)
            #expect(result.attempts[0].failedHost == "archive.org")
        }
    }

    @Test("rejects a transport error and records domain:code")
    func rejectsTransportError() async {
        let result = await resolver([:]).resolve(candidates: [canonical])
        #expect(result.resolvedURL == nil)
        #expect(result.attempts[0].statusCode == nil)
        #expect(result.attempts[0].errorCode == "NSURLErrorDomain:\(URLError.timedOut.rawValue)")
    }

    @Test("returns the final URL after a redirect")
    func returnsFinalURL() async {
        let r = resolver([canonical: CDNHeaderResponse(finalURL: cdn, statusCode: 206)])
        let result = await r.resolve(candidates: [canonical])
        #expect(result.resolvedURL == cdn)
        #expect(result.attempts[0].requestedHost == "archive.org")
        #expect(result.attempts[0].finalHost == "dn720303.ca.archive.org")
    }

    @Test("falls through canonical to a storage server")
    func fallsThrough() async {
        let r = resolver([
            canonical: CDNHeaderResponse(finalURL: cdn, statusCode: 500),
            fallbackA: CDNHeaderResponse(finalURL: fallbackA, statusCode: 206),
        ])
        let result = await r.resolve(candidates: [canonical, fallbackA, fallbackB])
        #expect(result.resolvedURL == fallbackA)
        #expect(result.attempts.count == 2)
        #expect(result.attempts[0].failedHost == "dn720303.ca.archive.org")
        #expect(result.attempts[1].isSuccess)
    }

    @Test("empty fallbacks uses the canonical URL only")
    func canonicalOnly() async {
        let candidates = CDNURLResolver.orderedCandidates(canonical: canonical, fallbacks: [], failedHosts: [])
        #expect(candidates == [canonical])
        let result = await resolver([:]).resolve(candidates: candidates)
        #expect(result.resolvedURL == nil)
        #expect(result.attempts.count == 1)
    }

    @Test("an expired deadline stops before the first request")
    func deadlineStops() async {
        let r = resolver([canonical: CDNHeaderResponse(finalURL: canonical, statusCode: 200)])
        let result = await r.resolve(candidates: [canonical], deadline: Date.now.addingTimeInterval(-1))
        #expect(result.resolvedURL == nil)
        #expect(result.attempts.isEmpty)
    }

    @Test("candidate order keeps canonical first, then fallbacks, without duplicates")
    func defaultOrder() {
        let order = CDNURLResolver.orderedCandidates(
            canonical: canonical,
            fallbacks: [fallbackA, canonical, fallbackB],
            failedHosts: []
        )
        #expect(order == [canonical, fallbackA, fallbackB])
    }

    @Test("a failed host moves to the end, order otherwise unchanged")
    func failedHostDemoted() {
        let order = CDNURLResolver.orderedCandidates(
            canonical: canonical,
            fallbacks: [fallbackA, fallbackB],
            failedHosts: ["ia800504.us.archive.org"]
        )
        #expect(order == [canonical, fallbackB, fallbackA])
    }

    @Test("a failed canonical host moves canonical behind healthy fallbacks")
    func failedCanonicalDemoted() {
        let order = CDNURLResolver.orderedCandidates(
            canonical: canonical,
            fallbacks: [fallbackA, fallbackB],
            failedHosts: ["archive.org"]
        )
        #expect(order == [fallbackA, fallbackB, canonical])
    }

#if DEBUG
    @Test("debug hook rejects a dn final host and falls through; accepts other hosts")
    func rejectFinalHostHook() async {
        var r = resolver([
            canonical: CDNHeaderResponse(finalURL: cdn, statusCode: 206),
            fallbackA: CDNHeaderResponse(finalURL: fallbackA, statusCode: 206),
        ])
        r.rejectFinalHost = { $0.hasPrefix("dn") && $0.hasSuffix(".archive.org") }
        let result = await r.resolve(candidates: [canonical, fallbackA])
        #expect(result.resolvedURL == fallbackA)
        #expect(result.attempts[0].errorCode == "debug:forcedFallback")
        #expect(result.attempts[0].statusCode == nil)
        #expect(result.attempts[0].finalHost == "dn720303.ca.archive.org")
        #expect(result.attempts[0].failedHost == "dn720303.ca.archive.org")
        #expect(result.attempts[1].isSuccess)
    }
#endif
}
