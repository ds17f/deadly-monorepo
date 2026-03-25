import SwiftUI

// MARK: - Quotes

/// Rotating quotes displayed during import.
/// Format: (quote, attribution)
private let deadlyQuotes: [(quote: String, song: String)] = [
    ("Music is the best", "Frank Zappa"),
    ("Where words fail, music speaks", "Hans Christian Andersen"),
    ("One good thing about music, when it hits you, you feel no pain", "Bob Marley"),
    ("Live music is the art of the moment", "The Archive"),
    ("Every show is unique, unrepeatable, and worth preserving", "The Archive"),
    ("The taper's art: capturing lightning in a bottle", "Live Music Archive"),
    ("Freely shared, non-commercially, and in community", "The Archive"),
    ("Thousands of artists. Hundreds of thousands of recordings.", "Live Music Archive"),
    ("Preserving live music for future generations", "Internet Archive"),
    ("The best music is the music that's shared", "The Community"),
]

// MARK: - Import Screen State

private enum ImportScreenState {
    case loading
    case importing
    case completed
    case error(String)
}

// MARK: - DataImportScreen

/// Full-screen splash/import screen shown on first launch (or forced re-import).
/// Features branding, rotating quotes, elapsed time counter, and auto-dismiss on completion.
struct DataImportScreen: View {
    @Environment(\.appContainer) private var container
    @Binding var isPresented: Bool

    @State private var progress: ImportProgress?
    @State private var screenState: ImportScreenState = .loading
    @State private var force: Bool

    // Quote rotation
    @State private var currentQuoteIndex = 0
    @State private var quoteOpacity: Double = 1.0

    // Elapsed time
    @State private var startTime: Date?
    @State private var elapsedSeconds: Int = 0

    init(isPresented: Binding<Bool>, force: Bool = false) {
        self._isPresented = isPresented
        self._force = State(initialValue: force)
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // MARK: - Branding Section
                brandingSection

                Spacer()
                    .frame(height: 40)

                // MARK: - Quote Section
                quoteSection
                    .frame(height: 100)

                Spacer()

                // MARK: - Progress/Status Section
                statusSection

                Spacer()
                    .frame(height: 40)
            }
            .padding()
        }
        .task {
            await runImport()
        }
        .task {
            await rotateQuotes()
        }
        .task {
            await updateElapsedTime()
        }
    }

    // MARK: - Branding

    private var brandingSection: some View {
        VStack(spacing: 12) {
            // Logo
            ZStack {
                if case .completed = screenState {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 80))
                        .foregroundStyle(.green)
                        .symbolEffect(.bounce, value: screenState.isCompleted)
                } else {
                    Image("deadly_logo_square")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 120, height: 120)
                        .clipShape(RoundedRectangle(cornerRadius: 24))
                }
            }

            // App name
            Text("Deadly")
                .font(.system(size: 42, weight: .bold, design: .rounded))
                .foregroundStyle(
                    LinearGradient(
                        colors: [DeadlyColors.primary, DeadlyColors.tertiary],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )

            // Tagline
            Text("Live Music Archive")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Quote Section

    private var quoteSection: some View {
        VStack(spacing: 8) {
            let quote = deadlyQuotes[currentQuoteIndex]

            Text("\"\(quote.quote)\"")
                .font(.body.italic())
                .multilineTextAlignment(.center)
                .foregroundStyle(.primary)

            Text("— \(quote.song)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 24)
        .opacity(quoteOpacity)
        .animation(.easeInOut(duration: 0.5), value: quoteOpacity)
    }

    // MARK: - Status Section

    @ViewBuilder
    private var statusSection: some View {
        switch screenState {
        case .loading:
            loadingView

        case .importing:
            importingView

        case .completed:
            completedView

        case .error(let message):
            errorView(message: message)
        }
    }

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text("Starting…")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var importingView: some View {
        VStack(spacing: 16) {
            // Phase message
            if let p = progress {
                Text(p.message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                // Progress bar (when we have a meaningful total)
                if p.total > 0 {
                    ProgressView(value: p.fraction)
                        .padding(.horizontal, 48)
                        .animation(.easeInOut, value: p.fraction)

                    Text("\(p.processed) / \(p.total)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    ProgressView()
                }

                // Elapsed time
                Text("Elapsed: \(formattedElapsedTime)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.tertiary)
            }

            // Skip button
            Button("Skip") {
                isPresented = false
            }
            .buttonStyle(.bordered)
            .foregroundStyle(.secondary)
            .padding(.top, 8)
        }
    }

    private var completedView: some View {
        VStack(spacing: 16) {
            Text("Ready")
                .font(.title2.bold())
                .foregroundStyle(.green)
        }
        .onAppear {
            // Auto-dismiss after brief delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                isPresented = false
            }
        }
    }

    private func errorView(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 40))
                .foregroundStyle(.orange)

            Text("Import Failed")
                .font(.headline)

            Text(message)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            HStack(spacing: 16) {
                Button("Retry") {
                    screenState = .loading
                    progress = nil
                    elapsedSeconds = 0
                    startTime = nil
                    Task {
                        await runImport()
                    }
                }
                .buttonStyle(.borderedProminent)

                Button("Skip") {
                    isPresented = false
                }
                .buttonStyle(.bordered)
            }
        }
    }

    // MARK: - Formatted Elapsed Time

    private var formattedElapsedTime: String {
        let minutes = elapsedSeconds / 60
        let seconds = elapsedSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }

    // MARK: - Import Logic

    private func runImport() async {
        startTime = Date()
        screenState = .importing

        var lastPhase: ImportPhase?

        for await p in container.dataImportService.run(force: force) {
            progress = p
            lastPhase = p.phase

            if p.phase == .completed {
                screenState = .completed
                return
            } else if p.phase == .failed {
                screenState = .error(p.message)
                return
            }
        }

        // Stream ended - check final state
        if lastPhase == .completed || lastPhase == nil {
            screenState = .completed
        }
    }

    // MARK: - Quote Rotation

    private func rotateQuotes() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(5))

            guard !Task.isCancelled else { break }

            // Fade out
            withAnimation(.easeInOut(duration: 0.3)) {
                quoteOpacity = 0
            }

            try? await Task.sleep(for: .milliseconds(300))

            // Change quote
            currentQuoteIndex = (currentQuoteIndex + 1) % deadlyQuotes.count

            // Fade in
            withAnimation(.easeInOut(duration: 0.3)) {
                quoteOpacity = 1
            }
        }
    }

    // MARK: - Elapsed Time Updates

    private func updateElapsedTime() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(1))

            guard !Task.isCancelled else { break }

            if let start = startTime, case .importing = screenState {
                elapsedSeconds = Int(Date().timeIntervalSince(start))
            }
        }
    }
}

// MARK: - State Helpers

private extension ImportScreenState {
    var isCompleted: Bool {
        if case .completed = self { return true }
        return false
    }
}
