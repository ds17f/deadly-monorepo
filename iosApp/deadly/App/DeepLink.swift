import Foundation

enum DeepLink: Equatable {
    case show(id: String, recordingId: String?, trackNumber: Int?)
    case collection(id: String)

    static func parse(_ url: URL) -> DeepLink? {
        let pathParts = url.pathComponents.filter { $0 != "/" }

        if url.scheme == "deadly" {
            // deadly://show/{showId}[/recording/{recordingId}[/track/{trackNumber}]]
            // deadly://collection/{collectionId}
            guard let host = url.host else { return nil }
            switch host {
            case "show":
                guard let showId = pathParts.first, !showId.isEmpty else { return nil }
                let recordingId = pathParts.count >= 3 && pathParts[1] == "recording"
                    ? pathParts[2] : nil
                let trackNumber = pathParts.count >= 5 && pathParts[3] == "track"
                    ? Int(pathParts[4]) : nil
                return .show(id: showId, recordingId: recordingId, trackNumber: trackNumber)
            case "collection":
                guard let collectionId = pathParts.first, !collectionId.isEmpty else { return nil }
                return .collection(id: collectionId)
            default:
                return nil
            }

        } else if url.scheme == "https",
                  let host = url.host,
                  host == "share.thedeadly.app" || host == "share.beta.thedeadly.app" {
            // https://share.thedeadly.app/show/{showId}[/recording/{recordingId}[/track/{trackNumber}]]
            // https://share.thedeadly.app/collection/{collectionId}
            guard pathParts.count >= 2 else { return nil }
            switch pathParts[0] {
            case "show", "shows":
                let showId = pathParts[1]
                let recordingId = pathParts.count >= 4 && pathParts[2] == "recording"
                    ? pathParts[3] : nil
                let trackNumber = pathParts.count >= 6 && pathParts[4] == "track"
                    ? Int(pathParts[5]) : nil
                return .show(id: showId, recordingId: recordingId, trackNumber: trackNumber)
            case "collection":
                return .collection(id: pathParts[1])
            default:
                return nil
            }
        }

        return nil
    }
}

extension DeepLink: Identifiable {
    var id: String {
        switch self {
        case .show(let id, let rid, let track):
            return "show-\(id)-\(rid ?? "")-\(track.map(String.init) ?? "")"
        case .collection(let id):
            return "collection-\(id)"
        }
    }
}
