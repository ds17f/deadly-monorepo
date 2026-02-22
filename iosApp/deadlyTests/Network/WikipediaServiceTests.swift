import Testing
@testable import deadly

@Suite("WikipediaService JSON parsing")
struct WikipediaServiceTests {

    // MARK: - parseSummaryJSON

    @Test("extracts extract field from summary JSON")
    func parsesSummaryExtract() throws {
        let json = #"{"type":"standard","title":"Barton Hall","extract":"Barton Hall is a historic venue at Cornell University."}"#
        let data = try #require(json.data(using: .utf8))
        let result = URLSessionWikipediaService.parseSummaryJSON(data)
        #expect(result == "Barton Hall is a historic venue at Cornell University.")
    }

    @Test("returns nil when extract is blank")
    func nilOnBlankExtract() throws {
        let json = #"{"title":"Foo","extract":"   "}"#
        let data = try #require(json.data(using: .utf8))
        #expect(URLSessionWikipediaService.parseSummaryJSON(data) == nil)
    }

    @Test("returns nil when extract key missing")
    func nilWhenNoExtract() throws {
        let json = #"{"title":"Foo","description":"Something"}"#
        let data = try #require(json.data(using: .utf8))
        #expect(URLSessionWikipediaService.parseSummaryJSON(data) == nil)
    }

    @Test("returns nil for invalid JSON")
    func nilForInvalidJSON() throws {
        let data = try #require("not json".data(using: .utf8))
        #expect(URLSessionWikipediaService.parseSummaryJSON(data) == nil)
    }

    // MARK: - parseOpenSearchTitles

    @Test("returns first title from opensearch response")
    func parsesFirstTitle() throws {
        // opensearch format: ["query", ["Title1", "Title2"], [...], [...]]
        let json = #"["Barton Hall",["Barton Hall, Cornell University","Barton Hall (disambiguation)"],["",""],["https://en.wikipedia.org/wiki/Barton_Hall,_Cornell_University",""]]"#
        let data = try #require(json.data(using: .utf8))
        let result = URLSessionWikipediaService.parseOpenSearchTitles(data)
        #expect(result == "Barton Hall, Cornell University")
    }

    @Test("returns nil when titles array is empty")
    func nilOnEmptyTitles() throws {
        let json = #"["query",[],[],[]]"#
        let data = try #require(json.data(using: .utf8))
        #expect(URLSessionWikipediaService.parseOpenSearchTitles(data) == nil)
    }

    @Test("returns nil for malformed response")
    func nilOnMalformed() throws {
        let data = try #require("{}".data(using: .utf8))
        #expect(URLSessionWikipediaService.parseOpenSearchTitles(data) == nil)
    }

    @Test("returns nil when array has fewer than 2 elements")
    func nilOnShortArray() throws {
        let json = #"["query"]"#
        let data = try #require(json.data(using: .utf8))
        #expect(URLSessionWikipediaService.parseOpenSearchTitles(data) == nil)
    }
}
