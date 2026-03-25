import Foundation

struct Artist: Identifiable, Codable, Sendable, Hashable {
    let id: String
    let name: String
    let collection: String

    var imageUrl: String { "https://archive.org/services/img/\(collection)" }

    /// Whether this artist has rich local data (from the data pipeline) vs. live IA API only.
    var hasLocalData: Bool { collection == "GratefulDead" }
}

// MARK: - Curated Artist List

extension Artist {
    /// Curated artists from the Internet Archive's Live Music Archive.
    static let all: [Artist] = [
        Artist(id: "grateful-dead", name: "Grateful Dead", collection: "GratefulDead"),
        Artist(id: "ratdog", name: "RatDog", collection: "Ratdog"),
        Artist(id: "phil-lesh-and-friends", name: "Phil Lesh & Friends", collection: "PhilLeshandFriends"),
        Artist(id: "furthur", name: "Furthur", collection: "Furthur"),
        Artist(id: "the-other-ones", name: "The Other Ones", collection: "TheOtherOnes"),
        Artist(id: "the-dead", name: "The Dead", collection: "TheDead"),
        Artist(id: "mickey-hart-band", name: "Mickey Hart Band", collection: "MickeyHartBand"),
        Artist(id: "billy-and-the-kids", name: "Billy & the Kids", collection: "BillyAndTheKids"),
        Artist(id: "oteil-and-friends", name: "Oteil & Friends", collection: "OteilAndFriends"),
        Artist(id: "bob-weir", name: "Bob Weir", collection: "BobWeir"),
        Artist(id: "robert-hunter", name: "Robert Hunter", collection: "RobertHunter"),
        Artist(id: "tom-constanten", name: "Tom Constanten", collection: "TomConstanten"),
        Artist(id: "jrad", name: "Joe Russo's Almost Dead", collection: "JoeRussosAlmostDead"),
        Artist(id: "dso", name: "Dark Star Orchestra", collection: "DarkStarOrchestra"),
        Artist(id: "zen-tricksters", name: "The Zen Tricksters", collection: "ZenTricksters"),
        Artist(id: "cubensis", name: "Cubensis", collection: "Cubensis"),
        Artist(id: "forgotten-space", name: "Forgotten Space", collection: "ForgottenSpace"),
        Artist(id: "shakedown-street", name: "Shakedown Street", collection: "ShakedownStreet"),
        Artist(id: "scarlet-begonias", name: "Scarlet Begonias", collection: "ScarletBegoniaz"),
        Artist(id: "terrapin-flyer", name: "Terrapin Flyer", collection: "TerrapinFlyer"),
    ]

    /// All artists except Grateful Dead (which has its own rich experience).
    static let browsable: [Artist] = all.filter { !$0.hasLocalData }
}
