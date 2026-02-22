import Foundation

struct SetlistSong: Codable, Sendable, Equatable {
    let name: String
    let position: Int
    let hasSegue: Bool
    let segueSymbol: String?

    init(name: String, position: Int, hasSegue: Bool = false, segueSymbol: String? = nil) {
        self.name = name
        self.position = position
        self.hasSegue = hasSegue
        self.segueSymbol = segueSymbol
    }

    var displayName: String {
        if hasSegue, let symbol = segueSymbol {
            return "\(name) \(symbol)"
        }
        return name
    }
}

struct SetlistSet: Codable, Sendable, Equatable {
    let name: String
    let songs: [SetlistSong]
}

struct Setlist: Codable, Sendable, Equatable {
    let status: String
    let sets: [SetlistSet]
    let raw: String?
    let date: String?
    let venue: String?

    init(status: String, sets: [SetlistSet], raw: String?,
         date: String? = nil, venue: String? = nil) {
        self.status = status
        self.sets = sets
        self.raw = raw
        self.date = date
        self.venue = venue
    }

    /// Parse setlist from JSON string.
    /// Expected format: `[{"set_name": "Set 1", "songs": [{"name": "...", "segue_into_next": true}]}]`
    static func parse(json: String?, status: String?) -> Setlist? {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let status, !status.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        guard let data = json.data(using: .utf8),
              let jsonArray = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }

        var sets: [SetlistSet] = []
        for setObj in jsonArray {
            guard let setName = setObj["set_name"] as? String,
                  let songsArray = setObj["songs"] as? [[String: Any]] else { continue }
            var songs: [SetlistSong] = []
            for (j, songObj) in songsArray.enumerated() {
                guard let songName = songObj["name"] as? String else { continue }
                let hasSegue = songObj["segue_into_next"] as? Bool ?? false
                songs.append(SetlistSong(
                    name: songName,
                    position: j + 1,
                    hasSegue: hasSegue,
                    segueSymbol: hasSegue ? ">" : nil
                ))
            }
            sets.append(SetlistSet(name: setName, songs: songs))
        }

        return Setlist(status: status, sets: sets, raw: json)
    }
}
