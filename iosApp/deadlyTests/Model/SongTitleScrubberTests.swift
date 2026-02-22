import Testing
@testable import deadly

@Suite("SongTitleScrubber")
struct SongTitleScrubberTests {

    // MARK: - scrub()

    @Test func stripLeadingTrackNumberWithSpace() {
        #expect(SongTitleScrubber.scrub("02 Scarlet Begonias") == "Scarlet Begonias")
    }

    @Test func stripLeadingTrackNumberWithDot() {
        #expect(SongTitleScrubber.scrub("02. Scarlet Begonias") == "Scarlet Begonias")
    }

    @Test func stripLeadingTrackNumberWithDash() {
        #expect(SongTitleScrubber.scrub("02-Scarlet Begonias") == "Scarlet Begonias")
    }

    @Test func stripTrailingSegueMarker() {
        #expect(SongTitleScrubber.scrub("Scarlet Begonias >") == "Scarlet Begonias")
    }

    @Test func stripTrailingDoubleSegueMarker() {
        #expect(SongTitleScrubber.scrub("Scarlet Begonias >>") == "Scarlet Begonias")
    }

    @Test func stripLeadingSegueMarker() {
        #expect(SongTitleScrubber.scrub("> Fire on the Mountain") == "Fire on the Mountain")
    }

    @Test func stripBracketedSuffix() {
        #expect(SongTitleScrubber.scrub("Scarlet Begonias [10:23]") == "Scarlet Begonias")
    }

    @Test func stripParenthesizedDuration() {
        #expect(SongTitleScrubber.scrub("Scarlet Begonias (10:23)") == "Scarlet Begonias")
    }

    @Test func inlineSegueTakesFirstSegment() {
        #expect(SongTitleScrubber.scrub("Scarlet > Fire") == "Scarlet")
    }

    @Test func inlineSegueWithTrackNumber() {
        #expect(SongTitleScrubber.scrub("05 Scarlet Begonias > Fire on the Mountain") == "Scarlet Begonias")
    }

    @Test func noChangeForCleanTitle() {
        #expect(SongTitleScrubber.scrub("Playing in the Band") == "Playing in the Band")
    }

    @Test func combinedTrackNumberAndTrailingSegue() {
        #expect(SongTitleScrubber.scrub("02 Scarlet Begonias >") == "Scarlet Begonias")
    }

    @Test func blankInputReturnsEmpty() {
        #expect(SongTitleScrubber.scrub("") == "")
        #expect(SongTitleScrubber.scrub("   ") == "")
    }

    @Test func normalizesExtraWhitespace() {
        #expect(SongTitleScrubber.scrub("  Scarlet   Begonias  ") == "Scarlet Begonias")
    }

    @Test func preservesParenthesizedNonDuration() {
        #expect(SongTitleScrubber.scrub("The Other One (jam)") == "The Other One (jam)")
    }

    // MARK: - matchesSetlistSong()

    @Test func exactMatchCaseInsensitive() {
        #expect(SongTitleScrubber.matchesSetlistSong("scarlet begonias", setlistSongName: "Scarlet Begonias"))
    }

    @Test func partialMatchScrubbedTitleContainedInSetlistName() {
        #expect(SongTitleScrubber.matchesSetlistSong("Scarlet", setlistSongName: "Scarlet Begonias"))
    }

    @Test func noMatch() {
        #expect(!SongTitleScrubber.matchesSetlistSong("Dark Star", setlistSongName: "Scarlet Begonias"))
    }

    @Test func blankInputsReturnFalse() {
        #expect(!SongTitleScrubber.matchesSetlistSong("", setlistSongName: "Scarlet Begonias"))
        #expect(!SongTitleScrubber.matchesSetlistSong("Scarlet", setlistSongName: ""))
    }
}
