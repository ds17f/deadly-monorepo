import Testing
@testable import deadly

@Suite("DateFormatting.formatShowDate()")
struct DateFormattingTests {

    // MARK: - Long style (default)

    @Test func longStyleCornell77() {
        #expect(DateFormatting.formatShowDate("1977-05-08") == "May 8, 1977")
    }

    @Test func longStyleJanuary() {
        #expect(DateFormatting.formatShowDate("1969-01-01") == "January 1, 1969")
    }

    @Test func longStyleDecember() {
        #expect(DateFormatting.formatShowDate("1995-12-31") == "December 31, 1995")
    }

    @Test func longStyleAllMonths() {
        let expected = [
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        ]
        for (i, name) in expected.enumerated() {
            let month = String(format: "%02d", i + 1)
            let result = DateFormatting.formatShowDate("1977-\(month)-15")
            #expect(result == "\(name) 15, 1977")
        }
    }

    // MARK: - Short style

    @Test func shortStyleCornell77() {
        #expect(DateFormatting.formatShowDate("1977-05-08", style: .short) == "May 8, 1977")
    }

    @Test func shortStyleJuly() {
        #expect(DateFormatting.formatShowDate("1976-07-17", style: .short) == "Jul 17, 1976")
    }

    @Test func shortStyleAllMonths() {
        let expected = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
        for (i, abbr) in expected.enumerated() {
            let month = String(format: "%02d", i + 1)
            let result = DateFormatting.formatShowDate("1977-\(month)-15", style: .short)
            #expect(result == "\(abbr) 15, 1977")
        }
    }

    // MARK: - Invalid input

    @Test func invalidDatePassesThrough() {
        #expect(DateFormatting.formatShowDate("not-a-date") == "not-a-date")
        #expect(DateFormatting.formatShowDate("") == "")
        #expect(DateFormatting.formatShowDate("1977-13-01") == "1977-13-01")  // month out of range
    }

    @Test func partialDatePassesThrough() {
        #expect(DateFormatting.formatShowDate("1977-05") == "1977-05")
    }
}
