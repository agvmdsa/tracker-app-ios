import Foundation

/// A barometric reading from `CMAltimeter`. `relativeAltitudeM` is metres **relative to wherever
/// altitude updates started** for this run of the app — not altitude above sea level and not a
/// floor number — so it only tells two readings apart (this point is higher/lower than that one),
/// which is enough to separate floors in a multi-floor building as long as every recording in a
/// session starts from the same reference (e.g. always starting the app, or at least restarting
/// altitude updates, at the same floor).
struct AltitudeSample: Equatable {
    let relativeAltitudeM: Double
    let pressureKPa: Double
}

@MainActor
protocol AltitudeReading: AnyObject {
    /// The most recent reading; nil before the first one arrives or on hardware without a
    /// barometer (the Simulator, and iPhones older than the 6).
    var latest: AltitudeSample? { get }

    func start()
    func stop()
}
