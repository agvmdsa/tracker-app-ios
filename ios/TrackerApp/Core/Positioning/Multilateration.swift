import Foundation

struct AnchorRange {
    let anchor: Anchor
    let distanceMeters: Double
    /// The median RSSI (dBm) the distance was computed from, when it came from the radio. Shown on the
    /// map, because calibrating the path loss model needs the dBm and not only the distance.
    var rssi: Double? = nil
}

struct ObserverFix {
    let readings: [AnchorRange]
    let position: Point2D
    let uncertaintyMeters: Double
}

enum Multilateration {
    private static let epsilon = 1e-9

    /// Typical RSSI noise in dB. A fixed error in dB becomes an error that grows with the distance
    /// (the RSSI changes less per metre the further away the anchor is), so a reading is trusted in
    /// proportion to how near its anchor is.
    static let rssiNoiseDb = 3.0
    /// How wrong a distance is, as a fraction of itself, for `rssiNoiseDb` of noise: about 0.35,
    /// i.e. 0.7 m at 2 m and 2.8 m at 8 m.
    static let relativeDistanceError = log(10.0) / (10.0 * PathLossModel.pathLossExponent) * rssiNoiseDb
    /// Closer than this the error stops shrinking (the model, the antenna and the phone's own
    /// position are never that exact), which also keeps the weight of a tiny distance finite. Small,
    /// so a beacon a few centimetres away decides the fix: indoors the far beacons read stronger than
    /// the model expects, and with 0.5 m they pulled a phone touching a corner 0.6 m towards the
    /// middle. It only applies below 1.45 m, so the middle is not affected.
    static let minimumSigmaMeters = 0.1

    /// A beacon this close by its RSSI (about "RSSI encostado" up to a few dB weaker) holds the
    /// phone near it, whatever the others say.
    static let proximityRadiusMeters = 0.5

    private static let maximumIterations = 50
    private static let convergedStepMeters = 1e-6
    private static let smallestStepFraction = 1e-3

    /// How much a reading counts: 1 / error², with the error proportional to the distance, so an
    /// anchor at 2 m counts 16 times more than one at 8 m.
    static func weight(forDistance distance: Double) -> Double {
        let sigma = sigma(forDistance: distance)
        return 1 / (sigma * sigma)
    }

    private static func sigma(forDistance distance: Double) -> Double {
        max(minimumSigmaMeters, relativeDistanceError * distance)
    }

    /// Weighted least squares over every reading: it starts from the unweighted linear solution
    /// (which also rejects anchors that are in a line) and refines it by Gauss-Newton, so no single
    /// anchor, and in particular not the first one in the list, decides the result.
    static func solve(readings: [AnchorRange]) -> ObserverFix? {
        guard readings.count >= 3, let start = linearSolution(readings: readings) else { return nil }

        // Not a search from several starts: with equal readings the linear start is the middle, and
        // other starts would pick one of the symmetric minima around it at random.
        let position = heldByTheNearest(refined(start, readings: readings), readings: readings)

        let residual = sqrt(
            readings.reduce(0.0) { partial, reading in
                partial + sq(reading.anchor.position.distance(to: position) - reading.distanceMeters)
            } / Double(readings.count)
        )

        return ObserverFix(readings: readings, position: position, uncertaintyMeters: residual)
    }

    /// Subtracts the first anchor's circle from the others, which leaves linear equations. It
    /// counts every reading the same and trusts the first one's distance in every equation, so it is
    /// only the starting point. Nil when the anchors are (almost) in a line.
    private static func linearSolution(readings: [AnchorRange]) -> Point2D? {
        let first = readings[0]
        let p1 = first.anchor.position
        let d1 = first.distanceMeters

        var m11 = 0.0, m12 = 0.0, m22 = 0.0
        var c1 = 0.0, c2 = 0.0

        for reading in readings.dropFirst() {
            let p = reading.anchor.position
            let a1 = 2 * (p.x - p1.x)
            let a2 = 2 * (p.y - p1.y)
            let b = sq(d1) - sq(reading.distanceMeters) + sq(p.x) - sq(p1.x) + sq(p.y) - sq(p1.y)

            m11 += a1 * a1
            m12 += a1 * a2
            m22 += a2 * a2
            c1 += a1 * b
            c2 += a2 * b
        }

        let determinant = m11 * m22 - m12 * m12
        guard abs(determinant) > epsilon else { return nil }

        return Point2D(
            x: (c1 * m22 - m12 * c2) / determinant,
            y: (m11 * c2 - c1 * m12) / determinant
        )
    }

