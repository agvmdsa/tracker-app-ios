import SwiftUI
import os

@main
struct TrackerApp: App {
    @State private var proximityTracker = ProximityTracker()
    @State private var beaconAdvertiser = BeaconAdvertiser()
    @State private var beaconScanner = BeaconScanner()
    @State private var anchorStore = AnchorStore()
    @State private var arPositionTracker = ARPositionTracker()
    @State private var savedLocationStore = SavedLocationStore()
    @State private var deviceMotion = DeviceMotionReader()
    @State private var altimeter = AltitudeReader()
    @State private var motionActivity = MotionActivityReader()
    @Environment(\.scenePhase) private var scenePhase
    private static let logger = Logger(subsystem: "com.airtagclone.TrackerApp", category: "AppLifecycle")

    var body: some Scene {
        WindowGroup {
            MainView()
                .environment(\.proximityTracker, proximityTracker)
                .environment(\.beaconAdvertiser, beaconAdvertiser)
                .environment(\.beaconScanner, beaconScanner)
                .environment(\.anchorStore, anchorStore)
                .environment(\.arPositionTracker, arPositionTracker)
                .environment(\.savedLocationStore, savedLocationStore)
                .environment(\.deviceMotion, deviceMotion)
                .environment(\.altimeter, altimeter)
                .environment(\.motionActivity, motionActivity)
        }
        .onChange(of: scenePhase) { newPhase in
            // Edge Case: Background Behavior — pause everything in background, resume in foreground.
            // `.inactive` is intentionally a no-op: it fires during transient foreground events too
            // (system permission prompts, alerts, Control Center, notification banners), and
            // stopping the session there was killing in-progress connections before they finished.
            Self.logger.info("scenePhase changed: \(String(describing: newPhase), privacy: .public)")
            switch newPhase {
            case .active:
                proximityTracker.resume()
            case .background:
                Self.logger.info("scenePhase == .background — pausing ProximityTracker")
                proximityTracker.pause()
            case .inactive:
                break
            @unknown default:
                break
            }
        }
    }
}
