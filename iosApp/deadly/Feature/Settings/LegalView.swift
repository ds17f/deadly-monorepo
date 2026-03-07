import SwiftUI

struct LegalView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 36) {

                ArticleSection("Streaming & Recording Access Policy") {
                    Text("This app streams live recordings from the Internet Archive, a non-profit digital library that hosts recordings in accordance with the Grateful Dead's long-standing non-commercial taping tradition and current rights-holder policies.")
                    Text("This app is independent and not affiliated with the band, its members, or its management.")
                }

                ArticleSection("The Band's Taping & Sharing Tradition") {
                    Text("The Grateful Dead historically permitted audience members to record live performances for personal, non-commercial use and free trading. This policy helped create one of the most active live-music communities in history.")
                    Text("Historical policy statements:")
                        .fontWeight(.medium)
                    ArticleLink("Grateful Dead Statement to Digital Archive Operators",
                                url: "https://web.archive.org/web/20051124082136/http://www.sugarmegs.org/purpose.html")
                    ArticleLink("WIRED: Everyone Is Grateful Again",
                                url: "https://www.wired.com/2005/12/everyone-is-grateful-again/")
                }

                ArticleSection("Internet Archive Collection Policy") {
                    Text("The Internet Archive hosts the Grateful Dead collection under specific access rules set in coordination with rights holders. Availability of recordings — including whether they are stream-only or downloadable — is determined by the Archive and applicable rights holders.")
                    ArticleLink("Grateful Dead Collection Help Page",
                                url: "https://archivesupport.zendesk.com/hc/en-us/articles/360004715891-The-Grateful-Dead-Collection")
                }

                ArticleSection("How This App Handles Streaming") {
                    BulletRow("All recordings are streamed directly from the Internet Archive.")
                    BulletRow("This app does not host, modify, or redistribute audio files.")
                    BulletRow("Recording availability is subject to change based on Archive or rights-holder decisions.")
                }

                ArticleSection("Offline Listening") {
                    Text("Where technically permitted by the Internet Archive, this app may allow recordings to be temporarily downloaded for in-app offline listening only.")
                    BulletRow("Downloads are stored within the app.")
                    BulletRow("They are not provided as exported audio files.")
                    BulletRow("They are intended solely for personal, non-commercial listening.")
                    Text("Users are responsible for complying with applicable copyright and Archive usage policies.")
                }

                ArticleSection("Official Commercial Releases") {
                    Text("Commercially released recordings — studio albums, official live releases, box sets, and similar material — are protected by copyright and should be accessed through authorized services.")
                }

                ArticleSection("Respect for Artists & Rights Holders") {
                    Text("This app exists to support listening, exploration, and historical appreciation of the Grateful Dead's live performances — in the spirit of their taping tradition — while respecting modern copyright law and the policies of the Internet Archive and rights holders.")
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .navigationTitle("Legal & Policies")
        .navigationBarTitleDisplayMode(.large)
    }
}

// MARK: - ArticleSection

private struct ArticleSection<Content: View>: View {
    let title: String
    let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundStyle(DeadlyColors.primary)
            Rectangle()
                .fill(DeadlyColors.primary.opacity(0.25))
                .frame(height: 1)
            VStack(alignment: .leading, spacing: 10) {
                content
            }
            .font(.body)
            .lineSpacing(4)
            .foregroundStyle(.primary)
        }
    }
}

// MARK: - ArticleLink

private struct ArticleLink: View {
    let label: String
    let url: String

    init(_ label: String, url: String) {
        self.label = label
        self.url = url
    }

    var body: some View {
        Link(destination: URL(string: url)!) {
            HStack(spacing: 4) {
                Text(label)
                    .multilineTextAlignment(.leading)
                Image(systemName: "arrow.up.right")
                    .font(.caption)
            }
            .foregroundStyle(DeadlyColors.primary)
        }
    }
}

// MARK: - BulletRow

struct BulletRow: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("•")
                .foregroundStyle(.secondary)
            Text(text)
                .foregroundStyle(.primary)
        }
        .font(.body)
    }
}
