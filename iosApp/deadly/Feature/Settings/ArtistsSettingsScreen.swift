import SwiftUI

struct ArtistsSettingsScreen: View {
    @Environment(\.appContainer) private var container

    var body: some View {
        List {
            Section {
                ForEach(Artist.browsable) { artist in
                    Toggle(isOn: Binding(
                        get: { container.appPreferences.isArtistEnabled(artist.id) },
                        set: { container.appPreferences.setArtistEnabled(artist.id, enabled: $0) }
                    )) {
                        Text(artist.name)
                    }
                }
            } footer: {
                Text("Enabled artists appear in the Artists tab and Home screen.")
            }
        }
        .navigationTitle("Artists")
    }
}
