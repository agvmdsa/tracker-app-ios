import CoreMotion
import Observation

@Observable
@MainActor
final class AccelerometerReader {
    /// Well above the beacons' 10 advertisements per second, so the reading attached to each one is
    /// never more than 20 ms old. Recorded in each recording's metadata.
    static let updateInterval: TimeInterval = 1.0 / 50.0

    private(set) var latest: AccelerometerSample?

    private let motionManager = CMMotionManager()

    nonisolated init() {}

    func start() {
        guard motionManager.isAccelerometerAvailable, !motionManager.isAccelerometerActive else { return }
        motionManager.accelerometerUpdateInterval = Self.updateInterval
        motionManager.startAccelerometerUpdates(to: .main) { [weak self] data, _ in
            guard let acceleration = data?.acceleration else { return }
            // Delivered on the main queue, as requested above.
            MainActor.assumeIsolated {
                self?.latest = AccelerometerSample(x: acceleration.x, y: acceleration.y, z: acceleration.z)
            }
        }
    }

    func stop() {
        motionManager.stopAccelerometerUpdates()
        latest = nil
    }
}

extension AccelerometerReader: AccelerometerReading {}
