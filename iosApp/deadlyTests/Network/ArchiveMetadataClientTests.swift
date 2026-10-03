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

    // MARK: - Storage server fallback

    private func makeItemJSON(extra: [String: Any], files: [[String: Any]]? = nil) -> Data {
        var json: [String: Any] = [
            "files": files ?? [audioFile(name: "d1t01.mp3", track: 1, title: "Song A")],
        ]
        for (key, value) in extra { json[key] = value }
        return try! JSONSerialization.data(withJSONObject: json)
    }

    @Test("parseTracks reads workable_servers and dir onto every track")
    func readsWorkableServers() {
        let data = makeItemJSON(
            extra: [
                "workable_servers": ["ia800504.us.archive.org", "ia600504.us.archive.org"],
                "dir": "/33/items/gd77-10-28",
                "d1": "ignored.us.archive.org",
            ],
            files: [
                audioFile(name: "d1t01.mp3", track: 1, title: "Song A"),
                audioFile(name: "d1t02.mp3", track: 2, title: "Song B"),
            ]
        )

        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: data)
        #expect(tracks.count == 2)
        for track in tracks {
            #expect(track.fallbackServers == ["ia800504.us.archive.org", "ia600504.us.archive.org"])
            #expect(track.itemDir == "/33/items/gd77-10-28")
        }
    }

    @Test("parseTracks falls back to d1, d2, server, deduplicated, when workable_servers is missing")
    func fallsBackToD1D2Server() {
        let data = makeItemJSON(extra: [
            "d1": "ia800001.us.archive.org",
            "d2": "ia600001.us.archive.org",
            "server": "ia800001.us.archive.org",
            "dir": "/1/items/x",
        ])

        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: data)
        #expect(tracks[0].fallbackServers == ["ia800001.us.archive.org", "ia600001.us.archive.org"])
    }

    @Test("parseTracks leaves fallback fields nil when the item has no server info")
    func noServerInfo() {
        let tracks = URLSessionArchiveMetadataClient.parseTracks(from: makeItemJSON(extra: [:]))
        #expect(tracks[0].fallbackServers == nil)
        #expect(tracks[0].itemDir == nil)
        #expect(tracks[0].fallbackStreamURLs().isEmpty)
    }

    @Test("fallbackStreamURLs builds https://server/dir/file with percent-encoding")
    func fallbackStreamURLsEncoding() {
        let track = ArchiveTrack(
            name: "gd77 10-28d1t01.mp3",
            title: "Song",
            trackNumber: 1,
            duration: nil,
            format: "VBR MP3",
            size: nil,
            fallbackServers: ["ia800504.us.archive.org", "ia600504.us.archive.org"],
            itemDir: "/33/items/gd77-10-28"
        )

        let urls = track.fallbackStreamURLs()
        #expect(urls.map(\.absoluteString) == [
            "https://ia800504.us.archive.org/33/items/gd77-10-28/gd77%2010-28d1t01.mp3",
            "https://ia600504.us.archive.org/33/items/gd77-10-28/gd77%2010-28d1t01.mp3",
        ])
    }

    @Test("fallbackStreamURLs is empty without a directory")
    func fallbackStreamURLsNeedsDir() {
        let track = ArchiveTrack(
            name: "a.mp3", title: "A", trackNumber: 1, duration: nil, format: "VBR MP3", size: nil,
            fallbackServers: ["ia800504.us.archive.org"], itemDir: nil
        )
        #expect(track.fallbackStreamURLs().isEmpty)
    }

    @Test("cached ArchiveTrack JSON written before the fallback fields still decodes")
    func oldCacheDecodes() throws {
        let old = """
        [{"name":"d1t01.mp3","title":"Song A","trackNumber":1,"duration":"300","format":"VBR MP3","size":"123"}]
        """.data(using: .utf8)!

        let tracks = try JSONDecoder().decode([ArchiveTrack].self, from: old)
        #expect(tracks.count == 1)
        #expect(tracks[0].fallbackServers == nil)
        #expect(tracks[0].itemDir == nil)
        #expect(tracks[0].fallbackStreamURLs().isEmpty)
    }

    @Test("fallback fields survive an encode/decode round trip")
    func fallbackRoundTrip() throws {
        let track = ArchiveTrack(
            name: "a.mp3", title: "A", trackNumber: 1, duration: nil, format: "VBR MP3", size: nil,
            fallbackServers: ["s.archive.org"], itemDir: "/1/items/x"
        )
        let decoded = try JSONDecoder().decode([ArchiveTrack].self, from: JSONEncoder().encode([track]))
        #expect(decoded == [track])
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
