import Testing
@testable import deadly

@Suite("Lineup.parse()")
struct LineupTests {

    // MARK: - Array format

    @Test func parseArrayFormat() throws {
        let json = """
        [
            {"name": "Jerry Garcia", "instruments": "guitar, vocals"},
            {"name": "Phil Lesh", "instruments": "bass, vocals"}
        ]
        """
        let lineup = try #require(Lineup.parse(json: json, status: "classic"))
        #expect(lineup.members.count == 2)
        #expect(lineup.members[0].name == "Jerry Garcia")
        #expect(lineup.members[0].instruments == "guitar, vocals")
        #expect(lineup.members[1].name == "Phil Lesh")
        #expect(lineup.status == "classic")
    }

    // MARK: - Wrapped format

    @Test func parseWrappedFormat() throws {
        let json = """
        {"members": [
            {"name": "Bob Weir", "instruments": "guitar, vocals"},
            {"name": "Bill Kreutzmann", "instruments": "drums"}
        ]}
        """
        let lineup = try #require(Lineup.parse(json: json, status: "classic"))
        #expect(lineup.members.count == 2)
        #expect(lineup.members[0].name == "Bob Weir")
        #expect(lineup.members[1].instruments == "drums")
    }

    // MARK: - Missing instruments field

    @Test func missingInstrumentsFallsBackToEmpty() throws {
        let json = #"[{"name": "Mickey Hart"}]"#
        let lineup = try #require(Lineup.parse(json: json, status: "classic"))
        #expect(lineup.members[0].instruments == "")
    }

    // MARK: - Nil / empty / malformed

    @Test func nilJSONReturnsNil() {
        #expect(Lineup.parse(json: nil, status: "classic") == nil)
    }

    @Test func nilStatusReturnsNil() {
        #expect(Lineup.parse(json: "[]", status: nil) == nil)
    }

    @Test func emptyJSONReturnsNil() {
        #expect(Lineup.parse(json: "", status: "classic") == nil)
    }

    @Test func malformedJSONReturnsNil() {
        #expect(Lineup.parse(json: "not json", status: "classic") == nil)
    }

    @Test func missingMembersKeyReturnsNil() {
        // Object with no "members" key — not an array and not wrapped format
        #expect(Lineup.parse(json: "{\"other\": []}", status: "classic") == nil)
    }

    // MARK: - Raw preservation

    @Test func rawJSONPreserved() throws {
        let json = #"[{"name": "Pigpen", "instruments": "keyboards, vocals, harmonica"}]"#
        let lineup = try #require(Lineup.parse(json: json, status: "early"))
        #expect(lineup.raw == json)
    }
}
