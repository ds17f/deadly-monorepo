import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - SettingsScreen

struct SettingsScreen: View {
    @Environment(\.appContainer) private var container
    @Environment(\.openURL) private var openURL
    @State private var showingFilePicker = false
    @State private var exportData: Data?
    @State private var importResult: BackupImportResult?
    @State private var importError: String?
    @State private var showingImportAlert = false
    @State private var showingExportShare = false
    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
    }

    var body: some View {
        List {
            // MARK: - Preferences
            Section("Preferences") {
                Toggle(isOn: Binding(
                    get: { container.appPreferences.showOnlyRecordedShows },
                    set: { container.appPreferences.showOnlyRecordedShows = $0 }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Hide shows without recordings")
                        Text("Only show concerts that have audio recordings available")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            // MARK: - Favorites
            Section("Favorites") {
                Button("Import Favorites from Old App") {
                    showingFilePicker = true
                }
                Button("Export Favorites") {
                    exportData = try? container.favoritesImportExportService.exportFavorites()
                    if exportData != nil { showingExportShare = true }
                }
            }

            // MARK: - About
            Section("About") {
                Button {
                    let urlString = "https://github.com/ds17f/deadly-monorepo/releases/tag/ios%2Fv\(appVersion)"
                    if let url = URL(string: urlString) { openURL(url) }
                } label: {
                    LabeledContent("Version", value: appVersion)
                }
                .foregroundStyle(.primary)
            }

            Section {
                Link(destination: URL(string: "https://archive.org/donate/")!) {
                    Label("Donate to Internet Archive", systemImage: "heart")
                }
                NavigationLink("Our Mission") {
                    MissionView()
                }
                NavigationLink("Legal & Policies") {
                    LegalView()
                }
                NavigationLink("Developer") {
                    DeveloperView()
                }
            }

        }
        .navigationTitle("Settings")

        // MARK: - Favorites File Picker
        .fileImporter(
            isPresented: $showingFilePicker,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                guard url.startAccessingSecurityScopedResource() else {
                    importError = "Could not access file."
                    showingImportAlert = true
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                guard let data = try? Data(contentsOf: url) else {
                    importError = "Could not read file."
                    showingImportAlert = true
                    return
                }
                do {
                    importResult = try container.favoritesImportExportService.importFavorites(from: data)
                    importError = nil
                } catch {
                    importError = error.localizedDescription
                    importResult = nil
                }
                showingImportAlert = true
            case .failure(let error):
                importError = error.localizedDescription
                showingImportAlert = true
            }
        }

        // MARK: - Favorites Import Alert
        .alert("Favorites Import", isPresented: $showingImportAlert) {
            Button("OK") {}
        } message: {
            if let result = importResult {
                Text("Imported \(result.favoritesImported) favorites, \(result.reviewsImported) reviews, \(result.preferencesImported) prefs.\n\(result.favoritesSkipped) already favorited.\n\(result.notFound) not found.")
            } else {
                Text(importError ?? "Unknown error.")
            }
        }

        // MARK: - Favorites Export Share Sheet
        .sheet(isPresented: $showingExportShare) {
            if let data = exportData {
                FavoritesExportShareSheet(data: data, filename: container.favoritesImportExportService.exportFilename())
            }
        }

    }
}

// MARK: - FavoritesExportShareSheet

struct FavoritesExportShareSheet: UIViewControllerRepresentable {
    let data: Data
    let filename: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        try? data.write(to: tempURL)
        return UIActivityViewController(activityItems: [tempURL], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
