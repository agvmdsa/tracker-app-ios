import Foundation

/// The two numbers of the log-distance model, `rssi = referenceRSSI - 10 * exponent * log10(d)`,
/// as calibrated from what the phone reads in the middle of the anchors and touching one.
struct PathLossCalibration: Equatable {
    /// The RSSI at 1 m.
    let referenceRSSI: Double
    let exponent: Double
    /// False when the touching reading could not be used (missing, or not stronger than the
    /// center's) and the center alone calibrated, with the nominal exponent.
    let usesTouchRSSI: Bool

    func distanceMeters(rssi: Double) -> Double {
        pow(10.0, (referenceRSSI - rssi) / (10.0 * exponent))
    }

    func rssi(atDistance distance: Double) -> Double {
        referenceRSSI - 10.0 * exponent * log10(distance)
    }
}

enum PathLossModel {
    /// The free-space exponent, used when only the center RSSI is known. `Multilateration` also
    /// sizes its RSSI noise with it.
    static let pathLossExponent: Double = 2.0
    /// What the iPhone reads from each ESP32 beacon in the middle of the 2.5 m bench square, 1.77 m
    /// from every corner. The app lets it be set again for another room or phone.
    static let defaultCenterRSSI: Double = -70.0
    /// What the phone reads touching a beacon. Measured between -34 and -42 dBm; -30 is the base.
    static let defaultTouchRSSI: Double = -30.0
    /// How far apart the antennas are with the phone touching a beacon: the phone's antenna and the
    /// board's are a few centimetres inside each case.
    static let touchDistanceMeters: Double = 0.1
    /// Exponents outside this are not a room but a wrong reading (the phone was not touching, or
    /// not in the middle), so the touching RSSI is then ignored.
    static let plausibleExponents: ClosedRange<Double> = 1.0...6.0

    /// The bench calibration: the default readings on the 2.5 m square.
    static let defaultCalibration = calibration(
        centerRSSI: defaultCenterRSSI,
        centerDistanceMeters: 2.5 / 2.0.squareRoot(),
        touchRSSI: defaultTouchRSSI
    )
    /// The RSSI at 1 m of `defaultCalibration`: about -62 dBm.
    static var referenceRSSI: Double { defaultCalibration.referenceRSSI }

    static func distanceMeters(rssi: Double, calibration: PathLossCalibration = defaultCalibration) -> Double {
        calibration.distanceMeters(rssi: rssi)
    }

    /// Reads `centerRSSI` as `centerDistanceMeters` and `touchRSSI` as `touchDistanceMeters`, which
    /// fixes both the RSSI at 1 m and the exponent. Without a usable touching reading, the center
    /// alone fixes the RSSI at 1 m, with the nominal exponent.
    static func calibration(centerRSSI: Double, centerDistanceMeters: Double, touchRSSI: Double?) -> PathLossCalibration {
        if let touchRSSI, centerDistanceMeters > touchDistanceMeters {
            let exponent = (touchRSSI - centerRSSI) / (10.0 * log10(centerDistanceMeters / touchDistanceMeters))
            if plausibleExponents.contains(exponent) {
                return PathLossCalibration(
                    referenceRSSI: centerRSSI + 10.0 * exponent * log10(centerDistanceMeters),
                    exponent: exponent,
                    usesTouchRSSI: true
                )
            }
        }
        return PathLossCalibration(
            referenceRSSI: centerRSSI + 10.0 * pathLossExponent * log10(centerDistanceMeters),
            exponent: pathLossExponent,
            usesTouchRSSI: false
        )
    }

    /// How far the anchors are, on average, from their middle (the mean of their positions). Nil
    /// for fewer than two anchors, which have no middle to be away from.
    static func centerDistanceMeters(of anchors: [Point2D]) -> Double? {
        guard anchors.count >= 2 else { return nil }
        let count = Double(anchors.count)
        let middle = Point2D(
            x: anchors.reduce(0.0) { $0 + $1.x } / count,
            y: anchors.reduce(0.0) { $0 + $1.y } / count
        )
        let distance = anchors.reduce(0.0) { $0 + $1.distance(to: middle) } / count
        return distance > 1e-6 ? distance : nil
    }
}

/// The recent RSSI readings of one beacon, answered with their median.
///
/// The median ignores a stray reading (one -90 among -60s does not move it) and, unlike following
/// the last reading, it uses every advertisement heard in the window. It trails a moving phone by
/// about half the window, so `durationMs` is a trade between steadiness and delay. Readings older
/// than the window are dropped and at most `capacity` are kept, so it never grows without bound.
struct RSSIWindow {
    let durationMs: Int64
    let capacity: Int
    private var readings: [(rssi: Int, atMs: Int64)] = []

    init(durationMs: Int64, capacity: Int) {
        precondition(durationMs > 0 && capacity > 0, "the window needs a duration and a capacity")
        self.durationMs = durationMs
        self.capacity = capacity
    }

    var count: Int { readings.count }

    /// `atMs` is the reading's monotonic time (`MonotonicClock`), the clock `median(atMs:)` is asked on.
    mutating func add(rssi: Int, atMs: Int64) {
        readings.append((rssi, atMs))
        let oldestKept = atMs - durationMs
        readings.removeAll { $0.atMs < oldestKept }
        if readings.count > capacity {
            readings.removeFirst(readings.count - capacity)
        }
    }

    /// The median of the readings from the last `durationMs` before `now`: the middle one, or the
    /// mean of the two middle ones. Nil when nothing was heard in that time.
    func median(atMs now: Int64) -> Double? {
        let oldestKept = now - durationMs
        let recent = readings.filter { $0.atMs >= oldestKept }.map(\.rssi).sorted()
        guard !recent.isEmpty else { return nil }
        let middle = recent.count / 2
        return recent.count % 2 == 1
            ? Double(recent[middle])
            : Double(recent[middle - 1] + recent[middle]) / 2
    }
}
