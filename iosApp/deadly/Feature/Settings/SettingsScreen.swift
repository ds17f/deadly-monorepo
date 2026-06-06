import AuthenticationServices
import SwiftAudioStreamEx
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
    private func cardSizePicker(
        title: String,
        helper: String,
        feature: String,
        get: @escaping () -> String,
        set: @escaping (String) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
            Picker(title, selection: Binding(
                get: { CarouselCardSize(preferenceKey: get()) },
                set: { newSize in
                    set(newSize.rawValue)
                    container.analyticsService.track("feature_use", props: [
                        "feature": feature,
                        "category": "preference",
                        "value": newSize.rawValue,
                    ])
                }
            )) {
                ForEach(CarouselCardSize.allCases) { size in
                    Text(size.label).tag(size)
                }
            }
            .pickerStyle(.segmented)
            Text(helper)
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }

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
                        container.analyticsService.track("feature_use", props: ["feature": "sign_out", "category": "account"])
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
                        container.analyticsService.track("feature_use", props: [
                            "feature": "toggle_shows_without_recordings",
                            "category": "preference",
                            "enabled": $0,
                        ])
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Include shows without recordings")
                        Text("Show concerts even if they have no audio recordings available")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Lock screen & CarPlay controls")
                    Picker("Lock screen & CarPlay controls", selection: Binding(
                        get: { PlayerControlsStyle(rawValueOrDefault: container.appPreferences.playerControlsStyle) },
                        set: { newStyle in
                            container.appPreferences.playerControlsStyle = newStyle.rawValue
                            container.streamPlayer.setControlStyle(newStyle)
                            container.analyticsService.track("feature_use", props: [
                                "feature": "set_player_controls_style",
                                "category": "preference",
                                "value": newStyle.rawValue,
                            ])
                        }
                    )) {
                        Text("Tracks").tag(PlayerControlsStyle.skipTrack)
                        Text("15s skip").tag(PlayerControlsStyle.skipSeconds)
                    }
                    .pickerStyle(.segmented)
                    Text("Previous/next track buttons or 15-second skip on the lock screen and CarPlay.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Source type badge")
                    Picker("Source type badge", selection: Binding(
                        get: { SourceBadgeStyle.fromString(container.appPreferences.sourceBadgeStyle) },
                        set: {
                            container.appPreferences.sourceBadgeStyle = $0.rawValue
                            ShowArtworkService.shared.badgeStyle = $0
                            container.analyticsService.track("feature_use", props: [
                                "feature": "set_source_badge_style",
                                "category": "preference",
                                "value": $0.rawValue,
                            ])
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

            // MARK: - Home Screen
            Section("Home Screen") {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Trending window on home")
                    Picker("Trending window on home", selection: Binding(
                        get: { TrendingWindow(preferenceKey: container.appPreferences.homeTrendingWindow) },
                        set: { newWindow in
                            container.appPreferences.homeTrendingWindow = newWindow.rawValue
                            container.analyticsService.track("feature_use", props: [
                                "feature": "set_home_trending_window",
                                "category": "preference",
                                "value": newWindow.rawValue,
                            ])
                        }
                    )) {
                        ForEach(TrendingWindow.allCases) { window in
                            Text(window.label).tag(window)
                        }
                    }
                    .pickerStyle(.segmented)
                    Text("Which time range \"Trending on The Deadly\" shows.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                Toggle(isOn: Binding(
                    get: { container.appPreferences.homeTrendingAboveToday },
                    set: { newValue in
                        container.appPreferences.homeTrendingAboveToday = newValue
                        container.analyticsService.track("feature_use", props: [
                            "feature": "set_home_trending_above_today",
                            "category": "preference",
                            "value": String(newValue),
                        ])
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Show Trending above Today")
                        Text("Move the Trending section below \"Today in Grateful Dead History\" by turning this off.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                Toggle(isOn: Binding(
                    get: { container.appPreferences.homeTrendingIncludeAnniversaries },
                    set: { newValue in
                        container.appPreferences.homeTrendingIncludeAnniversaries = newValue
                        container.analyticsService.track("feature_use", props: [
                            "feature": "set_home_trending_include_anniversaries",
                            "category": "preference",
                            "value": String(newValue),
                        ])
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Include \"Today in History\" in Trending")
                        Text("Off by default — recommended for variety. The 24-hour Trending window is otherwise dominated by anniversary plays.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Recently Played rows")
                    Picker("Recently Played rows", selection: Binding(
                        get: { container.appPreferences.homeRecentRows },
                        set: { newRows in
                            container.appPreferences.homeRecentRows = newRows
                            container.analyticsService.track("feature_use", props: [
                                "feature": "set_home_recent_rows",
                                "category": "preference",
                                "value": String(newRows),
                            ])
                        }
                    )) {
                        ForEach(1...4, id: \.self) { rows in
                            Text("\(rows)").tag(rows)
                        }
                    }
                    .pickerStyle(.segmented)
                    Text("How many rows of recent shows on the home screen (2 shows per row).")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                cardSizePicker(
                    title: "Trending card size",
                    helper: "Size of cards in the Trending carousel.",
                    feature: "set_home_trending_card_size",
                    get: { container.appPreferences.homeTrendingCardSize },
                    set: { container.appPreferences.homeTrendingCardSize = $0 }
                )

                cardSizePicker(
                    title: "Today in History card size",
                    helper: "Size of cards in the Today in Grateful Dead History carousel.",
                    feature: "set_home_today_card_size",
                    get: { container.appPreferences.homeTodayCardSize },
                    set: { container.appPreferences.homeTodayCardSize = $0 }
                )

                cardSizePicker(
                    title: "Featured Collections card size",
                    helper: "Size of cards in the Featured Collections carousel.",
                    feature: "set_home_collections_card_size",
                    get: { container.appPreferences.homeCollectionsCardSize },
                    set: { container.appPreferences.homeCollectionsCardSize = $0 }
                )

                Toggle(isOn: Binding(
                    get: { container.appPreferences.homePopularEnabled },
                    set: { newValue in
                        container.appPreferences.homePopularEnabled = newValue
                        container.analyticsService.track("feature_use", props: [
                            "feature": "set_home_popular_enabled",
                            "category": "preference",
                            "value": String(newValue),
                        ])
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Show Fan Favorites")
                        Text("Shows other listeners kept — ranked by saved-vs-played ratio.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text("Fan Favorites decade")
                    Picker("Fan Favorites decade", selection: Binding(
                        get: { PopularDecade(preferenceKey: container.appPreferences.homePopularDecade) },
                        set: { newDecade in
                            container.appPreferences.homePopularDecade = newDecade.rawValue
                            container.analyticsService.track("feature_use", props: [
                                "feature": "set_home_popular_decade",
                                "category": "preference",
                                "value": newDecade.rawValue,
                            ])
                        }
                    )) {
                        ForEach(PopularDecade.allCases) { decade in
                            Text(decade.label).tag(decade)
                        }
                    }
                    .pickerStyle(.segmented)
                    Text("Default decade filter for the Fan Favorites rail. Tap the header to cycle.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                cardSizePicker(
                    title: "Fan Favorites card size",
                    helper: "Size of cards in the Fan Favorites carousel.",
                    feature: "set_home_popular_card_size",
                    get: { container.appPreferences.homePopularCardSize },
                    set: { container.appPreferences.homePopularCardSize = $0 }
                )

                Button(role: .destructive) {
                    container.appPreferences.resetHomePreferences()
                    container.analyticsService.track("feature_use", props: [
                        "feature": "reset_home_preferences",
                        "category": "preference",
                    ])
                } label: {
                    Text("Reset Home Screen to Defaults")
                }
            }

            // MARK: - Connect
            if container.authService.isSignedIn {
                Section("Connect") {
                    NavigationLink {
                        ConnectScreen()
                    } label: {
                        HStack {
                            settingsRow("Connected Devices", systemImage: "iphone.and.arrow.forward")
                            if container.connectService.isConnected && !container.connectService.devices.isEmpty {
                                Text("\(container.connectService.devices.count)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .foregroundStyle(.primary)
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

            // MARK: - Support
            Section("Support") {
                NavigationLink {
                    BugReportView()
                } label: {
                    Label("Send Bug Report", systemImage: "ladybug")
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
