import Foundation

/// One accelerometer reading, in g along each device axis. Gravity is included, so a phone lying
/// still and face up reads about (0, 0, -1) — which is what makes the reading tell how the phone
/// is being held, not just whether it is moving.
struct AccelerometerSample: Equatable {
    let x: Double
    let y: Double
    let z: Double
}

@MainActor
protocol AccelerometerReading: AnyObject {
    /// The most recent reading; nil before the first one arrives or on hardware without an
    /// accelerometer (the Simulator).
    var latest: AccelerometerSample? { get }

    func start()
    func stop()
}
