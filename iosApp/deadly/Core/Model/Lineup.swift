import Foundation

struct LineupMember: Codable, Sendable, Equatable {
    let name: String
    let instruments: String
}

struct Lineup: Codable, Sendable, Equatable {
    let status: String
    let members: [LineupMember]
    let raw: String?

    /// Parse lineup from JSON string.
    /// Handles both array format `[{...}]` and wrapped format `{"members":[{...}]}`.
    static func parse(json: String?, status: String?) -> Lineup? {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let status, !status.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        guard let data = json.data(using: .utf8) else { return nil }

        let rawObject = try? JSONSerialization.jsonObject(with: data)
        let membersArray: [[String: Any]]

        if let arr = rawObject as? [[String: Any]] {
            membersArray = arr
        } else if let obj = rawObject as? [String: Any],
                  let arr = obj["members"] as? [[String: Any]] {
            membersArray = arr
        } else {
            return nil
        }

        let members = membersArray.compactMap { obj -> LineupMember? in
            guard let name = obj["name"] as? String else { return nil }
            return LineupMember(name: name, instruments: obj["instruments"] as? String ?? "")
        }

        return Lineup(status: status, members: members, raw: json)
    }
}
