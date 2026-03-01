import Testing
import Foundation
@testable import deadly

@Suite("DeepLink URL parsing")
struct DeepLinkTests {

    // MARK: - deadly:// scheme

    @Test func customSchemeShowWithRecording() {
        let url = URL(string: "deadly://show/1977-05-08/recording/gd77-05-08.xxx")!
        #expect(DeepLink.parse(url) == .show(id: "1977-05-08", recordingId: "gd77-05-08.xxx"))
    }

    @Test func customSchemeShowWithoutRecording() {
        let url = URL(string: "deadly://show/1977-05-08")!
        #expect(DeepLink.parse(url) == .show(id: "1977-05-08", recordingId: nil))
    }

    @Test func customSchemeCollection() {
        let url = URL(string: "deadly://collection/fall-1972")!
        #expect(DeepLink.parse(url) == .collection(id: "fall-1972"))
    }

    @Test func customSchemeUnknownHost() {
        let url = URL(string: "deadly://unknown/foo")!
        #expect(DeepLink.parse(url) == nil)
    }

    // MARK: - Universal Links (https://share.thedeadly.app)

    @Test func universalLinkShowWithRecording() {
        let url = URL(string: "https://share.thedeadly.app/show/1977-05-08/recording/gd77-05-08.xxx")!
        #expect(DeepLink.parse(url) == .show(id: "1977-05-08", recordingId: "gd77-05-08.xxx"))
    }

    @Test func universalLinkShowWithoutRecording() {
        let url = URL(string: "https://share.thedeadly.app/show/1977-05-08")!
        #expect(DeepLink.parse(url) == .show(id: "1977-05-08", recordingId: nil))
    }

    @Test func universalLinkCollection() {
        let url = URL(string: "https://share.thedeadly.app/collection/fall-1972")!
        #expect(DeepLink.parse(url) == .collection(id: "fall-1972"))
    }

    @Test func universalLinkRealShareURL() {
        let url = URL(string: "https://share.thedeadly.app/show/1981-03-28-gruga-halle-essen-germany/recording/gd81-03-28.fm.hanno.3306.sbefail.shnf")!
        #expect(DeepLink.parse(url) == .show(
            id: "1981-03-28-gruga-halle-essen-germany",
            recordingId: "gd81-03-28.fm.hanno.3306.sbefail.shnf"
        ))
    }

    @Test func universalLinkWrongHost() {
        let url = URL(string: "https://example.com/show/foo")!
        #expect(DeepLink.parse(url) == nil)
    }

    @Test func universalLinkUnknownPath() {
        let url = URL(string: "https://share.thedeadly.app/unknown/foo")!
        #expect(DeepLink.parse(url) == nil)
    }
}
