import SwiftUI

struct PrivacyDataView: View {
    @Environment(\.appContainer) private var container

    var body: some View {
        List {
            Section {
                Text("This app collects anonymous usage statistics to help improve the experience. No personal data is collected.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }

            Section {
                Toggle(isOn: Binding(
                    get: { !container.appPreferences.analyticsEnabled },
                    set: { disabling in
                        let newEnabled = !disabling
                        let event = newEnabled ? "analytics_opt_in" : "analytics_opt_out"
                        container.analyticsService.track("feature_use", props: ["feature": event])
                        container.analyticsService.flush()
                        container.appPreferences.analyticsEnabled = newEnabled
                    }
                )) {
                    Text("Disable Anonymous Usage Data")
                }
            }
        }
        .navigationTitle("Privacy & Data")
        .navigationBarTitleDisplayMode(.large)
    }
}
