import SwiftUI

struct SearchScreen: View {
    @Environment(\.appContainer) private var container

    var resetToken: Int = 0

    @State private var searchText = ""
    @FocusState private var isSearchFieldFocused: Bool
    @State private var searchTask: Task<Void, Never>?

    @State private var searchResults: [ArchiveShow] = []
    @State private var isSearchLoading = false

    private var showingResults: Bool {
        isSearchFieldFocused || !searchText.isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            searchBar
            if showingResults {
                resultsView
            } else {
                defaultView
            }
        }
        .toolbar(showingResults ? .hidden : .visible, for: .navigationBar)
        .animation(.easeInOut(duration: 0.2), value: showingResults)
        .onChange(of: resetToken) { _, _ in
            searchTask?.cancel()
            searchText = ""
            isSearchFieldFocused = false
            searchResults = []
        }
        .onChange(of: searchText) { _, newValue in
            if newValue.isEmpty {
                searchResults = []
                return
            }
            searchTask?.cancel()
            searchTask = Task {
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
                await performSearch(query: newValue)
            }
        }
    }

    // MARK: - Search bar

    private var searchBar: some View {
        HStack(spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                TextField("Search recordings", text: $searchText)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .focused($isSearchFieldFocused)
                    .toolbar {
                        ToolbarItemGroup(placement: .keyboard) {
                            Spacer()
                            Button("Done") {
                                isSearchFieldFocused = false
                            }
                        }
                    }
                    .onSubmit {
                        searchTask?.cancel()
                        Task { await performSearch(query: searchText) }
                    }
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(10)
            .background(Color(.systemGray5), in: RoundedRectangle(cornerRadius: 12))

            if showingResults {
                Button("Cancel") {
                    searchText = ""
                    isSearchFieldFocused = false
                    searchTask?.cancel()
                    searchResults = []
                }
                .foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
        .padding(.vertical, DeadlySpacing.screenPadding)
    }

    // MARK: - Search

    private func performSearch(query: String) async {
        isSearchLoading = true
        defer { isSearchLoading = false }
        do {
            let result = try await container.archiveSearchClient.searchAllArtists(
                query: query, page: 1, pageSize: 50
            )
            searchResults = result.shows
        } catch {
            searchResults = []
        }
    }

    // MARK: - Results

    private var resultsView: some View {
        Group {
            if isSearchLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if searchResults.isEmpty && !searchText.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else if searchResults.isEmpty {
                ContentUnavailableView(
                    "Search the Live Music Archive",
                    systemImage: "magnifyingglass",
                    description: Text("Find live recordings by artist, venue, or date.")
                )
            } else {
                List(searchResults) { show in
                    NavigationLink(value: show.identifier) {
                        ArtistShowRow(show: show)
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.plain)
                .scrollDismissesKeyboard(.immediately)
            }
        }
    }

    // MARK: - Default view

    private var defaultView: some View {
        ContentUnavailableView(
            "Search the Live Music Archive",
            systemImage: "magnifyingglass",
            description: Text("Find live recordings by artist, venue, or date.")
        )
    }
}
