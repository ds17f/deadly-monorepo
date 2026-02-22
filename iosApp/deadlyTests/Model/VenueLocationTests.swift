import Testing
@testable import deadly

@Suite("Venue.displayLocation and Location.fromRaw()")
struct VenueLocationTests {

    // MARK: - Venue.displayLocation

    @Test func usaVenueOmitsCountry() {
        let venue = Venue(name: "Barton Hall", city: "Ithaca", state: "NY", country: "USA")
        #expect(venue.displayLocation == "Ithaca, NY")
    }

    @Test func internationalVenueIncludesCountry() {
        let venue = Venue(name: "Wembley Arena", city: "London", state: nil, country: "UK")
        #expect(venue.displayLocation == "London, UK")
    }

    @Test func venueWithAllFields() {
        let venue = Venue(name: "Olympiahalle", city: "Munich", state: "Bavaria", country: "Germany")
        #expect(venue.displayLocation == "Munich, Bavaria, Germany")
    }

    @Test func venueNoCityOrState() {
        let venue = Venue(name: "Unknown Venue", city: nil, state: nil, country: "USA")
        #expect(venue.displayLocation == "")
    }

    @Test func venueNoCityOrStateInternational() {
        let venue = Venue(name: "Unknown Venue", city: nil, state: nil, country: "UK")
        #expect(venue.displayLocation == "UK")
    }

    // MARK: - Location.fromRaw()

    @Test func fromRawWithExplicitDisplayText() {
        let loc = Location.fromRaw("Cornell University, Ithaca, NY", city: "Ithaca", state: "NY")
        #expect(loc.displayText == "Cornell University, Ithaca, NY")
        #expect(loc.city == "Ithaca")
        #expect(loc.state == "NY")
    }

    @Test func fromRawNilFallsBackToCityState() {
        let loc = Location.fromRaw(nil, city: "San Francisco", state: "CA")
        #expect(loc.displayText == "San Francisco, CA")
    }

    @Test func fromRawNilWithNoCityOrStateFallsBackToUnknown() {
        let loc = Location.fromRaw(nil, city: nil, state: nil)
        #expect(loc.displayText == "Unknown Location")
    }

    @Test func fromRawNilWithOnlyCity() {
        let loc = Location.fromRaw(nil, city: "Portland", state: nil)
        #expect(loc.displayText == "Portland")
    }

    @Test func fromRawNilWithOnlyState() {
        let loc = Location.fromRaw(nil, city: nil, state: "OR")
        #expect(loc.displayText == "OR")
    }
}
