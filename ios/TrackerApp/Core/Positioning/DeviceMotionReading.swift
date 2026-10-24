import Foundation

/// The phone's attitude (orientation), in radians, from CoreMotion's sensor fusion
/// (accelerometer + gyroscope, and the magnetometer where available) rather than a single raw
/// accelerometer sample — this is what makes the reading tell how the phone is being held (and so
/// how the body might be blocking the antenna), without gravity and actual movement mixed into
/// the same three numbers the way a raw accelerometer reading has them. `roll`/`pitch` are tilt
/// relative to gravity; `yaw` turns with the phone around the vertical axis but, with this
/// reader's reference frame, isn't tied to true/magnetic north, so only relative changes in `yaw`
/// are meaningful, not its absolute value.
struct DeviceMotionSample: Equatable {
    let roll: Double
    let pitch: Double
    let yaw: Double
}

@MainActor
protocol DeviceMotionReading: AnyObject {
    /// The most recent reading; nil before the first one arrives or on hardware without the
    /// sensors device motion needs (the Simulator).
    var latest: DeviceMotionSample? { get }

    func start()
    func stop()
}
