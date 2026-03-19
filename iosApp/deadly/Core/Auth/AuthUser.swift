import Foundation

struct AuthUser: Codable, Sendable {
    let id: String
    let email: String?
    let name: String?
}
