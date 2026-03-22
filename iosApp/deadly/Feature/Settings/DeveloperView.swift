import SwiftUI

struct DeveloperView: View {
    @Environment(\.appContainer) private var container
    @State private var dataVersion: String?
    @State private var showingClearCacheAlert = false
    @State private var showingClearCacheResult = false
    @State private var clearCacheSuccess = false
    @State private var showingReimportConfirm = false
    @State private var showingImport = false

    var body: some View {
        List {
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

                Button("Clear All Caches") {
                    showingClearCacheAlert = true
                }

                Button("Force Re-Import") {
                    showingReimportConfirm = true
                }
                .foregroundStyle(.red)
            } footer: {
                Text("Advanced tools for debugging and data recovery.")
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