    /// Within `proximityRadiusMeters` of an anchor by its RSSI, and clearly nearer it than any other
    /// (the next at more than twice the range), the fix is kept within the range counted from
    /// touching the anchor: reading "RSSI encostado" (`PathLossModel.touchDistanceMeters`) or
    /// stronger puts the phone on the anchor, and 0.3 m by the model at most 0.2 m from it. Several
    /// anchors all reading that near contradict each other, and hold nothing.
    private static func heldByTheNearest(_ position: Point2D, readings: [AnchorRange]) -> Point2D {
        let sorted = readings.sorted { $0.distanceMeters < $1.distanceMeters }
        let nearest = sorted[0]
        guard nearest.distanceMeters <= proximityRadiusMeters, sorted[1].distanceMeters > 2 * nearest.distanceMeters else {
            return position
        }
        let anchor = nearest.anchor.position
        let radius = max(0, nearest.distanceMeters - PathLossModel.touchDistanceMeters)
        let away = position.distance(to: anchor)
        guard away > radius else { return position }
        guard radius > 0 else { return anchor }
        return Point2D(
            x: anchor.x + (position.x - anchor.x) * radius / away,
            y: anchor.y + (position.y - anchor.y) * radius / away
        )
    }

    /// Gauss-Newton on the weighted range residuals. A step that makes the weighted error worse is
    /// halved until it does not; it stops when even a tiny step does, or the equations degenerate.
    private static func refined(_ start: Point2D, readings: [AnchorRange]) -> Point2D {
        var position = start
        var cost = weightedCost(at: position, readings: readings)

        for _ in 0..<maximumIterations {
            var n11 = 0.0, n12 = 0.0, n22 = 0.0
            var g1 = 0.0, g2 = 0.0
            for reading in readings {
                let dx = position.x - reading.anchor.position.x
                let dy = position.y - reading.anchor.position.y
                let range = max(hypot(dx, dy), epsilon)
                let ux = dx / range
                let uy = dy / range
                let weight = weight(forDistance: reading.distanceMeters)
                let error = reading.distanceMeters - range

                n11 += weight * ux * ux
                n12 += weight * ux * uy
                n22 += weight * uy * uy
                g1 += weight * ux * error
                g2 += weight * uy * error
            }

            let determinant = n11 * n22 - n12 * n12
            guard abs(determinant) > epsilon else { break }
            let step = Point2D(
                x: (g1 * n22 - n12 * g2) / determinant,
                y: (n11 * g2 - g1 * n12) / determinant
            )

            var fraction = 1.0
            while true {
                let candidate = Point2D(x: position.x + fraction * step.x, y: position.y + fraction * step.y)
                let candidateCost = weightedCost(at: candidate, readings: readings)
                if candidateCost <= cost {
                    position = candidate
                    cost = candidateCost
                    break
                }
                fraction /= 2
                if fraction < smallestStepFraction { return position }
            }

            if fraction * step.magnitude < convergedStepMeters { break }
        }
        return position
    }

    private static func weightedCost(at position: Point2D, readings: [AnchorRange]) -> Double {
        readings.reduce(0.0) { partial, reading in
            let error = reading.anchor.position.distance(to: position) - reading.distanceMeters
            return partial + weight(forDistance: reading.distanceMeters) * sq(error)
        }
    }

    private static func sq(_ value: Double) -> Double { value * value }
}
