import SwiftUI
import os

@main
struct TrackerApp: App {
    @StateObject private var connectionManager = ConnectionManager()
    @StateObject private var uwbManager = UWBManager()
    @Environment(\.scenePhase) private var scenePhase
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "AppLifecycle")

    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(connectionManager)
                .environmentObject(uwbManager)
        }
        .onChange(of: scenePhase) { newPhase in
            // Edge Case: Background Behavior — pause everything in background, resume in foreground.
            // `.inactive` is intentionally a no-op: it fires during transient foreground events too
            // (system permission prompts, alerts, Control Center, notification banners), and
            // stopping the session there was killing in-progress connections before they finished.
            Self.logger.info("scenePhase changed: \(String(describing: newPhase), privacy: .public)")
            switch newPhase {
            case .active:
                connectionManager.start()
                uwbManager.resume()
            case .background:
                Self.logger.info("scenePhase == .background — stopping ConnectionManager/UWBManager")
                connectionManager.stop()
                uwbManager.pause()
            case .inactive:
                break
            @unknown default:
                break
            }
        }
    }
}
