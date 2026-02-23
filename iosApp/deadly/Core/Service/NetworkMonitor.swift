import Network
import Observation

/// Monitors network connectivity using NWPathMonitor.
/// Publishes `isConnected` which views can observe to show offline state.
/// Respects `AppPreferences.forceOnline` — when true, always reports connected.
@Observable
@MainActor
final class NetworkMonitor {
    private(set) var isConnected = true
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.grateful.deadly.NetworkMonitor")
    private let appPreferences: AppPreferences

    init(appPreferences: AppPreferences) {
        self.appPreferences = appPreferences
    }

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                guard let self else { return }
                if self.appPreferences.forceOnline {
                    self.isConnected = true
                } else {
                    self.isConnected = path.status == .satisfied
                }
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor.cancel()
    }
}
