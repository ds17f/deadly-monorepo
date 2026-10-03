import Testing
@testable import SwiftAudioStreamEx

@Suite("HTTPProbeResult")
struct HTTPProbeResultTests {
    @Test("log description includes HTTP routing details")
    func routingDetails() {
        let result = HTTPProbeResult(
            requestedHost: "dn.example.org",
            finalHost: "ia.example.org",
            statusCode: 503,
            contentRange: "bytes 0-1/1234",
            server: "nginx",
            retryAfter: "30",
            errorCode: nil
        )

        #expect(result.logDescription == "requestedHost=dn.example.org finalHost=ia.example.org status=503 contentRange=bytes 0-1/1234 server=nginx retryAfter=30 error=nil")
    }

    @Test("log description represents transport failures without localized text")
    func transportFailure() {
        let result = HTTPProbeResult(
            requestedHost: "dn.example.org",
            finalHost: nil,
            statusCode: nil,
            contentRange: nil,
            server: nil,
            retryAfter: nil,
            errorCode: "NSURLErrorDomain:-1001"
        )

        #expect(result.logDescription == "requestedHost=dn.example.org finalHost=nil status=nil contentRange=nil server=nil retryAfter=nil error=NSURLErrorDomain:-1001")
    }
}
