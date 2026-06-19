import SwiftUI

struct DeveloperView: View {
    @Environment(\.appContainer) private var container
    @State private var dataVersion: String?
    @State private var showingClearCacheAlert = false
    @State private var showingClearCacheResult = false
    @State private var clearCacheSuccess = false
    @State private var showingReimportConfirm = false
    @State private var showingImport = false
    @State private var flushInFlight = false
    @State private var showingFlushResult = false
    @State private var flushSuccess = false
    @State private var flushCount = 0
    @State private var flushError: String?
    @State private var syncInFlight = false
    @State private var syncLog: [String] = []

    /// Git commit hash embedded into Info.plist at build time by the
    /// "Embed Git Commit Hash" build phase. Nil when unavailable.
    private var gitCommitHash: String? {
        let value = Bundle.main.infoDictionary?["GitCommitHash"] as? String
        guard let value, !value.isEmpty else { return nil }
        return value
    }

    var body: some View {
        List {
            Section("Build") {
                if let hash = gitCommitHash {
                    Link(destination: URL(string: "https://github.com/ds17f/deadly-monorepo/commit/\(hash)")!) {
                        LabeledContent("Commit") {
                            HStack(spacing: 4) {
                                Text(hash)
                                    .font(.system(.body, design: .monospaced))
                                Image(systemName: "arrow.up.right")
                                    .font(.caption)
                            }
                        }
                    }
                } else {
                    LabeledContent("Commit", value: "unknown")
                }
            }

            if container.authService.isSignedIn {
                Section {
                    Toggle(isOn: Binding(
                        get: { container.appPreferences.connectEnabled },
                        set: { newValue in
                            container.connectService.setEnabled(newValue)
                            container.analyticsService.track("feature_use", props: [
                                "feature": "set_connect_enabled",
                                "category": "preference",
                                "value": newValue ? "on" : "off",
                            ])
                        }
                    )) {
                        Label("Enable Connect (Beta)", systemImage: "iphone.and.arrow.forward")
                    }

                    NavigationLink {
                        ConnectScreen()
                    } label: {
                        HStack {
                            Label("Connected Devices", systemImage: "rectangle.connected.to.line.below")
                            if container.connectService.isConnected && !container.connectService.devices.isEmpty {
                                Spacer()
                                Text("\(container.connectService.devices.count)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .disabled(!container.appPreferences.connectEnabled)
                } header: {
                    Text("Connect")
                } footer: {
                    Text("Play in sync across your devices. Off by default; the server may also disable it globally.")
                }
            }

            Section {
                Picker("Server", selection: Binding(
                    get: { container.appPreferences.serverEnvironment },
                    set: {
                        container.appPreferences.serverEnvironment = $0
                        container.authService.onEnvironmentChanged()
                    }
                )) {
                    Text("Production").tag("prod")
                    Text("Beta").tag("beta")
                    Text("Custom").tag("custom")
                }
                .pickerStyle(.segmented)
                .listRowSeparator(.hidden)

                if container.appPreferences.serverEnvironment == "custom" {
                    TextField("Server URL", text: Binding(
                        get: { container.appPreferences.customServerUrl },
                        set: { container.appPreferences.customServerUrl = $0 }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)

                    TextField("Email", text: Binding(
                        get: { container.appPreferences.customDevEmail },
                        set: { container.appPreferences.customDevEmail = $0 }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.emailAddress)
                }

                Toggle(isOn: Binding(
                    get: { container.appPreferences.forceOnline },
                    set: { container.appPreferences.forceOnline = $0 }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Force online mode")
                        Text("Override offline detection when the app incorrectly shows as offline")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                LabeledContent("Database Version", value: dataVersion ?? "No data")

                Button(flushInFlight ? "Flushing analytics…" : "Flush analytics") {
                    guard !flushInFlight else { return }
                    flushInFlight = true
                    container.analyticsService.flushNow { ok, n, err in
                        DispatchQueue.main.async {
                            flushSuccess = ok
                            flushCount = n
                            flushError = err
                            flushInFlight = false
                            showingFlushResult = true
                        }
                    }
                }
                .disabled(flushInFlight)

                Button("Clear All Caches") {
                    showingClearCacheAlert = true
                }

                Button("Inject Network Error") {
                    container.streamPlayer.debugInjectNetworkError()
                }

                Button("Force Stale-Gen Race") {
                    container.streamPlayer.debugForceRaceCondition()
                }

                Button("Force Re-Import") {
                    showingReimportConfirm = true
                }
                .foregroundStyle(.red)
            } footer: {
                Text("Advanced tools for debugging and data recovery.")
            }

            Section("User Sync") {
                Button(syncInFlight ? "Pulling…" : "Pull from server") {
                    pullFromServer()
                }
                .disabled(syncInFlight)

                Button(syncInFlight ? "Pushing…" : "Push pending favorites (\(container.favoritesPushService.pendingCount()))") {
                    pushPending()
                }
                .disabled(syncInFlight)

                Button(syncInFlight ? "Pushing…" : "Push all local data") {
                    pushAll()
                }
                .disabled(syncInFlight)

                if !syncLog.isEmpty {
                    Button("Clear log") { syncLog.removeAll() }
                        .foregroundStyle(.secondary)

                    ForEach(Array(syncLog.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                    }
                }
            }
        }
        .navigationTitle("Developer")
        .navigationBarTitleDisplayMode(.inline)

        .alert("Clear All Caches", isPresented: $showingClearCacheAlert) {
            Button("Clear", role: .destructive) {
                clearCacheSuccess = clearAllCaches()
                showingClearCacheResult = true
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete all cached images, track lists, and reviews. The data will be re-downloaded when needed.")
        }

        .alert(flushSuccess ? "Flushed" : "Flush Failed", isPresented: $showingFlushResult) {
            Button("OK") {}
        } message: {
            if flushSuccess {
                Text(flushCount == 0 ? "Buffer was empty." : "Sent \(flushCount) event(s).")
            } else {
                Text("Failed to send \(flushCount) event(s). They have been preserved in the buffer.\n\n\(flushError ?? "Unknown error")")
            }
        }

        .alert(clearCacheSuccess ? "Caches Cleared" : "Clear Failed", isPresented: $showingClearCacheResult) {
            Button("OK") {}
        } message: {
            Text(clearCacheSuccess
                 ? "All caches have been cleared."
                 : "Failed to clear some caches.")
        }

        .alert("Force Re-Import", isPresented: $showingReimportConfirm) {
            Button("Re-Import", role: .destructive) {
                showingImport = true
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will re-download and re-import all show data. Your favorites will be preserved.")
        }

        .fullScreenCover(isPresented: $showingImport) {
            DataImportScreen(isPresented: $showingImport, force: true)
                .environment(\.appContainer, container)
        }

        .task {
            dataVersion = try? container.database.read { db in
                try String.fetchOne(db, sql: "SELECT dataVersion FROM data_version WHERE id = 1")
            }
        }
        .onChange(of: showingImport) { _, isShowing in
            if !isShowing {
                Task {
                    dataVersion = try? container.database.read { db in
                        try String.fetchOne(db, sql: "SELECT dataVersion FROM data_version WHERE id = 1")
                    }
                }
            }
        }
    }

    private func pullFromServer() {
        guard !syncInFlight else { return }
        syncInFlight = true
        let client = container.userSyncAPIClient
        Task {
            let start = Date()
            do {
                let backup = try await client.pullFullBackup()
                let elapsedMs = Int(Date().timeIntervalSince(start) * 1000)
                let lines: [String] = [
                    "[\(timestamp())] GET /api/user/sync OK in \(elapsedMs)ms",
                    "  version=\(backup.version) app=\(backup.app)",
                    "  favorites.shows=\(backup.favorites.shows.count)",
                    "  favorites.tracks=\(backup.favorites.tracks.count)",
                    "  reviews=\(backup.reviews.count)",
                    "  recordingPreferences=\(backup.recordingPreferences.count)",
                    "  recentShows=\(backup.recentShows?.count ?? 0)",
                    "  playbackPosition=\(backup.playbackPosition == nil ? "none" : "present")",
                    "  settings=\(backup.settings == nil ? "none" : "present")",
                ]
                syncLog.insert(contentsOf: lines, at: 0)
            } catch {
                syncLog.insert("[\(timestamp())] FAILED: \(error.localizedDescription)", at: 0)
            }
            syncInFlight = false
        }
    }

    private func pushPending() {
        guard !syncInFlight else { return }
        syncInFlight = true
        let svc = container.favoritesPushService
        Task {
            let ts = timestamp()
            let results = await svc.flushPending()
            var lines: [String] = ["[\(ts)] Push: \(results.count) entries"]
            if results.isEmpty {
                lines.append("  (outbox empty)")
            } else {
                for r in results {
                    let status = r.success ? "OK" : "FAIL"
                    var line = "  [\(r.kind)] \(r.operation) \(r.refId) → \(status)"
                    if let err = r.error { line += " (\(err))" }
                    lines.append(line)
                }
            }
            syncLog.insert(contentsOf: lines, at: 0)
            syncInFlight = false
        }
    }

    private func pushAll() {
        guard !syncInFlight else { return }
        syncInFlight = true
        let svc = container.favoritesPushService
        Task {
            let ts = timestamp()
            let results = await svc.enqueueAllLocalAndFlush()
            var lines: [String] = ["[\(ts)] Push all: \(results.count) entries"]
            if results.isEmpty {
                lines.append("  (nothing local)")
            } else {
                for r in results {
                    let status = r.success ? "OK" : "FAIL"
                    var line = "  [\(r.kind)] \(r.operation) \(r.refId) → \(status)"
                    if let err = r.error { line += " (\(err))" }
                    lines.append(line)
                }
            }
            syncLog.insert(contentsOf: lines, at: 0)
            syncInFlight = false
        }
    }

    private func timestamp() -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: Date())
    }

    private func clearAllCaches() -> Bool {
        guard let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            return false
        }

        var success = true

        let archiveDir = cacheDir.appendingPathComponent("archive")
        if FileManager.default.fileExists(atPath: archiveDir.path) {
            do {
                try FileManager.default.removeItem(at: archiveDir)
            } catch {
                success = false
            }
        }

        let imagesDir = cacheDir.appendingPathComponent("images")
        if FileManager.default.fileExists(atPath: imagesDir.path) {
            do {
                try FileManager.default.removeItem(at: imagesDir)
            } catch {
                success = false
            }
        }

        return success
    }
}
