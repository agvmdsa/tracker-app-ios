import SwiftUI

@main
struct TrackerApp: App {
    @StateObject private var connectionManager = ConnectionManager()
    @StateObject private var uwbManager = UWBManager()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MainView()
                .environmentObject(connectionManager)
                .environmentObject(uwbManager)
        }
        .onChange(of: scenePhase) { newPhase in
            // Edge Case: Background Behavior — pause everything in background, resume in foreground.
            switch newPhase {
            case .active:
                connectionManager.start()
                uwbManager.resume()
            case .background, .inactive:
                connectionManager.stop()
                uwbManager.pause()
            @unknown default:
                break
            }
        }
    }
}
