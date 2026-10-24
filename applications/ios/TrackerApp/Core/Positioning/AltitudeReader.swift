import CoreMotion
import Observation

@Observable
@MainActor
final class AltitudeReader {
    /// No permission prompt: `CMAltimeter` isn't gated by an `Info.plist` usage description, only
    /// by whether the hardware has a barometer (`isRelativeAltitudeAvailable()`).
    private(set) var latest: AltitudeSample?

    private let altimeter = CMAltimeter()

    nonisolated init() {}

    func start() {
        guard CMAltimeter.isRelativeAltitudeAvailable() else { return }
        // No update-interval setter on CMAltimeter: it delivers on pressure changes, not a fixed
        // rate, so there's no equivalent of DeviceMotionReader's updateInterval to set here.
        altimeter.startRelativeAltitudeUpdates(to: .main) { [weak self] data, _ in
            guard let data else { return }
            MainActor.assumeIsolated {
                self?.latest = AltitudeSample(
                    relativeAltitudeM: data.relativeAltitude.doubleValue,
                    pressureKPa: data.pressure.doubleValue
                )
            }
        }
    }

    func stop() {
        altimeter.stopRelativeAltitudeUpdates()
        latest = nil
    }
}

extension AltitudeReader: AltitudeReading {}
