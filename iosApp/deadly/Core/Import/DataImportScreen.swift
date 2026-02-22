import SwiftUI

/// Full-screen import progress view shown on first launch (or forced re-import).
/// Dismisses automatically when the import completes.
struct DataImportScreen: View {
    @Environment(\.appContainer) private var container
    @Binding var isPresented: Bool

    @State private var progress: ImportProgress?
    @State private var completed = false
    @State private var force: Bool

    init(isPresented: Binding<Bool>, force: Bool = false) {
        self._isPresented = isPresented
        self._force = State(initialValue: force)
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 32) {
                Spacer()

                // Icon
                Image(systemName: completed ? "checkmark.circle.fill" : "arrow.down.circle")
                    .font(.system(size: 64))
                    .foregroundStyle(completed ? .green : .accentColor)
                    .symbolEffect(.bounce, value: completed)

                // Title
                Text(completed ? "Ready" : "Loading Show Data")
                    .font(.title2.bold())

                // Phase label
                if let p = progress {
                    Text(p.message)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)

                    // Progress bar (only when we have a meaningful total)
                    if p.total > 0 {
                        ProgressView(value: p.fraction)
                            .padding(.horizontal, 48)
                            .animation(.easeInOut, value: p.fraction)

                        Text("\(p.processed) / \(p.total)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else if !completed {
                        ProgressView()
                    }
                } else {
                    ProgressView()
                    Text("Starting…")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                // Done button (visible after completion)
                if completed {
                    Button("Let's Go") {
                        isPresented = false
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.bottom, 40)
                }
            }
            .padding()
        }
        .task {
            await runImport()
        }
    }

    private func runImport() async {
        for await p in container.dataImportService.run(force: force) {
            progress = p
            if p.phase == .completed || p.phase == .failed {
                completed = true
            }
        }
        if !completed { completed = true }
    }
}
