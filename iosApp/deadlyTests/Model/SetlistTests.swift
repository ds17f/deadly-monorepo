import Testing
@testable import deadly

@Suite("Setlist.parse()")
struct SetlistTests {

    // MARK: - Valid JSON

    @Test func parseValidJSON() throws {
        let json = """
        [
            {"set_name": "Set 1", "songs": [
                {"name": "Scarlet Begonias", "segue_into_next": true},
                {"name": "Fire on the Mountain", "segue_into_next": false}
            ]},
            {"set_name": "Set 2", "songs": [
                {"name": "Dark Star"}
            ]}
        ]
        """
        let setlist = try #require(Setlist.parse(json: json, status: "complete"))
        #expect(setlist.sets.count == 2)
        #expect(setlist.sets[0].name == "Set 1")
        #expect(setlist.sets[0].songs.count == 2)
        #expect(setlist.sets[0].songs[0].name == "Scarlet Begonias")
        #expect(setlist.sets[0].songs[0].hasSegue == true)
        #expect(setlist.sets[0].songs[0].segueSymbol == ">")
        #expect(setlist.sets[0].songs[1].hasSegue == false)
        #expect(setlist.sets[1].songs[0].name == "Dark Star")
        #expect(setlist.status == "complete")
    }

    @Test func parseAssignsSongPositions() throws {
        let json = """
        [{"set_name": "Set 1", "songs": [
            {"name": "Song A"},
            {"name": "Song B"},
            {"name": "Song C"}
        ]}]
        """
        let setlist = try #require(Setlist.parse(json: json, status: "complete"))
        let songs = setlist.sets[0].songs
        #expect(songs[0].position == 1)
        #expect(songs[1].position == 2)
        #expect(songs[2].position == 3)
    }

    // MARK: - Segue display

    @Test func segueDisplayName() {
        let withSegue = SetlistSong(name: "Scarlet Begonias", position: 1, hasSegue: true, segueSymbol: ">")
        #expect(withSegue.displayName == "Scarlet Begonias >")

        let noSegue = SetlistSong(name: "Dark Star", position: 2, hasSegue: false)
        #expect(noSegue.displayName == "Dark Star")
    }

    // MARK: - Nil / empty / malformed inputs

    @Test func nilJSONReturnsNil() {
        #expect(Setlist.parse(json: nil, status: "complete") == nil)
    }

    @Test func nilStatusReturnsNil() {
        #expect(Setlist.parse(json: "[]", status: nil) == nil)
    }

    @Test func emptyJSONReturnsNil() {
        #expect(Setlist.parse(json: "", status: "complete") == nil)
        #expect(Setlist.parse(json: "   ", status: "complete") == nil)
    }

    @Test func malformedJSONReturnsNil() {
        #expect(Setlist.parse(json: "not json at all", status: "complete") == nil)
        #expect(Setlist.parse(json: "{}", status: "complete") == nil)
    }

    @Test func emptyArrayProducesEmptySets() throws {
        let setlist = try #require(Setlist.parse(json: "[]", status: "partial"))
        #expect(setlist.sets.isEmpty)
    }

    // MARK: - Raw preservation

    @Test func rawJSONPreserved() throws {
        let json = """
        [{"set_name": "Set 1", "songs": [{"name": "Truckin'"}]}]
        """
        let setlist = try #require(Setlist.parse(json: json, status: "complete"))
        #expect(setlist.raw == json)
    }
}
