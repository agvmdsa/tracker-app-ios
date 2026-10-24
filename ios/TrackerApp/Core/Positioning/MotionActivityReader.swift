import CoreMotion
import Observation

@Observable
@MainActor
final class MotionActivityReader {
    private(set) var latest: MotionActivitySample?

    private let activityManager = CMMotionActivityManager()

    nonisolated init() {}

    /// Needs `NSMotionUsageDescription`; like other CoreMotion permissions, iOS prompts on this
    /// first call rather than needing a separate authorization request.
    func start() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        activityManager.startActivityUpdates(to: .main) { [weak self] activity in
            guard let activity else { return }
            MainActor.assumeIsolated {
                self?.latest = MotionActivitySample(state: Self.state(of: activity), confidence: activity.confidence.rawValue)
            }
        }
    }

    func stop() {
        activityManager.stopActivityUpdates()
        latest = nil
    }

    /// CoreMotion's flags aren't mutually exclusive in principle, so this picks the most specific
    /// one true, in order from "most clearly not standing still" down to "standing still".
    private static func state(of activity: CMMotionActivity) -> MotionActivityState {
        if activity.automotive { return .automotive }
        if activity.cycling { return .cycling }
        if activity.running { return .running }
        if activity.walking { return .walking }
        if activity.stationary { return .stationary }
        return .unknown
    }
}

extension MotionActivityReader: MotionActivityReading {}
