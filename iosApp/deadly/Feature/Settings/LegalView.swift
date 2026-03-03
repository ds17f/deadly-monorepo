import SwiftUI

struct LegalView: View {
    var body: some View {
        List {
            Section("Streaming & Recording Access Policy") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("This app streams live recordings from the Internet Archive, a non-profit digital library that hosts recordings in accordance with the Grateful Dead's long-standing non-commercial taping tradition and current rights-holder policies.")
                    Text("This app is independent and not affiliated with the band, its members, or its management.")
                }
                .font(.body)
            }

            Section("The Band's Taping & Sharing Tradition") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("The Grateful Dead historically permitted audience members to record live performances for personal, non-commercial use and free trading. This policy helped create one of the most active live-music communities in history.")

                    Text("Historical policy statements:")
                        .fontWeight(.medium)

                    Link("Grateful Dead Statement to Digital Archive Operators",
                         destination: URL(string: "https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html")!)

                    Link("WIRED: Everyone Is Grateful Again",
                         destination: URL(string: "https://www.wired.com/2005/12/everyone-is-grateful-again/")!)
                }
                .font(.body)
            }

            Section("Internet Archive Collection Policy") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("The Internet Archive hosts the Grateful Dead collection under specific access rules set in coordination with rights holders. Availability of recordings — including whether they are stream-only or downloadable — is determined by the Archive and applicable rights holders.")

                    Link("Grateful Dead Collection Help Page",
                         destination: URL(string: "https://archivesupport.zendesk.com/hc/en-us/articles/360004715891-The-Grateful-Dead-Collection")!)
                }
                .font(.body)
            }

            Section("How This App Handles Streaming") {
                VStack(alignment: .leading, spacing: 8) {
                    BulletRow("All recordings are streamed directly from the Internet Archive.")
                    BulletRow("This app does not host, modify, or redistribute audio files.")
                    BulletRow("Recording availability is subject to change based on Archive or rights-holder decisions.")
                }
            }

            Section("Offline Listening") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Where technically permitted by the Internet Archive, this app may allow recordings to be temporarily downloaded for in-app offline listening only.")

                    VStack(alignment: .leading, spacing: 8) {
                        BulletRow("Downloads are stored within the app.")
                        BulletRow("They are not provided as exported audio files.")
                        BulletRow("They are intended solely for personal, non-commercial listening.")
                    }

                    Text("Users are responsible for complying with applicable copyright and Archive usage policies.")
                }
                .font(.body)
            }

            Section("Official Commercial Releases") {
                Text("Commercially released recordings — studio albums, official live releases, box sets, and similar material — are protected by copyright and should be accessed through authorized services.")
                    .font(.body)
            }

            Section("Respect for Artists & Rights Holders") {
                Text("This app exists to support listening, exploration, and historical appreciation of the Grateful Dead's live performances — in the spirit of their taping tradition — while respecting modern copyright law and the policies of the Internet Archive and rights holders.")
                    .font(.body)
            }
        }
        .navigationTitle("Legal & Policies")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - BulletRow

struct BulletRow: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text("\u{2022}")
                .foregroundStyle(.secondary)
            Text(text)
        }
        .font(.body)
    }
}
