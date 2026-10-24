import SwiftUI

private struct ProximityTrackingKey: EnvironmentKey {
    static let defaultValue: any ProximityTracking = ProximityTracker()
}

extension EnvironmentValues {
    var proximityTracker: any ProximityTracking {
        get { self[ProximityTrackingKey.self] }
        set { self[ProximityTrackingKey.self] = newValue }
    }
}
