import Foundation

enum PathLossModel {
    static let pathLossExponent: Double = 2.0
    /// What the iPhone reads from each ESP32 beacon in the middle of the 2.5 m bench square, 1.77 m
    /// from every corner. The app lets it be set again for another room or phone.
    static let defaultCenterRSSI: Double = -70.0
    /// The RSSI at 1 m that `defaultCenterRSSI` implies on the bench square: about -65 dBm.
    static let referenceRSSI: Double = referenceRSSI(centerRSSI: defaultCenterRSSI, centerDistanceMeters: 2.5 / 2.0.squareRoot())

    static func distanceMeters(rssi: Double, referenceRSSI: Double = referenceRSSI) -> Double {
        pow(10.0, (referenceRSSI - rssi) / (10.0 * pathLossExponent))
    }

    /// The RSSI at 1 m for which `centerRSSI` reads as `centerDistanceMeters`.
    static func referenceRSSI(centerRSSI: Double, centerDistanceMeters: Double) -> Double {
        centerRSSI + 10.0 * pathLossExponent * log10(centerDistanceMeters)
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
