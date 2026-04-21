import AuthenticationServices
import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - SettingsScreen

struct SettingsScreen: View {
    var onNavigateToDownloads: (() -> Void)? = nil
    var onNavigateToEqualizer: (() -> Void)? = nil
    @Environment(\.appContainer) private var container
    @Environment(\.openURL) private var openURL
    @State private var showingFilePicker = false
    @State private var exportItem: ExportItem?
    @State private var importResult: BackupImportResult?
    @State private var importError: String?
    @State private var showingImportAlert = false
    @State private var authError: String?
    @State private var showingAuthError = false
    private func settingsRow(_ title: String, systemImage: String) -> some View {
        HStack {
            Image(systemName: systemImage)
                .foregroundStyle(DeadlyColors.primary)
            Text(title)
                .foregroundStyle(.primary)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
    }

    var body: some View {
        List {
            // MARK: - Account
            Section("Account") {
                if container.authService.isSignedIn {
                    if let user = container.authService.currentUser {
                        VStack(alignment: .leading, spacing: 4) {
                            if let name = user.name {
                                Text(name)
                                    .font(.body)
                            }
                            if let email = user.email {
                                Text(email)
                                    .font(.callout)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    Button("Sign Out", role: .destructive) {
                        container.analyticsService.track("feature_use", props: ["feature": "sign_out"])
                        container.authService.signOut()
                    }
                } else if container.appPreferences.serverEnvironment == "custom" {
                    Button {
                        Task { await container.authService.fetchDevToken() }
                    } label: {
                        Text("Sign In (Dev)")
                            .frame(maxWidth: .infinity, alignment: .center)
                            .frame(height: 44)
                    }
                    .buttonStyle(.bordered)
                } else {
                    SignInWithAppleButton(.signIn) { request in
                        request.requestedScopes = [.fullName, .email]
                    } onCompletion: { _ in
                        // Handled by AuthService delegate
                    }
                    .signInWithAppleButtonStyle(.whiteOutline)
                    .frame(height: 44)
                    // Use our own async flow instead of the completion handler
                    .overlay {
                        Color.clear
                            .contentShape(Rectangle())
                            .onTapGesture {
                                Task {
                                    do {
                                        try await container.authService.signInWithApple()
                                    } catch {
                                        authError = error.localizedDescription
                                        showingAuthError = true
                                    }
                                }
                            }
                    }

                    Button {
                        Task {
                            do {
                                guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                                      let viewController = scene.windows.first?.rootViewController else { return }
                                try await container.authService.signInWithGoogle(presenting: viewController)
                            } catch {
                                authError = error.localizedDescription
                                showingAuthError = true
                            }
                        }
                    } label: {
                        HStack {
                            Image(systemName: "g.circle.fill")
                                .font(.title2)
                            Text("Sign in with Google")
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                        .frame(height: 44)
                    }
                    .buttonStyle(.bordered)
                }
            }

            // MARK: - Preferences
            Section("Preferences") {
                Toggle(isOn: Binding(
                    get: { container.appPreferences.includeShowsWithoutRecordings },
                    set: {
                        container.appPreferences.includeShowsWithoutRecordings = $0
                        container.analyticsService.track("feature_use", props: ["feature": "toggle_shows_without_recordings", "enabled": $0])
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Include shows without recordings")
                        Text("Show concerts even if they have no audio recordings available")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                Toggle(isOn: Binding(
                    get: { container.appPreferences.showTicketImages },
                    set: {
                        container.appPreferences.showTicketImages = $0
                        container.analyticsService.track("feature_use", props: ["feature": "toggle_ticket_images", "enabled": $0])
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Show ticket images")
                        Text("Display historical concert ticket stubs as show artwork")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Source type badge")
                    Picker("Source type badge", selection: Binding(
                        get: { SourceBadgeStyle.fromString(container.appPreferences.sourceBadgeStyle) },
                        set: {
                            container.appPreferences.sourceBadgeStyle = $0.rawValue
                            ShowArtworkService.shared.badgeStyle = $0
                            container.analyticsService.track("feature_use", props: ["feature": "set_source_badge_style", "value": $0.rawValue])
                        }
                    )) {
                        ForEach(SourceBadgeStyle.allCases, id: \.self) { style in
                            Text(style.label).tag(style)
                        }
                    }
                    .pickerStyle(.segmented)
                    Text("Label style on artwork thumbnails")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }

            // MARK: - Audio
            Section("Audio") {
                if let onNavigateToEqualizer {
                    Button { onNavigateToEqualizer() } label: {
                        settingsRow("Equalizer", systemImage: "slider.vertical.3")
                    }
                    .foregroundStyle(.primary)
                } else {
                    NavigationLink {
                        EqualizerView()
                    } label: {
                        Label("Equalizer", systemImage: "slider.vertical.3")
                    }
                }
            }

            // MARK: - Favorites & Data
            Section("Favorites & Data") {
                if let onNavigateToDownloads {
                    Button { onNavigateToDownloads() } label: {
                        settingsRow("Manage Downloads", systemImage: "arrow.down.circle")
                    }
                    .foregroundStyle(.primary)
                } else {
                    NavigationLink(value: SettingsRoute.downloads) {
                        Label("Manage Downloads", systemImage: "arrow.down.circle")
                    }
                }
                Button {
                    showingFilePicker = true
                } label: {
                    settingsRow("Import Favorites", systemImage: "square.and.arrow.down")
                }
                .foregroundStyle(.primary)
                Button {
                    if let data = try? container.favoritesImportExportService.exportFavorites() {
                        exportItem = ExportItem(data: data, filename: container.favoritesImportExportService.exportFilename())
                    }
                } label: {
                    settingsRow("Export Favorites", systemImage: "square.and.arrow.up")
                }
                .foregroundStyle(.primary)
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
                    settingsRow("Donate to Internet Archive", systemImage: "heart")
                }
                .foregroundStyle(.primary)
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

            Section {
                NavigationLink("Privacy & Data") {
                    PrivacyDataView()
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

        // MARK: - Auth Error Alert
        .alert("Sign In Error", isPresented: $showingAuthError) {
            Button("OK") {}
        } message: {
            Text(authError ?? "Unknown error.")
        }

        // MARK: - Favorites Export Share Sheet
        .sheet(item: $exportItem) { item in
            FavoritesExportShareSheet(data: item.data, filename: item.filename)
        }

    }
}

// MARK: - ExportItem

private struct ExportItem: Identifiable {
    let id = UUID()
    let data: Data
    let filename: String
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
