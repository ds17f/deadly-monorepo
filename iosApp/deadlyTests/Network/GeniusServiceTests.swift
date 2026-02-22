import Testing
@testable import deadly

@Suite("GeniusService HTML parsing")
struct GeniusServiceTests {

    // MARK: - stripExcludeBlocks

    @Test("removes a single exclude block")
    func stripsExcludeBlock() {
        let html = #"""
        <div data-lyrics-container="true">
        <div data-exclude-from-selection="true"><span>Contributors: 42</span></div>
        <a>Some lyric line</a>
        </div>
        """#

        let result = URLSessionGeniusService.stripExcludeBlocks(html)
        #expect(!result.contains("Contributors"))
        #expect(result.contains("Some lyric line"))
    }

    @Test("preserves content outside exclude blocks")
    func preservesOutsideContent() {
        let html = "<p>Before</p><div data-exclude-from-selection=\"true\"><b>Hidden</b></div><p>After</p>"
        let result = URLSessionGeniusService.stripExcludeBlocks(html)
        #expect(result.contains("Before"))
        #expect(result.contains("After"))
        #expect(!result.contains("Hidden"))
    }

    @Test("handles nested divs inside exclude block")
    func handlesNestedDivs() {
        let html = #"Start<div data-exclude-from-selection="true"><div><div>Deep</div></div></div>End"#
        let result = URLSessionGeniusService.stripExcludeBlocks(html)
        #expect(result.contains("Start"))
        #expect(result.contains("End"))
        #expect(!result.contains("Deep"))
    }

    @Test("no change when no exclude blocks present")
    func noChange() {
        let html = "<div><p>Lyrics here</p></div>"
        let result = URLSessionGeniusService.stripExcludeBlocks(html)
        #expect(result == html)
    }

    // MARK: - extractDivContent

    @Test("extracts flat div content")
    func extractsFlatContent() {
        let html = "<div>Hello world</div><div>Other</div>"
        // startIndex points right after the first <div>
        let start = html.index(html.startIndex, offsetBy: 5)
        let result = URLSessionGeniusService.extractDivContent(from: html, startIndex: start)
        #expect(result == "Hello world")
    }

    @Test("extracts content with nested div")
    func extractsWithNested() {
        let html = "<div>Line 1<div>Nested</div>Line 2</div>Tail"
        let start = html.index(html.startIndex, offsetBy: 5)
        let result = URLSessionGeniusService.extractDivContent(from: html, startIndex: start)
        #expect(result.contains("Line 1"))
        #expect(result.contains("Nested"))
        #expect(result.contains("Line 2"))
        #expect(!result.contains("Tail"))
    }

    // MARK: - decodeHTMLEntities

    @Test("decodes common HTML entities")
    func decodesEntities() {
        let input = "Rock &amp; Roll &lt;live&gt; &quot;Tonight&quot; it&#39;s &#x27;magic&#x27;"
        let expected = #"Rock & Roll <live> "Tonight" it's 'magic'"#
        #expect(URLSessionGeniusService.decodeHTMLEntities(input) == expected)
    }

    @Test("no-op when no entities present")
    func noopWithoutEntities() {
        let input = "Just plain text"
        #expect(URLSessionGeniusService.decodeHTMLEntities(input) == input)
    }
}
