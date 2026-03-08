import SwiftUI

// MARK: - Navigation route

enum CollectionRoute: Hashable {
    case detail(String)
}

// MARK: - CollectionsScreen

struct CollectionsScreen: View {
    @Environment(\.appContainer) private var container
    private var service: CollectionsServiceImpl { container.collectionsService }

    @State private var searchText = ""
    @FocusState private var isSearchFieldFocused: Bool
    private var isSearchActive: Bool {
        isSearchFieldFocused || !searchText.isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            searchBar

            if service.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if service.collections.isEmpty {
                ContentUnavailableView(
                    "No Collections",
                    systemImage: "square.stack",
                    description: Text(!searchText.isEmpty
                                      ? "No collections match your search"
                                      : "Collections will appear after data import")
                )
            } else {
                collectionsList
            }
        }
        .toolbar(isSearchActive ? .hidden : .visible, for: .navigationBar)
        .animation(.easeInOut(duration: 0.2), value: isSearchActive)
        .task { service.loadAll() }
        .onChange(of: searchText) { _, query in
            if query.isEmpty {
                service.loadAll()
            } else {
                service.search(query)
            }
        }
    }

    // MARK: - Search bar

    private var searchBar: some View {
        HStack(spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                TextField("Search collections", text: $searchText)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .focused($isSearchFieldFocused)
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

            if isSearchActive {
                Button("Cancel") {
                    searchText = ""
                    isSearchFieldFocused = false
                }
                .foregroundStyle(.primary)
            }
        }
        .padding(.horizontal, DeadlySpacing.screenPadding)
        .padding(.top, DeadlySpacing.screenPadding)
    }

    // MARK: - List

    private var collectionsList: some View {
        List(service.collections) { collection in
            NavigationLink(value: CollectionRoute.detail(collection.id)) {
                CollectionRowView(collection: collection)
            }
        }
        .listStyle(.plain)
    }
}
