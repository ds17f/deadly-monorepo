import SwiftUI
import UIKit
import SwiftAudioStreamEx

/// "Send Bug Report" screen. Reads the last hour of unified-log entries (filtered
/// by app + playback subsystems) via `LogExport`, shows a preview, and offers
/// share + copy-to-clipboard actions so users can ship logs to support.
struct BugReportView: View {
    @Environment(\.appContainer) private var container

    /// Full log text — used for Send, Copy and Share. Never rendered directly.
    @State private var logText: String = ""
    /// Bounded tail of `logText` shown on screen (see `LogPreview`).
    @State private var preview = LogPreview.empty
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var shareItem: ShareItem?
    @State private var copied = false

    @State private var note: String = ""
    @State private var isSending = false
    @State private var sendStatus: SendStatus?

    /// Optional preset filter — e.g., `[PB]` to scope to playback lines only.
    var filterContains: String? = nil

    private enum SendStatus: Equatable {
        case success
        case failure(String)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            preamble
                .padding()
                .background(Color(.secondarySystemBackground))

            Divider()

            if isLoading {
                Spacer()
                ProgressView("Reading logs…")
                    .frame(maxWidth: .infinity)
                Spacer()
            } else if let err = loadError {
                Spacer()
                VStack(spacing: 8) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundStyle(.orange)
                    Text("Couldn't read logs")
                        .font(.headline)
                    Text(err)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        if preview.isTruncated {
                            Text("Showing last \(preview.shownLines) lines — full log is included when you send")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text(logText.isEmpty ? "(no log entries in the selected window)" : preview.text)
                            .font(.system(.footnote, design: .monospaced))
                            .textSelection(.enabled)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                }
            }

            Divider()

