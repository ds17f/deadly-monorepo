import CarPlay
import os
import UIKit

private let logger = Logger(subsystem: "com.grateful.deadly", category: "CarPlay")

/// Handles the CarPlay scene lifecycle — creates CarPlayManager on connect, tears down on disconnect.
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {
    private var carPlayManager: CarPlayManager?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        logger.info("CarPlay scene didConnect")

        guard let appDelegate = DeadlyAppDelegate.shared else {
            logger.error("CarPlay: DeadlyAppDelegate.shared is nil — app not fully launched yet")
            return
        }

        let manager = CarPlayManager(interfaceController: interfaceController, container: appDelegate.container)
        self.carPlayManager = manager
        manager.configure()
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        logger.info("CarPlay scene didDisconnect")
        carPlayManager = nil
    }
}
