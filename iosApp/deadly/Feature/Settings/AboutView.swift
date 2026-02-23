import SwiftUI

/// About screen with app info, mission statement, and legal/streaming policies.
/// Matches the Android AboutScreen content.
struct AboutView: View {
    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }

    var body: some View {
        List {
            // App Header
            Section {
                VStack(spacing: 4) {
                    Text("Deadly")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundStyle(.primary)
                    Text("Version \(appVersion)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .listRowBackground(Color.clear)
            }

            // Support the Archive
            Section("Support the Archive") {
                Text("The Internet Archive is the backbone of this app. Their infrastructure hosts and streams every recording you hear. Please consider donating directly to help cover their hosting and bandwidth costs.")
                    .font(.subheadline)

                Link("Donate to Internet Archive", destination: URL(string: "https://archive.org/donate/")!)
                    .font(.subheadline.weight(.medium))
            }

            // Our Mission
            Section("Our Mission") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("We built this app for one simple reason: we want to encourage Deadheads — old and new — to engage with, enjoy, and share the music of the Grateful Dead.")
                    Text("The goal is to make listening to live shows as easy and enjoyable as possible in a modern streaming experience. We have deep respect for the spirit of the band and the long-standing belief that this music is meant to be shared — freely, non-commercially, and in community.")
                    Text("This app is completely open source. Anyone can inspect the code, contribute improvements, or build upon it.")
                    Text("No money is made from streaming music through this app. It exists because one Deadhead wanted a modern way to listen to his favorite band.")
                }
                .font(.subheadline)
            }

            // Streaming & Recording Access Policy
            Section("Streaming & Recording Access Policy") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("This app streams live recordings from the Internet Archive, a non-profit digital library that hosts recordings in accordance with the Grateful Dead's long-standing non-commercial taping tradition and current rights-holder policies.")
                    Text("This app is independent and not affiliated with the band, its members, or its management.")
                }
                .font(.subheadline)
            }

            // The Band's Taping & Sharing Tradition
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
                .font(.subheadline)
            }

            // Internet Archive Collection Policy
            Section("Internet Archive Collection Policy") {
                VStack(alignment: .leading, spacing: 12) {
                    Text("The Internet Archive hosts the Grateful Dead collection under specific access rules set in coordination with rights holders. Availability of recordings — including whether they are stream-only or downloadable — is determined by the Archive and applicable rights holders.")

                    Link("Grateful Dead Collection Help Page",
                         destination: URL(string: "https://archivesupport.zendesk.com/hc/en-us/articles/360004715891-The-Grateful-Dead-Collection")!)
                }
                .font(.subheadline)
            }

            // How This App Handles Streaming
            Section("How This App Handles Streaming") {
                VStack(alignment: .leading, spacing: 8) {
                    BulletRow("All recordings are streamed directly from the Internet Archive.")
                    BulletRow("This app does not host, modify, or redistribute audio files.")
                    BulletRow("Recording availability is subject to change based on Archive or rights-holder decisions.")
                }
            }

            // Offline Listening
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
                .font(.subheadline)
            }

            // Official Commercial Releases
            Section("Official Commercial Releases") {
                Text("Commercially released recordings — studio albums, official live releases, box sets, and similar material — are protected by copyright and should be accessed through authorized services.")
                    .font(.subheadline)
            }

            // Respect for Artists & Rights Holders
            Section("Respect for Artists & Rights Holders") {
                Text("This app exists to support listening, exploration, and historical appreciation of the Grateful Dead's live performances — in the spirit of their taping tradition — while respecting modern copyright law and the policies of the Internet Archive and rights holders.")
                    .font(.subheadline)
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - BulletRow

private struct BulletRow: View {
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
        .font(.subheadline)
    }
}
