import SwiftUI
import UIKit
import SwiftAudioStreamEx

/// "Send Bug Report" screen. Reads the last hour of unified-log entries (filtered
/// by app + playback subsystems) via `LogExport`, shows a preview, and offers
/// share + copy-to-clipboard actions so users can ship logs to support.
struct BugReportView: View {
    @State private var logText: String = ""
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var shareItem: ShareItem?
    @State private var copied = false

    /// Optional preset filter — e.g., `[PB]` to scope to playback lines only.
    var filterContains: String? = nil

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
                    Text(logText.isEmpty ? "(no log entries in the selected window)" : logText)
                        .font(.system(.footnote, design: .monospaced))
                        .textSelection(.enabled)
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
            Text("Tap **Share** to send these logs to support. They contain URLs of tracks you played and timing information — no account or personal data.")
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }

    private var actionBar: some View {
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
            .buttonStyle(.borderedProminent)
            .disabled(isLoading || logText.isEmpty)

            Button {
                Task { await loadLogs() }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .frame(width: 44, height: 32)
            }
            .buttonStyle(.bordered)
            .disabled(isLoading)
        }
    }

    private func loadLogs() async {
        isLoading = true
        loadError = nil
        copied = false
        do {
            let text = try await Task.detached(priority: .userInitiated) {
                try LogExport.exportRecentLogs(duration: 3600, filterContains: filterContains)
            }.value
            logText = text
        } catch {
            loadError = error.localizedDescription
        }
        isLoading = false
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

private struct BugReportShareSheet: UIViewControllerRepresentable {
    let fileURL: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
