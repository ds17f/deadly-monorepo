import Foundation

enum DeepLink: Equatable {
    case show(id: String, recordingId: String?)
    case collection(id: String)

    static func parse(_ url: URL) -> DeepLink? {
        let pathParts = url.pathComponents.filter { $0 != "/" }

        if url.scheme == "deadly" {
            // deadly://show/{showId}[/recording/{recordingId}]
            // deadly://collection/{collectionId}
            guard let host = url.host else { return nil }
            switch host {
            case "show":
                guard let showId = pathParts.first, !showId.isEmpty else { return nil }
                let recordingId = pathParts.count >= 3 && pathParts[1] == "recording"
                    ? pathParts[2] : nil
                return .show(id: showId, recordingId: recordingId)
            case "collection":
                guard let collectionId = pathParts.first, !collectionId.isEmpty else { return nil }
                return .collection(id: collectionId)
            default:
                return nil
            }

        } else if url.scheme == "https", url.host == "share.thedeadly.app" {
            // https://share.thedeadly.app/show/{showId}[/recording/{recordingId}]
            // https://share.thedeadly.app/collection/{collectionId}
            guard pathParts.count >= 2 else { return nil }
            switch pathParts[0] {
            case "show":
                let showId = pathParts[1]
                let recordingId = pathParts.count >= 4 && pathParts[2] == "recording"
                    ? pathParts[3] : nil
                return .show(id: showId, recordingId: recordingId)
            case "collection":
                return .collection(id: pathParts[1])
            default:
                return nil
            }
        }

        return nil
    }
}
