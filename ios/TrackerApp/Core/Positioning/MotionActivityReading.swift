import Foundation

/// A classified state from `CMMotionActivityManager`. Separates "parado de propósito num ponto"
/// from "passando andando" in a fingerprint recording, and marks rows that happened while the
/// phone wasn't actually stationary even though the recording was labelled as a static point.
enum MotionActivityState: String {
    case stationary, walking, running, automotive, cycling, unknown
}

struct MotionActivitySample: Equatable {
    let state: MotionActivityState
    /// 0 = low, 1 = medium, 2 = high (`CMMotionActivityConfidence`'s own raw values).
    let confidence: Int
}

@MainActor
protocol MotionActivityReading: AnyObject {
    /// The most recent reading; nil before the first one arrives, before authorization is
    /// granted, or on hardware without the M-series coprocessor this needs (the Simulator).
    var latest: MotionActivitySample? { get }

    func start()
    func stop()
}
