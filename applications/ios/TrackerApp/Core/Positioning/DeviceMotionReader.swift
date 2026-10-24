import CoreMotion
import Observation

@Observable
@MainActor
final class DeviceMotionReader {
    /// Well above the beacons' 10 advertisements per second, so the reading attached to each one is
    /// never more than 20 ms old. Recorded in each recording's metadata.
    static let updateInterval: TimeInterval = 1.0 / 50.0

    private(set) var latest: DeviceMotionSample?

    private let motionManager = CMMotionManager()

    nonisolated init() {}

    func start() {
        guard motionManager.isDeviceMotionAvailable, !motionManager.isDeviceMotionActive else { return }
        motionManager.deviceMotionUpdateInterval = Self.updateInterval
        // .xArbitraryZVertical: tilt relative to gravity is always available with no setup; it
        // skips the magnetometer-calibration figure-8 prompt that a magnetic-north frame would
        // need, at the cost of `yaw` not being tied to a fixed real-world direction. Fine here —
        // the value this reader cares about (how the phone is tilted/held, not which way it faces
        // North) doesn't need it.
        motionManager.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: .main) { [weak self] motion, _ in
            guard let attitude = motion?.attitude else { return }
            // Delivered on the main queue, as requested above.
            MainActor.assumeIsolated {
                self?.latest = DeviceMotionSample(roll: attitude.roll, pitch: attitude.pitch, yaw: attitude.yaw)
            }
        }
    }

    func stop() {
        motionManager.stopDeviceMotionUpdates()
        latest = nil
    }
}

extension DeviceMotionReader: DeviceMotionReading {}
