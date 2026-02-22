import Foundation
import Testing
@testable import deadly

@Suite("ArchiveMetadataClient Tests")
struct ArchiveMetadataClientTests {

    // MARK: - Helpers

    private func makeFilesJSON(files: [[String: Any]]) -> Data {
        let json: [String: Any] = ["files": files]
        return try! JSONSerialization.data(withJSONObject: json)
    }

    private func audioFile(
        name: String,
        format: String = "VBR MP3",
        track: Any? = nil,
        title: String? = nil,
        length: String? = nil
    ) -> [String: Any] {
        var file: [String: Any] = ["name": name, "format": format]
        if let t = track { file["track"] = t }
        if let ti = title { file["title"] = ti }
        if let l = length { file["length"] = l }
        return file
    }

    // MARK: - Parsing tests

    @Test("parseTracks returns only MP3 files, filters everything else")
    func filtersNonMp3() {
        let data = makeFilesJSON(files: [
            audioFile(name: "show.mp3", track: 1, title: "Song A"),
            ["name": "cover.jpg", "format": "JPEG"],
            ["name": "metadata.xml", "format": "Metadata"],
            audioFile(name: "show.flac", track: 2, title: "Song B"),
            audioFile(name: "show.ogg", track: 3, title: "Song C"),
        ])

        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: data)
        #expect(tracks.count == 1)
        #expect(tracks[0].name == "show.mp3")
    }

    @Test("parseTracks sorts by track number")
    func sortsByTrackNumber() {
        let data = makeFilesJSON(files: [
            audioFile(name: "d1t03.mp3", track: 3, title: "Song C"),
            audioFile(name: "d1t01.mp3", track: 1, title: "Song A"),
            audioFile(name: "d1t02.mp3", track: 2, title: "Song B"),
        ])

        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: data)
        #expect(tracks.count == 3)
        #expect(tracks[0].title == "Song A")
        #expect(tracks[1].title == "Song B")
        #expect(tracks[2].title == "Song C")
    }

    @Test("parseTracks handles polymorphic title field (array)")
    func polymorphicTitleArray() {
        let data = makeFilesJSON(files: [
            ["name": "show.mp3", "format": "VBR MP3", "track": "1", "title": ["Dark Star", "Extra"]],
        ])

        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: data)
        #expect(tracks.count == 1)
        #expect(tracks[0].title == "Dark Star")
    }

    @Test("parseTracks uses filename fallback when title absent")
    func titleFallbackFromFilename() {
        let data = makeFilesJSON(files: [
            ["name": "grateful_dead_1977_dark_star.mp3", "format": "VBR MP3", "track": "1"],
        ])

        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: data)
        #expect(tracks.count == 1)
        #expect(!tracks[0].title.isEmpty)
    }

    // MARK: - extractTitleFromFilename tests

    @Test("extractTitleFromFilename strips gd prefix and date")
    func extractTitleStripsPrefix() {
        let result = URLSessionArchiveMetadataClient.extractTitleFromFilename("gd77-05-08dark_star.mp3")
        #expect(!result.lowercased().hasPrefix("gd"))
        #expect(!result.isEmpty)
    }

    @Test("extractTitleFromFilename converts underscores to spaces")
    func extractTitleUnderscoresToSpaces() {
        let result = URLSessionArchiveMetadataClient.extractTitleFromFilename("grateful_dead_1977_dark_star.flac")
        #expect(!result.contains("_"))
        #expect(result.lowercased().contains("dark star"))
    }

    // MARK: - ArchiveTrack tests

    @Test("streamURL builds correct archive.org download URL")
    func streamURLBuildsCorrectly() {
        let track = ArchiveTrack(
            name: "gd77-05-08eaton-d1t01.mp3",
            title: "Minglewood Blues",
            trackNumber: 1,
            duration: "423.12",
            format: "VBR MP3",
            size: nil
        )

        let url = track.streamURL(recordingId: "gd77-05-08.sbd.hicks.4982.sbeok.shnf")
        #expect(url.absoluteString == "https://archive.org/download/gd77-05-08.sbd.hicks.4982.sbeok.shnf/gd77-05-08eaton-d1t01.mp3")
    }

    @Test("displayDuration formats seconds correctly")
    func displayDurationFormats() {
        let track = ArchiveTrack(
            name: "track.mp3",
            title: "Song",
            trackNumber: 1,
            duration: "423.12",
            format: "VBR MP3",
            size: nil
        )
        #expect(track.displayDuration == "7:03")
    }

    @Test("displayDuration returns nil for missing duration")
    func displayDurationNilWhenMissing() {
        let track = ArchiveTrack(
            name: "track.mp3",
            title: "Song",
            trackNumber: 1,
            duration: nil,
            format: "VBR MP3",
            size: nil
        )
        #expect(track.displayDuration == nil)
    }
}
