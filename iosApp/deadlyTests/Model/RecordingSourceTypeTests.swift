import Testing
@testable import deadly

@Suite("RecordingSourceType.fromString()")
struct RecordingSourceTypeTests {

    @Test func soundboardAliases() {
        #expect(RecordingSourceType.fromString("SBD") == .soundboard)
        #expect(RecordingSourceType.fromString("SOUNDBOARD") == .soundboard)
        #expect(RecordingSourceType.fromString("sbd") == .soundboard)
        #expect(RecordingSourceType.fromString("soundboard") == .soundboard)
    }

    @Test func audienceAliases() {
        #expect(RecordingSourceType.fromString("AUD") == .audience)
        #expect(RecordingSourceType.fromString("AUDIENCE") == .audience)
        #expect(RecordingSourceType.fromString("aud") == .audience)
    }

    @Test func fmAlias() {
        #expect(RecordingSourceType.fromString("FM") == .fm)
        #expect(RecordingSourceType.fromString("fm") == .fm)
    }

    @Test func matrixAliases() {
        #expect(RecordingSourceType.fromString("MATRIX") == .matrix)
        #expect(RecordingSourceType.fromString("MTX") == .matrix)
        #expect(RecordingSourceType.fromString("matrix") == .matrix)
        #expect(RecordingSourceType.fromString("mtx") == .matrix)
    }

    @Test func remaster() {
        #expect(RecordingSourceType.fromString("REMASTER") == .remaster)
        #expect(RecordingSourceType.fromString("remaster") == .remaster)
    }

    @Test func unknownFallback() {
        #expect(RecordingSourceType.fromString(nil) == .unknown)
        #expect(RecordingSourceType.fromString("") == .unknown)
        #expect(RecordingSourceType.fromString("SOMETHING_ELSE") == .unknown)
        #expect(RecordingSourceType.fromString("CASSETTE") == .unknown)
    }

    @Test func displayNames() {
        #expect(RecordingSourceType.soundboard.displayName == "SBD")
        #expect(RecordingSourceType.audience.displayName == "AUD")
        #expect(RecordingSourceType.fm.displayName == "FM")
        #expect(RecordingSourceType.matrix.displayName == "Matrix")
        #expect(RecordingSourceType.remaster.displayName == "Remaster")
        #expect(RecordingSourceType.unknown.displayName == "Unknown")
    }
}