            actionBar
                .padding()
        }
        .navigationTitle("Send Bug Report")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadLogs() }
        .sheet(item: $shareItem) { item in
            BugReportShareSheet(fileURL: item.url)
        }
    }

    private var preamble: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Logs from the last hour")
                .font(.headline)
            Text("Tap **Send Bug Report** to send these logs straight to support. They contain URLs of tracks you played and timing information — no account or personal data.")
                .font(.callout)
                .foregroundStyle(.secondary)

            TextField("What happened? (optional)", text: $note, axis: .vertical)
                .lineLimit(1...3)
                .textFieldStyle(.roundedBorder)
                .padding(.top, 4)
        }
    }

    private var actionBar: some View {
        VStack(spacing: 10) {
            switch sendStatus {
            case .success:
                Label("Sent — thank you!", systemImage: "checkmark.circle.fill")
                    .font(.callout)
                    .foregroundStyle(.green)
                    .frame(maxWidth: .infinity, alignment: .leading)
            case .failure(let msg):
                Label(msg, systemImage: "exclamationmark.triangle.fill")
                    .font(.callout)
                    .foregroundStyle(.orange)
                    .frame(maxWidth: .infinity, alignment: .leading)
            case nil:
                EmptyView()
            }

            Button {
                Task { await sendReport() }
            } label: {
                HStack {
                    if isSending {
                        ProgressView().controlSize(.small)
                    } else {
                        Image(systemName: "paperplane.fill")
                    }
                    Text(isSending ? "Sending…" : "Send Bug Report")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading || isSending || logText.isEmpty)

            // Fallback path — for when sending fails or the user prefers to send
            // the logs themselves (e.g. via email/Reddit).
            HStack(spacing: 12) {
                Button {
                    copyToClipboard()
                } label: {
                    Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isLoading || logText.isEmpty)

                Button {
                    prepareShare()
                } label: {
                    Label("Share", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isLoading || logText.isEmpty)

                Button {
                    Task { await loadLogs() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .frame(width: 44, height: 32)
                }
                .buttonStyle(.bordered)
                .disabled(isLoading || isSending)
            }
        }
    }

    private func loadLogs() async {
        isLoading = true
        loadError = nil
        copied = false
        do {
            // Export and build the bounded preview off the main thread. A full
            // hour of logs can be several MB; laying that out in one Text
            // hangs the main thread.
            let filter = filterContains
            let (text, tail) = try await Task.detached(priority: .userInitiated) {
                let text = try LogExport.exportRecentLogs(duration: 3600, filterContains: filter)
                return (text, LogPreview.make(from: text))
            }.value
            logText = text
            preview = tail
        } catch {
            loadError = error.localizedDescription
        }
        isLoading = false
    }

    /// One-tap submit: POSTs the logs + metadata to the server. Gated by the
    /// shared analytics key; stamps the signed-in user when a token exists,
    /// otherwise the report is anonymous.
    private func sendReport() async {
        guard !logText.isEmpty else { return }
        isSending = true
        sendStatus = nil

        let prefs = container.appPreferences
        let baseURL = prefs.apiBaseUrl
        guard let url = URL(string: "\(baseURL)/api/bug-reports") else {
            sendStatus = .failure("Couldn't build request URL.")
            isSending = false
            return
        }

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let payload: [String: String?] = [
            "logs": logText,
            "note": note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : note,
            "platform": "ios",
            "appVersion": appVersion,
            "osVersion": "iOS \(UIDevice.current.systemVersion)",
            "device": UIDevice.current.model,
            "installId": prefs.installId,
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Secrets.analyticsApiKey, forHTTPHeaderField: "X-Analytics-Key")
        if let token = container.authService.token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        do {
            request.httpBody = try JSONSerialization.data(
                withJSONObject: payload.compactMapValues { $0 }
            )
            let (_, response) = try await URLSession.shared.data(for: request)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            if (200..<300).contains(code) {
                sendStatus = .success
            } else if code == 429 {
                sendStatus = .failure("Too many reports — please try again later.")
            } else {
                sendStatus = .failure("Send failed (\(code)). Try Copy or Share instead.")
            }
        } catch {
            sendStatus = .failure("Couldn't reach the server. Try Copy or Share instead.")
        }
        isSending = false
    }

    private func copyToClipboard() {
        UIPasteboard.general.string = logText
        copied = true
        Task {
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            copied = false
        }
    }

    private func prepareShare() {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        let filename = "deadly-playback-logs-\(formatter.string(from: Date())).txt"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        do {
            try logText.write(to: url, atomically: true, encoding: .utf8)
            shareItem = ShareItem(url: url)
        } catch {
            loadError = "Couldn't prepare share file: \(error.localizedDescription)"
        }
    }

    private struct ShareItem: Identifiable {
        let id = UUID()
        let url: URL
    }
}

/// Bounded tail of the log for on-screen preview: the last
/// `maxLines` lines or `maxBytes` UTF-8 bytes, whichever is smaller.
/// The full log is still sent / copied / shared.
struct LogPreview: Sendable {
    static let maxLines = 500
    static let maxBytes = 100_000
    static let empty = LogPreview(text: "", shownLines: 0, isTruncated: false)

    let text: String
    let shownLines: Int
    let isTruncated: Bool

    static func make(from fullText: String) -> LogPreview {
        let lines = fullText.split(separator: "\n", omittingEmptySubsequences: false)
        var kept: [Substring] = []
        var bytes = 0
        for line in lines.reversed() {
            let cost = line.utf8.count + 1
            if kept.count >= maxLines || bytes + cost > maxBytes { break }
            kept.append(line)
            bytes += cost
        }
        var cutLongLine = false
        if kept.isEmpty, let last = lines.last {
            // A single line larger than the byte budget: keep its tail.
            kept = [Substring(String(decoding: last.utf8.suffix(maxBytes), as: UTF8.self))]
            cutLongLine = true
        }
        kept.reverse()
        return LogPreview(
            text: kept.joined(separator: "\n"),
            shownLines: kept.count,
            isTruncated: cutLongLine || kept.count < lines.count
        )
    }
}

private struct BugReportShareSheet: UIViewControllerRepresentable {
    let fileURL: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
