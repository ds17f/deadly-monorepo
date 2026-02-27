import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - SettingsScreen

struct SettingsScreen: View {
    @Environment(\.appContainer) private var container
    @State private var showingImport = false
    @State private var dataVersion: String?
    @State private var showingLibraryFilePicker = false
    @State private var libraryExportData: Data?
    @State private var libraryImportResult: LibraryImportResult?
    @State private var libraryImportError: String?
    @State private var showingLibraryImportAlert = false
    @State private var showingLibraryExportShare = false
    @State private var showingClearCacheAlert = false
    @State private var showingClearCacheResult = false
    @State private var clearCacheSuccess = false
    @State private var showingReimportConfirm = false

    var body: some View {
        List {
            // MARK: - Migration
            Section("Migration") {
                Button("Import Library from Old App") {
                    showingLibraryFilePicker = true
                }
                Button("Export Library") {
                    libraryExportData = try? container.libraryImportExportService.exportLibrary()
                    if libraryExportData != nil { showingLibraryExportShare = true }
                }
            }

            // MARK: - Cache Management
            Section {
                Button("Clear All Caches") {
                    showingClearCacheAlert = true
                }
            } header: {
                Text("Cache Management")
            } footer: {
                Text("Deletes cached images, track lists, and reviews. Data will be re-downloaded when needed.")
            }

            // MARK: - Data Management
            Section("Data Management") {
                if let v = dataVersion {
                    LabeledContent("Database Version", value: v)
                } else {
                    LabeledContent("Database Version", value: "No data")
                }
                Button("Force Re-Import") {
                    showingReimportConfirm = true
                }
                .foregroundStyle(.red)
            }

            // MARK: - Preferences
            Section("Preferences") {
                Toggle(isOn: Binding(
                    get: { container.appPreferences.showOnlyRecordedShows },
                    set: { container.appPreferences.showOnlyRecordedShows = $0 }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Hide shows without recordings")
                        Text("Only show concerts that have audio recordings available")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
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
            }

            // MARK: - About
            Section("About") {
                NavigationLink("Legal & About") {
                    AboutView()
                }
            }
        }
        .navigationTitle("Settings")

        // MARK: - Library File Picker
        .fileImporter(
            isPresented: $showingLibraryFilePicker,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else {
                    libraryImportError = "Could not access file."
                    showingLibraryImportAlert = true
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                guard let data = try? Data(contentsOf: url) else {
                    libraryImportError = "Could not read file."
                    showingLibraryImportAlert = true
                    return
                }
                do {
                    libraryImportResult = try container.libraryImportExportService.importLibrary(from: data)
                    libraryImportError = nil
                } catch {
                    libraryImportError = error.localizedDescription
                    libraryImportResult = nil
                }
                showingLibraryImportAlert = true
            case .failure(let error):
                libraryImportError = error.localizedDescription
                showingLibraryImportAlert = true
            }
        }

        // MARK: - Library Import Alert
        .alert("Library Import", isPresented: $showingLibraryImportAlert) {
            Button("OK") {}
        } message: {
            if let result = libraryImportResult {
                Text("Imported \(result.imported) shows.\n\(result.alreadyInLibrary) already in library.\n\(result.notFound) not found in database.")
            } else {
                Text(libraryImportError ?? "Unknown error.")
            }
        }

        // MARK: - Library Export Share Sheet
        .sheet(isPresented: $showingLibraryExportShare) {
            if let data = libraryExportData {
                LibraryExportShareSheet(data: data, filename: container.libraryImportExportService.exportFilename())
            }
        }

        // MARK: - Clear Cache Confirmation
        .alert("Clear All Caches", isPresented: $showingClearCacheAlert) {
            Button("Clear", role: .destructive) {
                clearCacheSuccess = clearAllCaches()
                showingClearCacheResult = true
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will delete all cached images, track lists, and reviews. The data will be re-downloaded when needed.")
        }

        // MARK: - Clear Cache Result
        .alert(clearCacheSuccess ? "Caches Cleared" : "Clear Failed", isPresented: $showingClearCacheResult) {
            Button("OK") {}
        } message: {
            Text(clearCacheSuccess
                 ? "All caches have been cleared."
                 : "Failed to clear some caches.")
        }

        // MARK: - Re-Import Confirmation
        .alert("Force Re-Import", isPresented: $showingReimportConfirm) {
            Button("Re-Import", role: .destructive) {
                showingImport = true
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will re-download and re-import all show data. Your library will be preserved.")
        }

        // MARK: - Full-Screen Import
        .fullScreenCover(isPresented: $showingImport) {
            DataImportScreen(isPresented: $showingImport, force: true)
                .environment(\.appContainer, container)
        }

        // MARK: - Load Data Version
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

    // MARK: - Cache Clearing

    private func clearAllCaches() -> Bool {
        guard let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            return false
        }

        var success = true

        // Clear archive cache (track lists, reviews)
        let archiveDir = cacheDir.appendingPathComponent("archive")
        if FileManager.default.fileExists(atPath: archiveDir.path) {
            do {
                try FileManager.default.removeItem(at: archiveDir)
            } catch {
                success = false
            }
        }

        // Clear image cache
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

// MARK: - LibraryExportShareSheet

struct LibraryExportShareSheet: UIViewControllerRepresentable {
    let data: Data
    let filename: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        try? data.write(to: tempURL)
        return UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
